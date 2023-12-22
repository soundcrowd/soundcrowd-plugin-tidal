package com.tiefensuche.soundcrowd.plugins.tidal

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.tiefensuche.tidal.api.Endpoints
import com.tiefensuche.tidal.api.TidalApi
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin
import com.tiefensuche.tidal.api.Artist
import com.tiefensuche.tidal.api.Playlist
import com.tiefensuche.tidal.api.Track

/**
 * Tidal plugin for soundcrowd
 */
class Plugin(appContext: Context, val context: Context) : IPlugin {

    companion object {
        const val name = "Tidal"
        const val TRACKS = "Tracks"
        const val ARTISTS = "Artists"
        const val PLAYLISTS = "Playlists"
        const val MIXES = "Mixes"
    }

    private val icon = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private var sharedPref = PreferenceManager.getDefaultSharedPreferences(appContext)
    private var api = TidalApi(
        TidalApi.Session(
            context.getString(R.string.client_id),
            ::sessionCallback
        )
    )
    private val connectPreference = SwitchPreference(appContext)

    init {
        api.session.quality = TidalApi.Quality.LOSSLESS

        connectPreference.key = context.getString(R.string.connect_key)
        connectPreference.title = context.getString(R.string.connect_title)
        connectPreference.summary = context.getString(R.string.connect_summary)
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                AsyncTask.execute {
                    val verificationUriComplete = api.auth()
                    val intent = Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://$verificationUriComplete")
                    )
                    intent.flags = FLAG_ACTIVITY_NEW_TASK
                    appContext.startActivity(intent)
                    for (i in 0..30) {
                        if (api.getAccessToken()) {
                            println("Auth complete")
                            break
                        }
                        println("Auth pending...")
                        Thread.sleep(5_000)
                    }
                }
                true
            } else {
                sharedPref.edit().putString(context.getString(R.string.access_token_key), null)
                    .putString(context.getString(R.string.refresh_token_key), null).apply()
                true
            }
        }

        if (sharedPref.getString(context.getString(R.string.access_token_key), null) != null) {
            api.session.setAuth(
                sharedPref.getLong(context.getString(R.string.user_id_key), 0),
                sharedPref.getString(context.getString(R.string.country_code_key), null) ?: api.session.countryCode,
                sharedPref.getString(context.getString(R.string.access_token_key), null) ?: "",
                sharedPref.getString(context.getString(R.string.refresh_token_key), null) ?: ""
            )
            connectPreference.isChecked = true
        }
    }

    override fun name(): String = name

    override fun mediaCategories(): List<String> = listOf(TRACKS, ARTISTS, PLAYLISTS, MIXES)

    override fun preferences() = listOf(connectPreference)

    @Throws(Exception::class)
    override fun getMediaItems(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        processRequest(mediaCategory, callback, refresh)
    }

    private fun processRequest(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        when (mediaCategory) {
            TRACKS -> callback.onResult(tracksToMediaMetadataCompat(api.getTracks(refresh)))
            ARTISTS -> callback.onResult(artistsToMediaMetadataCompat(api.getArtists(refresh)))
            PLAYLISTS -> callback.onResult(playlistToMediaMetadataCompat(api.getPlaylists(refresh)))
            MIXES -> callback.onResult(playlistToMediaMetadataCompat(api.getMixes()))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        when (mediaCategory) {
            ARTISTS -> callback.onResult(tracksToMediaMetadataCompat(api.getArtist(path.toLong(), refresh)))
            PLAYLISTS -> callback.onResult(tracksToMediaMetadataCompat(api.getPlaylist(path, refresh)))
            MIXES -> callback.onResult(tracksToMediaMetadataCompat(api.getMix(path, refresh)))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        query: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        callback.onResult(tracksToMediaMetadataCompat(api.query(query, refresh)))
    }

    override fun getMediaUrl(
        metadata: MediaMetadataCompat,
        callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>
    ) {
        val steamUrl = api.getStreamUrl(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).toLong())
        callback.onResult(
            Pair(
                MediaMetadataCompat.Builder(metadata)
                    .putString(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL, steamUrl).build(), null
            )
        )
    }

    override fun favorite(id: String, callback: Callback<Boolean>) {
        callback.onResult(api.toggleLike(id))
    }

    override fun getIcon(): Bitmap = icon

    private fun tracksToMediaMetadataCompat(tracks: List<Track>): List<MediaMetadataCompat> {
        return tracks.map {
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.id.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, it.url)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.artwork)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration)
                .putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, RatingCompat.newHeartRating(it.liked))
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)
                .build()
        }
    }

    private fun artistsToMediaMetadataCompat(artists: List<Artist>): List<MediaMetadataCompat> {
        return artists.map {
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.id.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, it.url)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.artwork)
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                .build()
        }
    }

    private fun playlistToMediaMetadataCompat(playlists: List<Playlist>): List<MediaMetadataCompat> {
        return playlists.map {
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.uuid)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.artwork)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration)
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                .build()
        }
    }

    private fun sessionCallback(session: TidalApi.Session) {
        sharedPref.edit()
            .putLong(context.getString(R.string.user_id_key), session.userId!!)
            .putString(context.getString(R.string.country_code_key), session.countryCode)
            .putString(context.getString(R.string.access_token_key), session.accessToken)
            .putString(context.getString(R.string.refresh_token_key), session.refreshToken)
            .apply()
    }
}