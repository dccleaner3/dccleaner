package com.dccleaner.app.music

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.dccleaner.app.ui.screen.MusicPlatformOption
import com.dccleaner.musiccontract.NowPlayingContract
import com.dccleaner.musiccontract.supportedMusicPlatforms
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

private const val TAG = "NowPlayingAttachment"
private const val MUSIC_PREFS = "write_music_attachment_preferences"
private const val KEY_ENABLED = "enabled"
private const val KEY_PLATFORM_IDS = "platform_ids"
private const val KEY_BACKGROUND_COLOR = "background_color"
private const val KEY_FALLBACK_IMAGE_URL = "fallback_image_url"
private const val MAX_FALLBACK_IMAGE_BYTES = 12 * 1024 * 1024
private const val MAX_FALLBACK_IMAGE_DIMENSION = 4096
internal const val DEFAULT_MUSIC_BACKGROUND_COLOR: Int = 0xFF0D1117.toInt()
internal const val MUSIC_BRIDGE_DOWNLOAD_URL =
    "https://github.com/dccleaner3/dccleaner/releases/latest"

internal fun musicPlatformOptions(): List<MusicPlatformOption> =
    supportedMusicPlatforms.map { MusicPlatformOption(it.id, it.displayName) }

internal fun getMusicAttachmentEnabled(context: Context): Boolean =
    musicPreferences(context).getBoolean(KEY_ENABLED, false)

internal fun saveMusicAttachmentEnabled(context: Context, enabled: Boolean): Boolean =
    musicPreferences(context).edit().putBoolean(KEY_ENABLED, enabled).commit()

internal fun getEnabledMusicPlatformIds(context: Context): Set<String> =
    musicPreferences(context).getStringSet(KEY_PLATFORM_IDS, emptySet()).orEmpty().toSet()
        .intersect(supportedMusicPlatforms.mapTo(mutableSetOf()) { it.id })

internal fun saveEnabledMusicPlatformIds(context: Context, ids: Set<String>): Boolean =
    musicPreferences(context).edit().putStringSet(KEY_PLATFORM_IDS, ids).commit()

internal fun getMusicBackgroundColor(context: Context): Int =
    musicPreferences(context).getInt(KEY_BACKGROUND_COLOR, DEFAULT_MUSIC_BACKGROUND_COLOR)

internal fun saveMusicBackgroundColor(context: Context, color: Int): Boolean =
    musicPreferences(context).edit().putInt(KEY_BACKGROUND_COLOR, color or 0xFF000000.toInt()).commit()

internal fun getMusicFallbackImageUrl(context: Context): String =
    musicPreferences(context).getString(KEY_FALLBACK_IMAGE_URL, "").orEmpty()

internal fun saveMusicFallbackImageUrl(context: Context, url: String): Boolean =
    musicPreferences(context).edit().putString(KEY_FALLBACK_IMAGE_URL, url.trim()).commit()

private fun musicPreferences(context: Context) = context.applicationContext
    .getSharedPreferences(MUSIC_PREFS, Context.MODE_PRIVATE)

internal data class MusicBridgeStatus(
    val installed: Boolean,
    val notificationAccessGranted: Boolean,
    val compatible: Boolean
)

internal fun getMusicBridgeStatus(context: Context): MusicBridgeStatus {
    val provider = context.packageManager.resolveContentProvider(NowPlayingContract.AUTHORITY, 0)
        ?: return MusicBridgeStatus(false, false, false)
    return runCatching {
        val status = context.contentResolver.call(
            NowPlayingContract.CURRENT_TRACKS_URI,
            NowPlayingContract.METHOD_STATUS,
            null,
            null
        )
        val version = status?.getInt(NowPlayingContract.EXTRA_PROTOCOL_VERSION, 0) ?: 0
        MusicBridgeStatus(
            installed = true,
            notificationAccessGranted = status?.getBoolean(
                NowPlayingContract.EXTRA_NOTIFICATION_ACCESS,
                false
            ) == true,
            compatible = version == NowPlayingContract.PROTOCOL_VERSION
        )
    }.onFailure { error ->
        Log.e(TAG, "음악 연동 앱 상태를 읽지 못했습니다: ${provider.packageName}", error)
    }.getOrDefault(MusicBridgeStatus(true, false, false))
}

