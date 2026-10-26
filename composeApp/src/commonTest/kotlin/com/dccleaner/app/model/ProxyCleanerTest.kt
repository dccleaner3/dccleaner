package com.dccleaner.app.model

import com.dccleaner.app.ui.dialog.buildProxyCleanerApplicationMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProxyCleanerTest {
    @Test
    fun formatsWonAsRoundedWholeNumbers() {
        assertEquals("29,894원", formatWon(29_894.0))
        assertEquals("5,000원", formatWon(5_000.0))
        assertEquals("1,235원", formatWon(1_234.5))
        assertEquals("1,234원", formatWon(1_234.4))
        assertEquals("1,000,000원", formatWon(1_000_000.0))
        assertEquals("-36원", formatWon(-36.0))
        assertEquals("0원", formatWon(0.0))
        assertEquals("0원", formatWon(0.3))
    }

    @Test
    fun recentPurchaseAgeUsesTheLargestWholeUnit() {
        assertEquals("방금", formatTimeAgo(-3))
        assertEquals("방금", formatTimeAgo(4))
        assertEquals("방금", formatTimeAgo(5))
        assertEquals("방금", formatTimeAgo(59))
        assertEquals("1분 전", formatTimeAgo(60))
        assertEquals("59분 전", formatTimeAgo(3_599))
        assertEquals("1시간 전", formatTimeAgo(3_600))
        assertEquals("23시간 전", formatTimeAgo(86_399))
        assertEquals("3일 전", formatTimeAgo(3 * 86_400 + 5))
    }

    @Test
    fun purchaseSummaryShowsOnlyRealAndRecentEnoughActivity() {
        val now = 1_000_000_000L
        fun activity(day: Int, week: Int, latestSecondsAgo: Long? = null) = PurchaseActivity(day, week,
            listOfNotNull(latestSecondsAgo?.let { RecentCreditPurchase(now - it * 1_000) }))
        assertEquals(listOf("최근 24시간 동안 12명이 충전했어요", "마지막 충전 3분 전"), activity(12, 40, 180).summary(now))
        assertEquals(listOf("최근 24시간 동안 3명이 충전했어요", "방금 충전"), activity(3, 3, 2).summary(now))
        // 1분 안은 초를 세지 않는다.
        assertEquals(listOf("최근 24시간 동안 3명이 충전했어요", "방금 충전"), activity(3, 3, 37).summary(now))
        assertEquals(listOf("최근 24시간 동안 3명이 충전했어요", "마지막 충전 1분 전"), activity(3, 3, 60).summary(now))
        // 기기 시계를 되돌려 경과 시간이 음수면 시각을 붙이지 않는다.
        assertEquals(listOf("최근 24시간 동안 3명이 충전했어요"), activity(3, 3, -120).summary(now))
        // 마지막 충전이 한 시간보다 오래면 시각은 빼고 사람 수만.
        assertEquals(listOf("최근 24시간 동안 5명이 충전했어요"), activity(5, 9, 3_600).summary(now))
        // 하루 사람이 적으면 일주일로 넓히고, 그래도 적으면 아무것도 띄우지 않는다.
        assertEquals(listOf("최근 7일 동안 8명이 충전했어요"), activity(2, 8, 7_200).summary(now))
        assertEquals(null, activity(1, 2, 10).summary(now))
        assertEquals(null, activity(0, 0).summary(now))
        assertEquals(listOf(true, true, false), listOf(activity(3, 3), activity(0, 3), activity(2, 2)).map { it.hasSummary })
    }

    @Test
    fun unitPriceKeepsItsDecimals() {
        assertEquals("0.3원", formatUnitPrice(0.3))
        assertEquals("0.25원", formatUnitPrice(0.25))
        assertEquals("1원", formatUnitPrice(1.0))
    }

    @Test
    fun pricingPreviewDoesNotConvertRoundedMoneyBackIntoCounts() {
        val pricing = TEST_PRICING
        assertEquals(46_019L, pricing.calculateQuote(6, 46_013).creditedCount)
        assertEquals(15_000L, pricing.calculateQuote(1_000, 0).creditedCount)
        assertEquals(0L, pricing.calculateQuote(0, 0).creditedCount)
    }

    @Test
    fun lastTrashLineBecomesTheProgressLineWhenMarkerIsStripped() {
        val logs = listOf("[10:00:00] 🚀 갤러리 삭제 시작", "[10:00:05] 🗑️ 10/100 (10%)", "[10:00:10] 🗑️ 20/100 (20%)")

        val marked = logs.withDeleteProgressMarked()

        assertTrue(marked.last().isDeleteProgressLog())
        assertTrue(!marked.first().isDeleteProgressLog())
        assertTrue(!marked[1].isDeleteProgressLog())
        assertEquals("[10:00:10] 🗑️ 20/100 (20%)", marked.last().deleteLogDisplayText())
        assertEquals(marked, marked.withDeleteProgressMarked())
    }

    @Test
    fun progressLineEndsOnceItsGalleryCompletes() {
        val completed = listOf("🗑️ 100/100 (100%)", "✅ 자유 삭제 완료 100 · 0")
        assertEquals(completed, completed.withDeleteProgressMarked())

        val untouched = listOf("🚀 갤러리 삭제 시작", "처리 중")
        assertEquals(untouched, untouched.withDeleteProgressMarked())
    }

    @Test
    fun linesWithoutTimestampsGetTheMarker() {
        assertTrue(listOf("🗑️ 3/9 (33%)").withDeleteProgressMarked().single().isDeleteProgressLog())
    }

    @Test
    fun detectsBalanceExhaustedCompletion() {
        val job = ProxyJob(
            id = "j", status = "completed", deleteType = "posting", reservedCount = 333L, consumedCount = 333L, deletedCount = 333,
            skippedCount = 0, progress = 0.5f, galleriesDone = 1, galleriesTotal = 2, currentGallery = "",
            speedPerHour = 0, recentSpeedPerHour = 0, etaSeconds = 0,
            statusMessage = PROXY_BALANCE_EXHAUSTED_MESSAGE, logTail = emptyList(), cancelRequested = false, error = null
        )
        assertTrue(job.stoppedByBalance)
        assertTrue(!job.copy(status = "cancelled").stoppedByBalance)
        assertTrue(!job.copy(statusMessage = "완료").stoppedByBalance)
    }

    @Test
    fun volumeDiscountOnlyChangesPaymentNotDeletionUnits() {
        val pricing = TEST_PRICING
        val small = pricing.calculateQuote(1_000, 0)
        assertEquals(3_000, small.finalPrice)
        assertEquals(15_000L, small.creditedCount)
        assertEquals(
            """잔고 충전하기
총 삭제 대상: 1000개
총 견적 금액: 3000원
결제 방식: 오픈채팅 송금하기""",
            buildProxyCleanerApplicationMessage(small.totalCount, small.finalPrice)
        )
        val large = pricing.calculateQuote(200_000, 100_000)
        assertEquals(24_000, large.finalPrice)
        assertEquals(300_000L, large.creditedCount)
        assertEquals(
            """잔고 충전하기
총 삭제 대상: 300000개
총 견적 금액: 24000원
결제 방식: 오픈채팅 송금하기""",
            buildProxyCleanerApplicationMessage(large.totalCount, large.finalPrice)
        )
    }

    @Test
    fun fullPricePurchaseStillGrantsRequestedCount() {
        val pricing = TEST_PRICING.copy(tiers = listOf(ProxyCleanerPricingTier(1, null, 1.0)))
        val quote = pricing.calculateQuote(300_000, 0)
        assertEquals(60_000, quote.finalPrice)
        assertEquals(300_000L, quote.creditedCount)
    }

    @Test
    fun selectingEveryGallerySendsEmptyScope() {
        assertEquals(emptyList(), proxyGalleryScope(listOf("b", "a"), listOf("a", "b")))
        assertEquals(listOf("a"), proxyGalleryScope(listOf("a"), listOf("a", "b")))
    }

    @Test
    fun serverErrorCodesHaveKoreanMessages() {
        val generic = proxyCleanerErrorMessage("unknown")
        listOf(
            "insufficient_credits", "job_already_active", "invalid_config", "invalid_credentials",
            "profile_missing", "job_not_found", "not_authenticated", "purchase_pending_limit",
            "purchase_rate_limit", "job_rate_limit", "invalid_requested_count", "permission denied for function x",
            "pricing_unavailable", "unsupported_option", "invalid_delete_type", "event_rate_limit"
        ).forEach { assertNotEquals(generic, proxyCleanerErrorMessage(it), it) }
        assertEquals(generic, proxyCleanerErrorMessage("weird_code"))
        assertEquals(generic, proxyCleanerErrorMessage("java.lang.IllegalStateException: internal provider failure"))
        assertTrue("pricing_unavailable" in proxyCleanerErrorMessage("pricing_unavailable"))
    }

    @Test
    fun serverJobMessagesKeepPublicStatusAndRejectInternalDetails() {
        listOf(
            "작업 준비 중", "로그인 중", "갤러리 목록 확인 중", "삭제 진행 중", "캡챠 해결 중", "삭제 완료",
            "작업을 잠시 멈췄습니다.", "로그인에 실패했습니다. 아이디와 비밀번호를 확인해 주세요.",
            "삭제 응답을 확인하고 있습니다. 사용자 코드와 함께 문의해 주세요.", PROXY_BALANCE_EXHAUSTED_MESSAGE
        ).forEach { assertEquals(it, proxyJobMessage(it)) }
        listOf(
            "외부 서비스 캡챠 해결 중", "java.lang.IllegalStateException: internal provider failure",
            "삭제 완료\ninternal detail", "내부 종료로 중단했습니다."
        ).forEach {
            assertEquals("작업 상태를 확인하고 있습니다.", proxyJobMessage(it))
            assertEquals("서버 오류로 중단되었습니다.", proxyJobMessage(it, error = true))
        }
        assertEquals(proxyCleanerErrorMessage("pricing_unavailable"), proxyJobMessage("pricing_unavailable", error = true))
    }

    @Test
    fun returnedCountIsReservedMinusConsumed() {
        val job = ProxyJob(
            id = "j", status = "completed", deleteType = "posting", reservedCount = 5_000L, consumedCount = 120L, deletedCount = 120,
            skippedCount = 4, progress = 1f, galleriesDone = 2, galleriesTotal = 2, currentGallery = "",
            speedPerHour = 0, recentSpeedPerHour = 0, etaSeconds = 0, statusMessage = "", logTail = emptyList(),
            cancelRequested = false, error = null
        )
        assertEquals(4_880L, job.returnedCount)
        assertEquals(null, job.copy(consumedCount = null).returnedCount)
        assertEquals(false, job.isActive)
        assertEquals(true, job.copy(status = "queued").isActive)
    }
}

/** 실제 가격은 pricing.json에만 둔다. 계산 로직 검증용 고정 값. */
private val TEST_PRICING = ProxyCleanerPricing(
    baseUnitPrice = 0.2,
    minimumPrice = 3_000,
    deletionsPerSecond = 3.0,
    tiers = listOf(
        ProxyCleanerPricingTier(1, 15_000, 1.0),
        ProxyCleanerPricingTier(15_001, 50_000, 0.5),
        ProxyCleanerPricingTier(50_001, null, 0.35)
    ),
    kakaoOpenChatUrl = "https://open.kakao.com/o/test"
)
