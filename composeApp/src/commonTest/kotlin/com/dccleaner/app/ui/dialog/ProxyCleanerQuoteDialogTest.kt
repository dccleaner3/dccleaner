package com.dccleaner.app.ui.dialog

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.dccleaner.app.ui.proxy.ULTRA_COLORS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProxyCleanerQuoteDialogTest {
    @Test fun ultraGradientKeepsWhiteTextReadable() {
        ULTRA_COLORS.zipWithNext().forEach { (start, end) ->
            for (step in 0..20) {
                val background = lerp(start, end, step / 20f)
                assertTrue(1.05f / (background.luminance() + 0.05f) >= 4.5f)
            }
        }
    }

    @Test fun estimatesTwoCaptchaUsageSeparatelyFromTopUp() {
        assertEquals(0, twoCaptchaEstimatedCost(0))
        assertEquals(4, twoCaptchaEstimatedCost(200))
        assertEquals(8, twoCaptchaEstimatedCost(201))
        assertEquals(200, twoCaptchaEstimatedCost(10_000))
        assertEquals(4_500, twoCaptchaPayment(10_000))
        assertEquals(4_500, twoCaptchaPayment(225_000))
        assertEquals(4_504, twoCaptchaPayment(225_001))
    }

    @Test
    fun derivesHourlySpeedFromPricingRate() {
        assertEquals(6_336, hourlyDeletionCount(1.76))
        assertEquals(3_600, hourlyDeletionCount(1.0))
        assertEquals(3.3, speedMultiplier(6_336, 1_900))
        assertEquals(3.5, speedMultiplier(6_336, 1_800))
        assertEquals(7.0, speedMultiplier(6_336, 900))
        assertEquals(63.4, speedMultiplier(6_336, 100))
    }

    @Test
    fun roundsDisplayedTimesWithoutSeconds() {
        assertEquals("1일 6시간", formatApproximateDuration(29L * 3_600 + 56 * 60 + 26))
        assertEquals("1일", formatApproximateDuration(24L * 3_600))
        assertEquals("2일", formatApproximateDuration(48L * 3_600))
        assertEquals("2일 7시간 30분", formatApproximateDuration(55L * 3_600 + 30 * 60))
        assertEquals("1일 5시간", formatApproximateDuration(29L * 3_600))
        assertEquals("8시간 30분", formatApproximateDuration(8L * 3_600 + 30 * 60 + 26))
        assertEquals("21시간 30분", formatApproximateDuration(21L * 3_600 + 26 * 60))
        assertEquals("1분 미만", formatApproximateDuration(30))
    }

    @Test
    fun applicationMessageOmitsDeletionDetails() {
        assertEquals(
            """잔고 충전하기
총 삭제 대상: 2400개
총 견적 금액: 1700원
결제 방식: 오픈채팅 송금하기""",
            buildProxyCleanerApplicationMessage(
                totalCount = 2400,
                finalPrice = 1700
            )
        )
    }

    @Test
    fun galleryScopeRemainsAvailableForDisplay() {
        assertEquals(
            "작업 범위: 선택한 1개 갤러리에서만 삭제\n삭제할 갤러리: 책 갤러리",
            buildGalleryScopeText(
                linkedMapOf("book" to "책 갤러리", "baseball_new13" to "국내야구 갤러리", "test" to "테스트"),
                listOf("book")
            )
        )
        assertEquals(
            "작업 범위: 전체 갤러리에서 삭제",
            buildGalleryScopeText(linkedMapOf("book" to "책 갤러리"), listOf("book"))
        )
    }
}
