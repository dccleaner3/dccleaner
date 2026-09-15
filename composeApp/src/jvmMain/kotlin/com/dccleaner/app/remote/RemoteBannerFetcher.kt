package com.dccleaner.app.remote

import com.dccleaner.app.model.RemoteBannerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

private const val REMOTE_BANNER_URL =
    "https://raw.githubusercontent.com/dccleaner3/dccleaner/main/docs/ad.json"
private const val MAX_RESPONSE_LENGTH = 64 * 1024

object RemoteBannerFetcher {
    suspend fun fetchRemote(): RemoteBannerConfig? = withContext(Dispatchers.IO) {
        val connection = URI(REMOTE_BANNER_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", "DCCleaner")

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            if (connection.contentLengthLong > MAX_RESPONSE_LENGTH) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (response.length > MAX_RESPONSE_LENGTH) return@withContext null
            if (response.isBlank()) return@withContext null
            parse(response)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(response: String): RemoteBannerConfig? = runCatching {
        val json = Json.parseToJsonElement(response).jsonObject
        if (json["enabled"]?.jsonPrimitive?.booleanOrNull != true) return null

        val text = json.stringOrNull("text")?.takeIf { it.isNotBlank() } ?: return null
        val id = json.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: text

        RemoteBannerConfig(
            id = id,
            type = json.stringOrNull("type")?.lowercase() ?: "notice",
            text = text,
            actionText = json.stringOrNull("actionText")?.takeIf { it.isNotBlank() },
            actionUrl = json.stringOrNull("actionUrl")
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            backgroundColor = json.stringOrNull("backgroundColor"),
            textColor = json.stringOrNull("textColor"),
            dismissible = json["dismissible"]?.jsonPrimitive?.booleanOrNull ?: true
        )
    }.getOrNull()
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
