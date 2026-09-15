package com.dccleaner.musicbridge

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.dccleaner.musiccontract.NowPlayingContract
import com.dccleaner.musiccontract.MusicPlatform
import com.dccleaner.musiccontract.supportedMusicPlatforms
import java.io.ByteArrayOutputStream

class NowPlayingProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != NowPlayingContract.METHOD_STATUS) {
            return super.call(method, arg, extras) ?: Bundle.EMPTY
        }
        val appContext = requireNotNull(context)
        return Bundle().apply {
            putInt(NowPlayingContract.EXTRA_PROTOCOL_VERSION, NowPlayingContract.PROTOCOL_VERSION)
            putBoolean(
                NowPlayingContract.EXTRA_NOTIFICATION_ACCESS,
                isNotificationAccessGranted(appContext)
            )
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(NowPlayingContract.TRACK_COLUMNS)
        val appContext = requireNotNull(context)
        if (!isNotificationAccessGranted(appContext)) return cursor

        val manager = appContext.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(appContext, MusicNotificationListenerService::class.java)
        val controllers = try {
            manager.getActiveSessions(listener)
        } catch (error: SecurityException) {
            Log.e(TAG, "활성 미디어 세션을 읽지 못했습니다.", error)
            return cursor
        }

        controllers.forEach { controller ->
            val platform = supportedMusicPlatforms.firstOrNull {
                controller.packageName in it.packageNames
            } ?: return@forEach
            controller.toProviderRow(platform)?.let(cursor::addRow)
        }
        return cursor
    }

    override fun getType(uri: Uri): String =
        "vnd.android.cursor.dir/vnd.${NowPlayingContract.AUTHORITY}.track"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun MediaController.toProviderRow(platform: MusicPlatform): Array<Any?>? {
        val metadata = metadata ?: return null
        val state = playbackState?.takeIf { it.state == PlaybackState.STATE_PLAYING } ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: "알 수 없는 아티스트"
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        val position = (state.position + elapsed * state.playbackSpeed)
            .toLong()
            .coerceAtLeast(0L)
            .let { if (duration > 0L) it.coerceAtMost(duration) else it }
        val albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        return arrayOf(
            platform.id,
            title,
            artist,
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            position,
            duration,
            albumArt?.toCompressedBytes()
        )
    }

    private fun Bitmap.toCompressedBytes(): ByteArray {
        val maximumSide = 384
        val scale = minOf(1f, maximumSide.toFloat() / maxOf(width, height).coerceAtLeast(1))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                this,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            this
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 88, output))
                output.toByteArray()
            }
        } finally {
            if (scaled !== this) scaled.recycle()
        }
    }

    private fun isNotificationAccessGranted(appContext: android.content.Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expected = ComponentName(appContext, MusicNotificationListenerService::class.java)
        return enabledListeners.split(':').mapNotNull(ComponentName::unflattenFromString)
            .any { it == expected }
    }

    private companion object {
        const val TAG = "NowPlayingProvider"
    }
}
