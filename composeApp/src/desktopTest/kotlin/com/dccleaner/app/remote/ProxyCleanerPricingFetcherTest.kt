package com.dccleaner.app.remote

import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.calculateQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyCleanerPricingFetcherTest {
    @Test
    fun calculatesFallbackTierPrices() {
        assertEquals(5_000, ProxyCleanerPricing.Fallback.calculateQuote(500, 0).finalPrice)
        assertEquals(5_000, ProxyCleanerPricing.Fallback.calculateQuote(50_001, 0).finalPrice)
        assertEquals(5_001, ProxyCleanerPricing.Fallback.calculateQuote(130_001, 0).finalPrice)
        assertEquals(6_400, ProxyCleanerPricing.Fallback.calculateQuote(200_000, 0).finalPrice)
    }

    @Test
    fun parsesPricingConfiguration() {
        val pricing = ProxyCleanerPricingFetcher.parse(
            """{
                "baseUnitPrice": 0.1,
                "minimumPrice": 1000,
                "deletionsPerSecond": 1.0,
                "completedDeletionCount": 654321,
                "completedDeletionCountAsOf": "2026-08-29",
                "tiers": [
                    {"minCount": 1, "maxCount": 1000, "unitPriceMultiplier": 1.0, "fixedPrice": 1000},
                    {"minCount": 1001, "maxCount": null, "unitPriceMultiplier": 0.5}
                ]
            }""".trimIndent()
        )

        assertEquals(1_000, pricing?.minimumPrice)
        assertEquals(654_321L, pricing?.completedDeletionCount)
        assertEquals("2026-08-29", pricing?.completedDeletionCountAsOf)
        assertEquals(2, pricing?.tiers?.size)
    }

    @Test
    fun rejectsInvalidPricingConfiguration() {
        assertNull(
            ProxyCleanerPricingFetcher.parse(
                """{"baseUnitPrice":-1,"minimumPrice":1000,"tiers":[]}"""
            )
        )
    }
}
