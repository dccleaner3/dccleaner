package com.dccleaner.musiccontract

import android.net.Uri

object NowPlayingContract {
    const val BRIDGE_PACKAGE = "com.dccleaner.musicbridge"
    const val AUTHORITY = "com.dccleaner.musicbridge.nowplaying"
    const val READ_PERMISSION = "com.dccleaner.permission.READ_NOW_PLAYING"
    const val PROTOCOL_VERSION = 1

    val CURRENT_TRACKS_URI: Uri = Uri.parse("content://$AUTHORITY/current")

    const val METHOD_STATUS = "status"
    const val EXTRA_PROTOCOL_VERSION = "protocol_version"
    const val EXTRA_NOTIFICATION_ACCESS = "notification_access"

    const val COLUMN_PLATFORM_ID = "platform_id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_ARTIST = "artist"
    const val COLUMN_ALBUM = "album"
    const val COLUMN_POSITION_MS = "position_ms"
    const val COLUMN_DURATION_MS = "duration_ms"
    const val COLUMN_ALBUM_ART = "album_art"

    val TRACK_COLUMNS = arrayOf(
        COLUMN_PLATFORM_ID,
        COLUMN_TITLE,
        COLUMN_ARTIST,
        COLUMN_ALBUM,
        COLUMN_POSITION_MS,
        COLUMN_DURATION_MS,
        COLUMN_ALBUM_ART
    )
}

data class MusicPlatform(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>
)

val supportedMusicPlatforms = listOf(
    MusicPlatform(
        id = "youtube_music",
        displayName = "YouTube Music",
        packageNames = setOf("com.google.android.apps.youtube.music")
    ),
    MusicPlatform(
        id = "melon",
        displayName = "멜론",
        packageNames = setOf("com.iloen.melon")
    ),
    MusicPlatform(
        id = "spotify",
        displayName = "Spotify",
        packageNames = setOf("com.spotify.music")
    ),
    MusicPlatform(
        id = "apple_music",
        displayName = "Apple Music",
        packageNames = setOf("com.apple.android.music")
    ),
    MusicPlatform(
        id = "soundcloud",
        displayName = "SoundCloud",
        packageNames = setOf("com.soundcloud.android")
    )
)
