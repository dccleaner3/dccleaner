package com.dccleaner.app.remote

import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.ProxyCleanerPricingTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate

private const val PRICING_URL =
    "https://raw.githubusercontent.com/dccleaner3/dccleaner/main/pricing.json"
private const val MAX_RESPONSE_LENGTH = 32 * 1024

object ProxyCleanerPricingFetcher {
    suspend fun fetchRemote(): ProxyCleanerPricing = withContext(Dispatchers.IO) {
        val connection = URI(PRICING_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", "DCCleaner")

        try {
            if (connection.responseCode !in 200..299) return@withContext ProxyCleanerPricing.Fallback
            if (connection.contentLengthLong > MAX_RESPONSE_LENGTH) return@withContext ProxyCleanerPricing.Fallback
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            if (response.isBlank() || response.length > MAX_RESPONSE_LENGTH) {
                ProxyCleanerPricing.Fallback
            } else {
                parse(response) ?: ProxyCleanerPricing.Fallback
            }
        } catch (_: Exception) {
            ProxyCleanerPricing.Fallback
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(response: String): ProxyCleanerPricing? = runCatching {
        val json = Json.parseToJsonElement(response).jsonObject
        val tiers = json["tiers"]?.jsonArray?.map { element ->
            val tier = element.jsonObject
            ProxyCleanerPricingTier(
                minCount = tier["minCount"]?.jsonPrimitive?.intOrNull ?: return null,
                maxCount = tier["maxCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                unitPriceMultiplier = tier["unitPriceMultiplier"]?.jsonPrimitive?.doubleOrNull
                    ?: return null,
                fixedPrice = tier["fixedPrice"]?.jsonPrimitive?.intOrNull
            )
        }.orEmpty()
        val baseUnitPrice = json["baseUnitPrice"]?.jsonPrimitive?.doubleOrNull ?: return null
        val minimumPrice = json["minimumPrice"]?.jsonPrimitive?.intOrNull ?: return null
        val deletionsPerSecond = json["deletionsPerSecond"]?.jsonPrimitive?.doubleOrNull ?: 1.0
        val completedDeletionCount = json["completedDeletionCount"]?.jsonPrimitive?.longOrNull
            ?: ProxyCleanerPricing.Fallback.completedDeletionCount
        val completedDeletionCountAsOf = json["completedDeletionCountAsOf"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            ?: ProxyCleanerPricing.Fallback.completedDeletionCountAsOf
        val kakaoOpenChatUrl = json["kakaoOpenChatUrl"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("https://open.kakao.com/") }
            ?: ProxyCleanerPricing.Fallback.kakaoOpenChatUrl
        if (baseUnitPrice <= 0.0 || minimumPrice < 0 || deletionsPerSecond <= 0.0 || completedDeletionCount < 0L) return null
        val sortedTiers = tiers.sortedBy(ProxyCleanerPricingTier::minCount)
        if (sortedTiers.isEmpty() || sortedTiers.first().minCount != 1 || sortedTiers.last().maxCount != null ||
            sortedTiers.any {
                it.minCount < 1 || it.unitPriceMultiplier < 0.0 ||
                    (it.maxCount != null && it.maxCount < it.minCount) ||
                    (it.fixedPrice != null && it.fixedPrice < 0)
            } || sortedTiers.zipWithNext().any { (current, next) ->
                current.maxCount == null || next.minCount != current.maxCount + 1
            }
        ) return null
        ProxyCleanerPricing(
            baseUnitPrice,
            minimumPrice,
            deletionsPerSecond,
            completedDeletionCount,
            completedDeletionCountAsOf,
            sortedTiers,
            kakaoOpenChatUrl
        )
    }.getOrNull()
}