internal fun musicBridgeLaunchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(NowPlayingContract.BRIDGE_PACKAGE)

internal fun musicBridgeDownloadIntent(): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(MUSIC_BRIDGE_DOWNLOAD_URL))

internal data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val platformName: String,
    val albumArt: Bitmap?,
    val positionMs: Long,
    val durationMs: Long
)

internal fun getCurrentPlayingTrack(
    context: Context,
    enabledPlatformIds: Set<String>
): NowPlayingTrack? {
    if (enabledPlatformIds.isEmpty()) return null
    return runCatching {
        context.contentResolver.query(
            NowPlayingContract.CURRENT_TRACKS_URI,
            NowPlayingContract.TRACK_COLUMNS,
            null,
            null,
            null
        )?.use { cursor ->
            val platformIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_PLATFORM_ID)
            val titleIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_ALBUM)
            val positionIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_POSITION_MS)
            val durationIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_DURATION_MS)
            val artIndex = cursor.getColumnIndexOrThrow(NowPlayingContract.COLUMN_ALBUM_ART)
            while (cursor.moveToNext()) {
                val platformId = cursor.getString(platformIndex)
                if (platformId !in enabledPlatformIds) continue
                val platform = supportedMusicPlatforms.firstOrNull { it.id == platformId }
                    ?: continue
                val artBytes = if (cursor.isNull(artIndex)) null else cursor.getBlob(artIndex)
                return@use NowPlayingTrack(
                    title = cursor.getString(titleIndex),
                    artist = cursor.getString(artistIndex),
                    album = if (cursor.isNull(albumIndex)) null else cursor.getString(albumIndex),
                    platformName = platform.displayName,
                    albumArt = artBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
                    positionMs = cursor.getLong(positionIndex),
                    durationMs = cursor.getLong(durationIndex)
                )
            }
            null
        }
    }.onFailure { error ->
        Log.e(TAG, "음악 연동 앱에서 현재 곡을 읽지 못했습니다.", error)
    }.getOrNull()
}

