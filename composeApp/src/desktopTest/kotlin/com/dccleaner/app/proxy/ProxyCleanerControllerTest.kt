package com.dccleaner.app.proxy

import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.ProxyCleanerException
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.platform.ProxyCleanerBackend
import com.dccleaner.app.platform.ProxyCleanerStorage
import com.dccleaner.app.ui.proxy.ProxyCleanerController
import com.dccleaner.app.ui.proxy.ProxyStartResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxyCleanerControllerTest {
    private class FakeBackend : ProxyCleanerBackend {
        var balance = 0L
        val purchaseRequests = mutableListOf<Pair<Int, String>>()
        var purchaseFailure: String? = null
        var purchaseException: Exception? = null
        var purchaseGate: CompletableDeferred<Unit>? = null
        var ledger = emptyList<CreditLedgerEntry>()
        var job: ProxyJob? = null
        var jobReadFails = false
        var balanceReadFails = false
        var startRequests = 0
        var startFailure: String? = null
        val seenIds = mutableSetOf<String>()
        var pending: CreditPurchase? = null
        var pendingReadFails = false
        val pendingMargins = mutableListOf<Long>()
        override suspend fun pendingPurchase(requestedCount: Int?, minRemainingMillis: Long): Pair<CreditPurchase, Boolean>? {
            pendingMargins += minRemainingMillis
            if (pendingReadFails) throw IOException("test pending read failure")
            return pending?.takeIf { requestedCount == null || it.requestedCount == requestedCount }?.let { it to (it.id in seenIds) }
        }
        var seenReadFails = false
        override suspend fun purchaseRequestSeen(purchaseId: String): Boolean {
            if (seenReadFails) throw IOException("test seen read failure")
            return purchaseId in seenIds
        }
        override suspend fun ensureUserCode() = "CODE"
        override suspend fun createUserCode() = "CODE"
        override suspend fun balance(): Long {
            if (balanceReadFails) throw IOException("test balance read failure")
            return balance
        }
        override suspend fun createPurchase(requestedCount: Int, requestId: String): CreditPurchase {
            purchaseRequests += requestedCount to requestId
            purchaseGate?.await()
            purchaseException?.let { throw it }
            purchaseFailure?.let { throw ProxyCleanerException(it) }
            return CreditPurchase("purchase", requestedCount, 20_000L, 5_999, "v1")
        }
        override suspend fun ledger() = ledger
        override suspend fun latestJob(accountId: String?): ProxyJob? {
            if (jobReadFails) throw IOException("test job read failure")
            return job
        }
        override suspend fun startJob(deleteType: String, config: ProxyJobConfig, userId: String, password: String): String {
            startRequests++
            startFailure?.let { throw ProxyCleanerException(it) }
            return "j"
        }
        override suspend fun cancelJob(jobId: String) = Unit
        override fun logEvent(name: String, props: Map<String, String>) = Unit
    }

    private object NoStorage : ProxyCleanerStorage {
        override fun readRefreshToken(): String? = null
        override fun writeRefreshToken(token: String) = Unit
        override fun hasConsent() = true
        override fun saveConsent() = Unit
    }

    @Test
    fun reportsOperatorChargesButNotRefunds(): Unit = runBlocking {
        val backend = FakeBackend()
        val controller = ProxyCleanerController(backend, NoStorage)
        controller.refresh()
        assertFalse(controller.runOnServer)

        backend.balance = 5_000L
        backend.ledger = listOf(CreditLedgerEntry(5_000L, "charge", "", ""))
        assertEquals(5_000L, controller.refreshBalance())
        assertTrue(controller.runOnServer)

        backend.balance = 5_200L
        backend.ledger = listOf(CreditLedgerEntry(200L, "refund", "", "")) + backend.ledger
        assertEquals(0L, controller.refreshBalance())

        controller.serverOptOut = true
        assertFalse(controller.runOnServer)
    }

    @Test
    fun reusesPendingRequestAfterRestartAndRestoresIt(): Unit = runBlocking {
        val backend = FakeBackend()
        backend.pending = CreditPurchase("old", 100, 20_000L, 5_999, "v1", "K7Q3")
        backend.seenIds += "old"
        // 앱을 다시 켜면 진행 중인 신청과 확인 상태를 그대로 보여 준다.
        val restarted = ProxyCleanerController(backend, NoStorage)
        restarted.refresh()
        assertEquals("old", restarted.purchase?.id)
        assertTrue(restarted.requestSeen)
        // 같은 개수로 다시 충전하기를 눌러도 새로 만들지 않는다. 다른 개수면 새로 만든다.
        assertEquals("old", restarted.createPurchase(100).first?.id)
        assertTrue(backend.purchaseRequests.isEmpty())
        // 화면 복원은 만료 전이면 되고, 다시 쓸 신청은 만료까지 1시간 넘게 남아야 한다.
        assertEquals(listOf(0L, 60 * 60_000L), backend.pendingMargins)
        assertEquals("purchase", restarted.createPurchase(200).first?.id)
        assertFalse(restarted.requestSeen)
        assertEquals(listOf(200), backend.purchaseRequests.map { it.first })
        // 대기 신청을 읽지 못하면(이전 서버 등) 지금처럼 새로 만든다.
        val fresh = ProxyCleanerController(backend, NoStorage)
        backend.pendingReadFails = true
        fresh.refresh()
        assertEquals(null, fresh.purchase)
        assertEquals("purchase", fresh.createPurchase(100).first?.id)
    }

    @Test
    fun doesNotReusePendingRequestAfterPriceChange(): Unit = runBlocking {
        val backend = FakeBackend()
        backend.pending = CreditPurchase("old", 100, 20_000L, 3_000, "v1", "K7Q3")
        val controller = ProxyCleanerController(backend, NoStorage)
        controller.pricing = com.dccleaner.app.model.ProxyCleanerPricing(0.2, 2_000, 3.0,
            listOf(com.dccleaner.app.model.ProxyCleanerPricingTier(1, null, 1.0)), "")
        controller.refresh()
        // 최소 금액이 3,000원에서 2,000원으로 바뀌었다. 3,000원짜리 옛 신청 대신 새로 만든다.
        assertEquals("purchase", controller.createPurchase(100).first?.id)
        assertEquals(listOf(100), backend.purchaseRequests.map { it.first })
        // 금액이 같으면 다시 쓴다.
        backend.pending = CreditPurchase("same", 200, 20_000L, 2_000, "v2", "A3C4")
        assertEquals("same", controller.createPurchase(200).first?.id)
    }

    @Test
    fun showsRequestSeenUntilChargedAndResetsForANewRequest(): Unit = runBlocking {
        val backend = FakeBackend()
        val controller = ProxyCleanerController(backend, NoStorage)
        controller.refresh()
        controller.createPurchase(100)
        controller.refreshBalance()
        assertFalse(controller.requestSeen)
        // 확인 표시를 못 읽어도 충전 흐름은 그대로다.
        backend.seenReadFails = true
        controller.refreshBalance()
        assertFalse(controller.requestSeen)
        backend.seenReadFails = false
        backend.seenIds += "purchase"
        controller.refreshBalance()
        assertTrue(controller.requestSeen)
        // 충전되면 신청 안내가 사라지고, 새 신청은 다시 1단계부터다.
        backend.balance = 20_000L
        backend.ledger = listOf(CreditLedgerEntry(20_000L, "charge", "", ""))
        assertEquals(20_000L, controller.refreshBalance())
        assertEquals(null, controller.purchase)
        backend.seenIds.clear()
        controller.createPurchase(100)
        controller.refreshBalance()
        assertFalse(controller.requestSeen)
    }

    @Test
    fun purchaseUsesServerQuoteAndReusesIdAfterLostResponse(): Unit = runBlocking {
        val backend = FakeBackend()
        val controller = ProxyCleanerController(backend, NoStorage)
        backend.purchaseFailure = "network"
        assertEquals(null, controller.createPurchase(1_000).first)
        backend.purchaseFailure = null
        val confirmed = controller.createPurchase(1_000).first!!
        assertEquals(20_000L, confirmed.creditedCount)
        assertEquals(5_999, confirmed.amountWon)
        assertEquals(backend.purchaseRequests[0], backend.purchaseRequests[1])
        controller.createPurchase(2_000)
        assertTrue(backend.purchaseRequests[2].second != backend.purchaseRequests[1].second)
        backend.purchaseFailure = "purchase_already_credited"
        assertEquals(null, controller.createPurchase(2_000).first)
        backend.purchaseFailure = null
        controller.createPurchase(2_000)
        assertTrue(backend.purchaseRequests.last().second != backend.purchaseRequests[2].second)
        assertFalse(controller.isCreatingPurchase)
    }

    @Test
    fun repeatedClickDoesNotCreateConcurrentPurchases(): Unit = runBlocking {
        val backend = FakeBackend()
        val gate = CompletableDeferred<Unit>()
        backend.purchaseGate = gate
        val controller = ProxyCleanerController(backend, NoStorage)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { controller.createPurchase(1_000) }
        assertTrue(controller.isCreatingPurchase)
        assertEquals(null, controller.createPurchase(1_000).first)
        assertEquals(1, backend.purchaseRequests.size)
        gate.complete(Unit)
        first.join()
        assertFalse(controller.isCreatingPurchase)
    }

    @Test
    fun internalExceptionMessagesAreNotShownToUsers(): Unit = runBlocking {
        val backend = FakeBackend()
        val controller = ProxyCleanerController(backend, NoStorage)
        backend.purchaseException = IllegalArgumentException("internal provider detail")
        assertEquals("삭제 조건을 확인해 주세요.", controller.createPurchase(1_000).second)
        backend.purchaseException = ProxyCleanerException("java.lang.IllegalStateException: internal provider detail")
        assertEquals("초고속 삭제 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.", controller.createPurchase(1_000).second)
        backend.purchaseException = ProxyCleanerException("pricing_unavailable")
        assertEquals("가격 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요. (pricing_unavailable)",
            controller.createPurchase(1_000).second)
    }

    @Test
    fun lostStartResponseShowsTheServerSideJob(): Unit = runBlocking {
        val config = ProxyJobConfig(
            emptyList(), false, false, false, 1, false, 1, false, 1,
            false, "", false, "", false, false, false, 5, emptyList(), false
        )
        val backend = FakeBackend().apply { balance = 100L }
        val controller = ProxyCleanerController(backend, NoStorage)
        controller.refresh()
        backend.startFailure = "network"
        backend.job = ProxyJob(
            "server-job", "queued", "posting", 100L, null, 0, 0, 0f, 0, 0, "", 0, 0, 0L, "", emptyList(), false, null
        )
        assertEquals(null, controller.start("posting", config, "user", "password"))
        assertEquals("server-job", controller.job?.id)
        assertTrue(controller.showProgress)

        val none = FakeBackend().apply { balance = 100L; startFailure = "network" }
        val other = ProxyCleanerController(none, NoStorage)
        other.refresh()
        assertTrue(other.start("posting", config, "user", "password") != null)
        assertEquals(null, other.job)
    }

    @Test
    fun onlyAJobThisRequestStartedCountsAsStarted(): Unit = runBlocking {
        // 직접 삭제를 넘길 때 기록은 Started일 때만 지운다. 이미 돌던 작업을 찾은 것은 이번 요청이 받아졌다는 뜻이 아니다.
        val config = ProxyJobConfig(
            listOf("g"), false, false, false, 1, false, 1, false, 1,
            false, "", false, "", false, false, false, 5, emptyList(), false
        )
        suspend fun submitWith(backend: FakeBackend, beforeStart: FakeBackend.() -> Unit = {}): ProxyStartResult {
            val controller = ProxyCleanerController(backend.apply { balance = 100L }, NoStorage)
            controller.refresh()
            backend.beforeStart()
            return controller.submit("posting", config, "user", "password")
        }

        assertEquals(ProxyStartResult.Started, submitWith(FakeBackend()))
        assertEquals(ProxyStartResult.Recovered, submitWith(FakeBackend()) {
            startFailure = "job_already_active"
            job = ProxyJob("other-job", "running", "comment", 100L, null, 0, 0, 0f, 0, 0, "", 0, 0, 0L, "", emptyList(), false, null)
        })
        // 서버가 분명히 거절했을 때만 직접 삭제를 다시 켤 수 있다. 응답이 끊기거나 서버 오류면 받았을 수도 있다.
        val refused = submitWith(FakeBackend()) { startFailure = "insufficient_credits" }
        assertTrue(refused is ProxyStartResult.Failed && refused.message != null && !refused.maybeStarted)
        for (code in listOf("network", "http_502", "invalid_server_response")) {
            val unknown = submitWith(FakeBackend()) { startFailure = code }
            assertTrue(unknown is ProxyStartResult.Failed && unknown.maybeStarted, code)
        }
    }

    @Test
    fun purchaseAboveServerLimitIsRejectedLocally(): Unit = runBlocking {
        val backend = FakeBackend()
        val controller = ProxyCleanerController(backend, NoStorage)
        assertEquals(null, controller.createPurchase(10_000_001).first)
        assertTrue(backend.purchaseRequests.isEmpty())
    }

    @Test
    fun acceptedStartSurvivesFollowUpReadFailuresAndPollingRecovers(): Unit = runBlocking {
        val config = ProxyJobConfig(
            emptyList(), false, false, false, 1, false, 1, false, 1,
            false, "", false, "", false, false, false, 5, emptyList(), false
        )
        for (failJobRead in listOf(true, false)) {
            val backend = FakeBackend().apply { balance = 100L }
            val controller = ProxyCleanerController(backend, NoStorage)
            controller.refresh()
            backend.jobReadFails = failJobRead
            backend.balanceReadFails = !failJobRead

            assertEquals(null, controller.start("posting", config, "user", "password"))
            assertEquals("j", controller.job?.id)
            assertTrue(controller.hasActiveJob)
            assertTrue(controller.runOnServer)
            assertTrue(controller.showProgress)
            assertFalse(controller.isStarting)
            assertTrue(controller.start("posting", config, "user", "password") != null)
            assertEquals(1, backend.startRequests)

            backend.jobReadFails = false
            backend.balanceReadFails = false
            backend.balance = 0L
            controller.refreshBalance()
            assertEquals(0L, controller.balance)
            assertTrue(controller.runOnServer)
            backend.job = controller.job!!.copy(status = "completed", consumedCount = 2, deletedCount = 2)
            backend.balance = 98L
            withTimeout(5_000) { controller.pollActiveJob() }
            assertFalse(controller.hasActiveJob)
            assertEquals(2, controller.job?.deletedCount)
            assertEquals(98L, controller.balance)
        }
    }
}
