package com.dccleaner.app.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import com.dccleaner.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.io.IOException

@Composable
internal fun rememberUserProfileImage(userId: String): ImageBitmap {
    val resources = LocalResources.current
    val fallback = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.default_profile).asImageBitmap()
    }
    return key(userId) {
        val image by produceState(fallback) {
            if (userId.isNotBlank()) value = loadUserProfileImage(userId) ?: fallback
        }
        image
    }
}

private suspend fun loadUserProfileImage(userId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val url = "https://dcimg2.dcinside.co.kr/gallog_upimg.php".toUri().buildUpon()
        .appendQueryParameter("mode", "profile").appendQueryParameter("gid", userId)
        .appendQueryParameter("t", (System.currentTimeMillis() / 1_000).toString()).build()
    val connection = URI(url.toString()).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val limit = 2 * 1024 * 1024
        if (connection.contentLengthLong > limit) return@withContext null
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                // Bound responses even if the server omits Content-Length.
                if (output.size() + count > limit) return@withContext null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        decodeProfileImage(bytes)
    } catch (_: IOException) {
        null
    } finally {
        connection.disconnect()
    }
}

internal fun decodeProfileImage(bytes: ByteArray): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = 1
    while (options.outWidth / options.inSampleSize > 256 || options.outHeight / options.inSampleSize > 256) {
        options.inSampleSize *= 2
    }
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
