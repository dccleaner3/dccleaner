package com.dccleaner.app.model

import androidx.compose.runtime.Immutable
import kotlin.math.ceil
import kotlin.math.roundToInt

@Immutable
data class ProxyCleanerPricing(
    val baseUnitPrice: Double,
    val minimumPrice: Int,
    val deletionsPerSecond: Double,
    val tiers: List<ProxyCleanerPricingTier>,
    val kakaoOpenChatUrl: String
)

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
    val estimatedSeconds: Long,
    /** Preview only. Purchases use the server's confirmed count and payment amount. */
    val creditedCount: Long
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
    // 서버 _deletion_quote와 같다: 원 단위로 올린 뒤 100원 미만 절사, 최소 결제액 아래로는 내리지 않는다.
    val finalPrice = (ceil(calculatedPrice.coerceAtLeast(minimumPrice.toDouble())).toInt() / 100 * 100)
        .coerceAtLeast(minimumPrice)
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
    val creditedCount = if (totalCount == 0) 0L else if (calculatedPrice < minimumPrice && baseUnitPrice > 0) {
        maxOf(totalCount.toLong(), kotlin.math.floor(minimumPrice / baseUnitPrice + 1e-7).toLong())
    } else totalCount.toLong()
    return ProxyCleanerQuote(totalCount, originalPrice, finalPrice, discountPercent, estimatedSeconds, creditedCount)
}
