package com.dccleaner.app.model

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Immutable
data class ProxyCleanerPricing(
    val baseUnitPrice: Double,
    val minimumPrice: Int,
    val deletionsPerSecond: Double,
    val completedDeletionCount: Long,
    val completedDeletionCountAsOf: String,
    val tiers: List<ProxyCleanerPricingTier>,
    val kakaoOpenChatUrl: String
) {
    companion object {
        val Fallback = ProxyCleanerPricing(
            baseUnitPrice = 0.1,
            minimumPrice = 5_000,
            deletionsPerSecond = 1.0,
            completedDeletionCount = 877_322L,
            completedDeletionCountAsOf = "2026-09-12",
            tiers = listOf(
                ProxyCleanerPricingTier(1, 10_000, 1.0),
                ProxyCleanerPricingTier(10_001, 50_000, 0.6),
                ProxyCleanerPricingTier(50_001, null, 0.2)
            ),
            kakaoOpenChatUrl = "https://open.kakao.com/"
        )
    }
}

@Immutable
data class ProxyCleanerPricingTier(
    val minCount: Int,
    val maxCount: Int?,
    val unitPriceMultiplier: Double,
    val fixedPrice: Int? = null
)

@Immutable
data class ProxyCleanerQuote(
    val totalCount: Int,
    val originalPrice: Int,
    val finalPrice: Int,
    val discountPercent: Int,
    val estimatedSeconds: Long
)

fun ProxyCleanerPricing.calculateQuote(postCount: Int, commentCount: Int): ProxyCleanerQuote {
    val totalCount = (postCount.coerceAtLeast(0).toLong() + commentCount.coerceAtLeast(0))
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    val calculatedPrice = tiers.sumOf { tier ->
        if (totalCount < tier.minCount) {
            0.0
        } else {
            val tierEnd = minOf(totalCount, tier.maxCount ?: totalCount)
            val countInTier = (tierEnd - tier.minCount + 1).coerceAtLeast(0)
            tier.fixedPrice?.toDouble() ?: countInTier * baseUnitPrice * tier.unitPriceMultiplier
        }
    }
    val finalPrice = ceil(calculatedPrice.coerceAtLeast(minimumPrice.toDouble())).toInt()
    val originalPrice = ceil(totalCount * baseUnitPrice).toInt()
    val discountPercent = if (originalPrice > 0 && finalPrice < originalPrice) {
        ((1.0 - finalPrice.toDouble() / originalPrice) * 100).roundToInt().coerceIn(0, 100)
    } else {
        0
    }
    val estimatedSeconds = if (totalCount == 0 || deletionsPerSecond <= 0.0) {
        0L
    } else {
        ceil(totalCount / deletionsPerSecond).toLong()
    }
    return ProxyCleanerQuote(totalCount, originalPrice, finalPrice, discountPercent, estimatedSeconds)
}
