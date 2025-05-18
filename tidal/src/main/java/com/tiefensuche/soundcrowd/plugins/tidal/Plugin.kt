package com.tiefensuche.soundcrowd.plugins.tidal

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.tiefensuche.tidal.api.TidalApi
import com.tiefensuche.soundcrowd.plugins.IPlugin
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.tidal.api.Album
import com.tiefensuche.tidal.api.Artist
import com.tiefensuche.tidal.api.Playlist
import com.tiefensuche.tidal.api.Track

/**
 * Tidal plugin for soundcrowd
 */
class Plugin(val context: Context) : IPlugin {

    companion object {
        const val NAME = "Tidal"
        const val TRACKS = "Tracks"
        const val ARTISTS = "Artists"
        const val ALBUMS = "Albums"
        const val PLAYLISTS = "Playlists"
        const val MIXES = "Mixes"
    }

    private val icon = BitmapFactory.decodeResource(context.resources, R.drawable.icon_plugin_tidal)
    private var sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
    private var api = TidalApi(
        TidalApi.Session(
            context.getString(R.string.tidal_client_id),
            ::sessionCallback
        )
    )
    private val connectPreference = SwitchPreference(context)

    init {
        api.session.quality = TidalApi.Quality.LOSSLESS

        connectPreference.key = context.getString(R.string.tidal_connect_key)
        connectPreference.title = context.getString(R.string.tidal_connect_title)
        connectPreference.summary = context.getString(R.string.tidal_connect_summary)
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                AsyncTask.execute {
                    val verificationUriComplete = api.auth()
                    val intent = Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://$verificationUriComplete")
                    )
                    intent.flags = FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)

                    for (i in 0..30) {
                        try {
                            if (api.getAccessToken()) {
                                println("Auth complete")
                                break
                            }
                        } catch (ex: Exception) {
                            println(ex.message)
                        }
                        println("Auth pending...")
                        Thread.sleep(5_000)
                    }
                }
                true
            } else {
                sharedPref.edit().putString(context.getString(R.string.tidal_access_token_key), null)
                    .putString(context.getString(R.string.tidal_refresh_token_key), null).apply()
                true
            }
        }

        if (sharedPref.getString(context.getString(R.string.tidal_access_token_key), null) != null) {
            api.session.setAuth(
                sharedPref.getLong(context.getString(R.string.tidal_user_id_key), 0),
                sharedPref.getString(context.getString(R.string.tidal_country_code_key), null) ?: api.session.countryCode,
                sharedPref.getString(context.getString(R.string.tidal_access_token_key), null) ?: "",
                sharedPref.getString(context.getString(R.string.tidal_refresh_token_key), null) ?: ""
            )
            connectPreference.isChecked = true
        }
    }

    override fun name(): String = NAME

    // FIXME: MIXES currently broken because of duplicate values in json response and android sdk
    // uses org.json that does not have JSONParserConfiguration().withOverwriteDuplicateKey
    // Using workaround in tidal-kt which removes the duplicate keys for now.
    override fun mediaCategories(): List<String> = listOf(TRACKS, ARTISTS, ALBUMS, PLAYLISTS, MIXES)

    override fun preferences() = listOf(connectPreference)

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, refresh: Boolean): List<MediaItem> {
        return processRequest(mediaCategory, refresh)
    }

    private fun processRequest(mediaCategory: String, refresh: Boolean): List<MediaItem> {
        return when (mediaCategory) {
            TRACKS -> tracksToMediaMetadataCompat(api.getTracks(refresh))
            ARTISTS -> artistsToMediaMetadataCompat(api.getArtists(refresh))
            ALBUMS -> albumsToMediaMetadataCompat(api.getAlbums(refresh))
            PLAYLISTS -> playlistToMediaMetadataCompat(api.getPlaylists(refresh))
            MIXES -> playlistToMediaMetadataCompat(api.getMixes())
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return when (mediaCategory) {
            ARTISTS -> tracksToMediaMetadataCompat(api.getArtist(path.toLong(), refresh))
            ALBUMS -> tracksToMediaMetadataCompat(api.getAlbum(path.toLong(), refresh))
            PLAYLISTS -> tracksToMediaMetadataCompat(api.getPlaylist(path, refresh))
            MIXES -> tracksToMediaMetadataCompat(api.getMix(path, refresh))
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        query: String,
        type: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return tracksToMediaMetadataCompat(api.query(query, refresh))
    }

    override fun getMediaUri(
        mediaItem: MediaItem,
    ) : Uri {
        return Uri.parse(api.getStreamUrl(mediaItem.requestMetadata.mediaUri.toString().toLong()))
    }

    override fun favorite(id: String): Boolean {
        return api.toggleLike(id)
    }

    override fun getIcon(): Bitmap = icon

    private fun tracksToMediaMetadataCompat(tracks: List<Track>): List<MediaItem> {
        return tracks.map {
            MediaItemUtils.createMediaItem(
                it.id.toString(),
                Uri.parse(it.id.toString()),
                it.title,
                it.duration,
                it.artist,
                artworkUri = Uri.parse(it.artwork),
                rating = HeartRating(it.liked)
            )
        }
    }

    private fun artistsToMediaMetadataCompat(artists: List<Artist>): List<MediaItem> {
        return artists.map {
            MediaItemUtils.createBrowsableItem(
                it.id.toString(),
                it.name,
                MediaMetadataCompatExt.MediaType.STREAM,
                artworkUri = Uri.parse(it.artwork)
            )
        }
    }

    private fun albumsToMediaMetadataCompat(artists: List<Album>): List<MediaItem> {
        return artists.map {
            MediaItemUtils.createBrowsableItem(
                it.id.toString(),
                it.name,
                MediaMetadataCompatExt.MediaType.STREAM,
                it.artist,
                artworkUri = Uri.parse(it.artwork)
            )
        }
    }

    private fun playlistToMediaMetadataCompat(playlists: List<Playlist>): List<MediaItem> {
        return playlists.map {
            MediaItemUtils.createBrowsableItem(
                it.uuid,
                it.title,
                MediaMetadataCompatExt.MediaType.STREAM,
                artworkUri = Uri.parse(it.artwork)
            )
        }
    }

    private fun sessionCallback(session: TidalApi.Session) {
        sharedPref.edit()
            .putLong(context.getString(R.string.tidal_user_id_key), session.userId!!)
            .putString(context.getString(R.string.tidal_country_code_key), session.countryCode)
            .putString(context.getString(R.string.tidal_access_token_key), session.accessToken)
            .putString(context.getString(R.string.tidal_refresh_token_key), session.refreshToken)
            .apply()
    }
}