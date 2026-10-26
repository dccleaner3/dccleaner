package com.dccleaner.app.remote

import com.dccleaner.app.model.calculateQuote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyCleanerPricingFetcherTest {
    @Test
    fun publishedPricingFileParsesAndKeepsGraduatedPrices() {
        // composeApp 모듈에서 실행되므로 저장소 루트의 실제 가격 파일을 읽는다.
        val pricing = ProxyCleanerPricingFetcher.parse(java.io.File("../pricing.json").readText())!!
        assertTrue(pricing.deletionsPerSecond > 0)
        val prices = listOf(1, 10_000, 50_000, 100_000, 1_000_000).map { pricing.calculateQuote(it, 0).finalPrice }
        assertEquals(prices.sorted(), prices)
        assertTrue(pricing.kakaoOpenChatUrl.startsWith("https://open.kakao.com/"))
        // 원 단위 올림 뒤 100원 미만 절사 (서버 security-test.sql과 같은 경계)
        val edge = com.dccleaner.app.model.ProxyCleanerPricing(0.3, 5_000, 3.0, listOf(
            com.dccleaner.app.model.ProxyCleanerPricingTier(1, 50_000, 1.0),
            com.dccleaner.app.model.ProxyCleanerPricingTier(50_001, 100_000, 0.6666666666666666),
            com.dccleaner.app.model.ProxyCleanerPricingTier(100_001, null, 0.3333333333333333)), "")
        assertEquals(5_000, edge.calculateQuote(1_000, 0).finalPrice)
        assertEquals(13_800, edge.calculateQuote(46_019, 0).finalPrice)
        assertEquals(15_000, edge.calculateQuote(50_001, 0).finalPrice)
        assertEquals(15_100, edge.calculateQuote(50_499, 0).finalPrice)
        assertEquals(25_000, edge.calculateQuote(100_001, 0).finalPrice)
    }

    @Test
    fun parsesPricingConfiguration() {
        val pricing = ProxyCleanerPricingFetcher.parse(
            """{
                "baseUnitPrice": 0.1,
                "minimumPrice": 1000,
                "deletionsPerSecond": 1.0,
                "tiers": [
                    {"minCount": 1, "maxCount": 1000, "unitPriceMultiplier": 1.0, "fixedPrice": 1000},
                    {"minCount": 1001, "maxCount": null, "unitPriceMultiplier": 0.5}
                ]
            }""".trimIndent()
        )

        assertEquals(1_000, pricing?.minimumPrice)
        assertEquals(2, pricing?.tiers?.size)
        assertEquals(1.0, pricing?.deletionsPerSecond)
        assertEquals(6_335L, pricing?.calculateQuote(6_335, 0)?.estimatedSeconds)
    }

    @Test
    fun rejectsMissingSpeedInsteadOfGuessing() {
        assertNull(
            ProxyCleanerPricingFetcher.parse(
                """{"baseUnitPrice":0.1,"minimumPrice":5000,"tiers":[{"minCount":1,"maxCount":null,"unitPriceMultiplier":1.0}]}"""
            )
        )
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