internal fun createNowPlayingImageBase64(
    track: NowPlayingTrack,
    backgroundColor: Int = DEFAULT_MUSIC_BACKGROUND_COLOR
): String {
    val width = 840
    val height = 400
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val opaqueBackground = backgroundColor or 0xFF000000.toInt()
    val lightBackground = colorLuminance(opaqueBackground) >= 0.5
    val cardColor = blendColors(
        opaqueBackground,
        if (lightBackground) Color.BLACK else Color.WHITE,
        if (lightBackground) 0.10f else 0.07f
    )
    val primaryTextColor = if (lightBackground) Color.rgb(31, 35, 40) else Color.rgb(240, 246, 252)
    val secondaryTextColor = if (lightBackground) Color.rgb(87, 96, 106) else Color.rgb(173, 186, 199)
    val dividerColor = blendColors(
        opaqueBackground,
        if (lightBackground) Color.BLACK else Color.WHITE,
        if (lightBackground) 0.24f else 0.18f
    )

    canvas.drawColor(opaqueBackground)
    paint.color = cardColor
    canvas.drawRoundRect(RectF(20f, 20f, 820f, 380f), 28f, 28f, paint)

    paint.color = primaryTextColor
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 40f
    canvas.drawText("현재 재생 중 · ${track.platformName}", 44f, 84f, paint)

    val hasAlbumArt = track.albumArt != null
    val textRight = if (hasAlbumArt) 600f else 792f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 35f
    canvas.drawText(ellipsize(track.title, paint, textRight - 44f), 44f, 150f, paint)

    paint.color = secondaryTextColor
    paint.textSize = 30f
    canvas.drawText(ellipsize(track.artist, paint, textRight - 44f), 44f, 202f, paint)

    track.albumArt?.let { art ->
        val destination = RectF(620f, 56f, 764f, 200f)
        val clip = Path().apply { addRoundRect(destination, 16f, 16f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(art, null, destination, paint)
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = dividerColor
        canvas.drawRoundRect(destination, 16f, 16f, paint)
        paint.style = Paint.Style.FILL
    }

    paint.color = dividerColor
    paint.strokeWidth = 2f
    canvas.drawLine(40f, 240f, 800f, 240f, paint)

    val progressLeft = 44f
    val progressTop = 290f
    val progressWidth = 752f
    val progressHeight = 12f
    val ratio = if (track.durationMs > 0L) {
        (track.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    paint.color = dividerColor
    canvas.drawRoundRect(
        RectF(progressLeft, progressTop, progressLeft + progressWidth, progressTop + progressHeight),
        6f,
        6f,
        paint
    )
    paint.color = Color.rgb(35, 134, 54)
    canvas.drawRoundRect(
        RectF(
            progressLeft,
            progressTop,
            progressLeft + progressWidth * ratio,
            progressTop + progressHeight
        ),
        6f,
        6f,
        paint
    )
    paint.color = Color.rgb(46, 160, 67)
    canvas.drawCircle(progressLeft + progressWidth * ratio, progressTop + progressHeight / 2f, 10f, paint)

    paint.color = secondaryTextColor
    paint.textSize = 28f
    val currentTime = formatDuration(track.positionMs)
    val totalTime = if (track.durationMs > 0L) formatDuration(track.durationMs) else "--:--"
    canvas.drawText(currentTime, progressLeft, 356f, paint)
    canvas.drawText(totalTime, progressLeft + progressWidth - paint.measureText(totalTime), 356f, paint)

    return try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        bitmap.recycle()
        track.albumArt?.recycle()
    }
}

internal fun downloadFallbackImageBase64(url: String): String? {
    val normalizedUrl = url.trim()
    val uri = runCatching { URI(normalizedUrl) }.getOrNull() ?: return null
    if (uri.scheme !in setOf("http", "https")) return null

    return runCatching {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "DCCleaner-Android")
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_FALLBACK_IMAGE_BYTES) return@runCatching null
            val bytes = connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FALLBACK_IMAGE_BYTES) return@runCatching null
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > MAX_FALLBACK_IMAGE_DIMENSION ||
                bounds.outHeight / sampleSize > MAX_FALLBACK_IMAGE_DIMENSION
            ) {
                sampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return@runCatching null
            try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            connection.disconnect()
        }
    }.onFailure { error ->
        Log.e(TAG, "대체 이미지를 내려받지 못했습니다: $normalizedUrl", error)
    }.getOrNull()
}

private fun colorLuminance(color: Int): Double {
    fun channel(value: Int): Double {
        val normalized = value / 255.0
        return if (normalized <= 0.04045) normalized / 12.92
        else Math.pow((normalized + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(Color.red(color)) +
        0.7152 * channel(Color.green(color)) +
        0.0722 * channel(Color.blue(color))
}

private fun blendColors(base: Int, overlay: Int, overlayRatio: Float): Int {
    val ratio = overlayRatio.coerceIn(0f, 1f)
    fun blend(baseChannel: Int, overlayChannel: Int): Int =
        (baseChannel * (1f - ratio) + overlayChannel * ratio).toInt().coerceIn(0, 255)
    return Color.rgb(
        blend(Color.red(base), Color.red(overlay)),
        blend(Color.green(base), Color.green(overlay)),
        blend(Color.blue(base), Color.blue(overlay))
    )
}

private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(value) <= maxWidth) return value
    val suffix = "…"
    var end = value.length
    while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > maxWidth) end--
    return value.substring(0, end) + suffix
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
