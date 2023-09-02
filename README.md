# soundcrowd-plugin-tidal

[![android](https://github.com/soundcrowd/soundcrowd-plugin-tidal/actions/workflows/android.yml/badge.svg)](https://github.com/soundcrowd/soundcrowd-plugin-tidal/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-tidal.svg)](https://github.com/soundcrowd/soundcrowd-plugin-tidal/releases)
[![GitHub](https://img.shields.io/github/license/soundcrowd/soundcrowd-plugin-tidal.svg)](LICENSE)

This soundcrowd plugin adds basic Tidal support. It allows you to listen and browse your liked tracks, artists, some playlists, and supports searching for music. This plugin requires a Tidal account with subscription.

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd-plugin-tidal
    $ cd soundcrowd-plugin-tidal
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install build/outputs/apk/debug/tidal-debug.apk

## License

Licensed under GPLv3.