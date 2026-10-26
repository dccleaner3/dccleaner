package com.dccleaner.app.ui.proxy

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.ProxyCleanerException
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.calculateQuote
import com.dccleaner.app.platform.fetchProxyCleanerPricing
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.proxyCleanerErrorMessage
import com.dccleaner.app.platform.ProxyCleanerBackend
import com.dccleaner.app.platform.ProxyCleanerStorage
import com.dccleaner.app.platform.currentTimeMillis
import com.dccleaner.app.platform.generateDeleteTaskId
import com.dccleaner.app.ui.dialog.formatNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val JOB_POLL_INTERVAL_MILLIS = 3_000L
private const val STALLED_POLL_INTERVAL_MILLIS = 30_000L
private const val JOB_STALL_MILLIS = 5 * 60_000L
private const val MAX_PURCHASE_COUNT = 10_000_000
/** 다시 쓸 대기 신청은 만료까지 이만큼은 남아야 한다. 신청서를 보내고 송금할 시간, 기기 시계 오차를 감안한다. */
private const val REUSE_MIN_REMAINING_MILLIS = 60 * 60_000L

/** UI state for credits and the server-side proxy job. Deletion never runs in this app. */
@Stable
class ProxyCleanerController(
    private val backend: ProxyCleanerBackend,
    private val storage: ProxyCleanerStorage
) {
    var userCode by mutableStateOf<String?>(null)
        private set
    /** Available deletion count. */
    var balance by mutableStateOf<Long?>(null)
        private set
    var purchase by mutableStateOf<CreditPurchase?>(null)
        private set
    var isCreatingPurchase by mutableStateOf(false)
        private set
    /** 운영자가 오픈채팅에서 [purchase]의 신청서를 확인했다. 이제 송금하라고 안내한다. */
    var requestSeen by mutableStateOf(false)
        private set
    private var purchaseRequest: Pair<Int, String>? = null
    var pricing by mutableStateOf<ProxyCleanerPricing?>(null)
        internal set
    /** 가격 파일을 받지 못했다. 다시 받을 때까지 가격을 보여 주지 않는다. */
    var pricingFailed by mutableStateOf(false)
        private set
    var ledger by mutableStateOf<List<CreditLedgerEntry>>(emptyList())
        private set
    /** 모든 사용자의 최근 실제 충전 활동. 모르거나 보여 줄 게 없으면 화면에서 숨긴다. */
    var purchaseActivity by mutableStateOf<PurchaseActivity?>(null)
        private set
    var job by mutableStateOf<ProxyJob?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var isStarting by mutableStateOf(false)
        private set
    var isCancelling by mutableStateOf(false)
        private set
    /** When the wait before the next job sync began; with [syncIntervalMillis] it drives the countdown bar. */
    var syncWaitStartedAt by mutableStateOf(0L)
        private set
    var syncIntervalMillis by mutableStateOf(JOB_POLL_INTERVAL_MILLIS)
        private set
    var isSyncing by mutableStateOf(false)
        private set
    /** The last job sync failed; the next poll retries. */
    var syncFailed by mutableStateOf(false)
        private set
    /** A running job whose server heartbeat hasn't changed for several minutes. */
    var jobStalled by mutableStateOf(false)
        private set
    private var observedJobId: String? = null
    private var observedHeartbeat: Long? = null
    private var heartbeatChangedAt = 0L

    /** The logged-in DC account. Each account has its own server job, so several accounts can delete at once. */
    var accountId by mutableStateOf<String?>(null)

    /** Set when the user switches 초고속 삭제 off in the start dialog; it is on by default otherwise. */
    var serverOptOut by mutableStateOf(false)

    // An active job holds the whole balance in reserve, so the balance reads 0 meanwhile.
    val canRunOnServer: Boolean
        get() = hasActiveJob || (balance ?: 0L) >= 1L

    val runOnServer: Boolean
        get() = canRunOnServer && !serverOptOut
    var showProgress by mutableStateOf(false)

    val hasActiveJob: Boolean
        get() = job?.isActive == true

    suspend fun loadPricing() {
        pricing = fetchProxyCleanerPricing()
        pricingFailed = pricing == null
    }

    suspend fun refresh() {
        isRefreshing = true
        try {
            userCode = backend.ensureUserCode()
            balance = backend.balance()
            ledger = backend.ledger()
            job = backend.latestJob(accountId)
            // 앱을 다시 켜도 진행 중인 충전 신청을 이어서 보여 준다.
            if (purchase == null) restorePendingPurchase(null, 0L)
            loadError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProxyCleanerException) {
            if (e.code == "no_account") {
                // Nothing was ever charged on this device, so there is nothing to look up.
                userCode = null
                balance = 0L
                ledger = emptyList()
                job = null
                loadError = null
            } else {
                loadError = e.toProxyMessage()
            }
        } catch (e: Exception) {
            loadError = e.toProxyMessage()
        } finally {
            isRefreshing = false
        }
    }

    /** 최근 충전 활동을 다시 읽는다. 보여 주기용이라 실패하면 마지막 값을 그대로 둔다. */
    suspend fun loadPurchaseActivity() {
        try {
            purchaseActivity = backend.purchaseActivity()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** Reuses the request ID after a lost response; the server owns pricing and issuance. */
    suspend fun createPurchase(requestedCount: Int): Pair<CreditPurchase?, String?> {
        if (isCreatingPurchase) return null to "충전 견적을 확인하고 있습니다."
        if (requestedCount <= 0) return null to "삭제할 개수를 입력해 주세요."
        if (requestedCount > MAX_PURCHASE_COUNT) return null to proxyCleanerErrorMessage("invalid_requested_count")
        isCreatingPurchase = true
        return try {
            userCode = userCode ?: backend.createUserCode()
            // 앱을 다시 켰어도 같은 개수·같은 금액으로 만들어 둔 대기 신청이 있으면 새로 만들지 않고 그걸 쓴다.
            // 가격이 바뀐 뒤의 옛 신청은 화면 견적과 금액이 달라지므로 다시 쓰지 않는다.
            val expectedAmount = pricing?.calculateQuote(requestedCount, 0)?.finalPrice
            if (purchaseRequest?.first != requestedCount &&
                restorePendingPurchase(requestedCount, REUSE_MIN_REMAINING_MILLIS) { expectedAmount == null || it.amountWon == expectedAmount }) {
                backend.logEvent("charge_click")
                return purchase to null
            }
            val request = purchaseRequest?.takeIf { it.first == requestedCount }
                ?: (requestedCount to generateDeleteTaskId()).also { purchaseRequest = it }
            val confirmed = backend.createPurchase(requestedCount, request.second)
            if (confirmed.id != purchase?.id) requestSeen = false
            purchase = confirmed
            backend.logEvent("charge_click")
            confirmed to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ProxyCleanerException && e.code in setOf("purchase_already_credited", "purchase_expired")) {
                purchaseRequest = null
                purchase = null
            }
            null to e.toProxyMessage()
        } finally {
            isCreatingPurchase = false
        }
    }

    /** 서버의 대기 신청을 [purchase]로 불러온다. 찾았으면 true. 읽지 못하면(이전 서버 등) 없는 것으로 본다. */
    private suspend fun restorePendingPurchase(
        requestedCount: Int?, minRemainingMillis: Long, accept: (CreditPurchase) -> Boolean = { true }
    ): Boolean {
        val (pending, seen) = try { backend.pendingPurchase(requestedCount, minRemainingMillis) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return false
        if (!accept(pending)) return false
        if (pending.id != purchase?.id) purchaseRequest = null
        purchase = pending
        requestSeen = seen
        return true
    }

    /** Re-reads the balance; returns the amount just charged by the operator, or 0. */
    suspend fun refreshBalance(): Long {
        if (userCode == null) return 0L
        val previous = balance ?: 0L
        purchase?.takeIf { !requestSeen }?.let { pending ->
            // 확인 표시는 안내용이라 실패해도 다음 갱신 때 다시 본다.
            try { if (backend.purchaseRequestSeen(pending.id) && purchase?.id == pending.id) requestSeen = true }
            catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        return try {
            val current = backend.balance()
            balance = current
            if (current == previous) return 0L
            ledger = backend.ledger()
            // Refunds also raise the balance; only report operator charges.
            if (current > previous && ledger.firstOrNull()?.kind == "charge") {
                purchaseRequest = null
                purchase = null
                requestSeen = false
                current - previous
            } else 0L
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0L
        }
    }

    /** Polls the job row until it settles, then reloads the balance the server refunded. */
    suspend fun pollActiveJob() {
        while (job?.isActive == true || job?.needsSettlement == true) {
            syncIntervalMillis = if (jobStalled || job?.needsSettlement == true) STALLED_POLL_INTERVAL_MILLIS else JOB_POLL_INTERVAL_MILLIS
            syncWaitStartedAt = currentTimeMillis()
            delay(syncIntervalMillis)
            isSyncing = true
            try {
                // 잔고 조회가 서버에서 멈춘 작업을 정산한다(heartbeat 30분 기준).
                if (jobStalled || job?.needsSettlement == true) backend.balance()
                backend.latestJob(accountId)?.takeIf { it.id == job?.id }?.let { job = it; observeHeartbeat(it) }
                syncFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep showing the last known row; the next poll retries.
                syncFailed = true
            } finally {
                isSyncing = false
            }
        }
        jobStalled = false
        syncFailed = false
        try {
            balance = backend.balance()
            ledger = backend.ledger()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun observeHeartbeat(row: ProxyJob) {
        val now = currentTimeMillis()
        if (row.id != observedJobId || row.heartbeatAtMillis != observedHeartbeat) {
            observedJobId = row.id
            observedHeartbeat = row.heartbeatAtMillis
            heartbeatChangedAt = now
        }
        jobStalled = row.status == "running" && now - heartbeatChangedAt > JOB_STALL_MILLIS
    }

    /** After a lost start response the job may exist server-side; show it instead of hiding it. */
    private suspend fun recoverActiveJob(): Boolean = try {
        backend.latestJob(accountId)?.takeIf { it.isActive }?.let {
            job = it
            showProgress = true
            true
        } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    fun hasConsent(): Boolean = runCatching { storage.hasConsent() }.getOrDefault(false)

    fun saveConsent(): Boolean = runCatching { storage.saveConsent() }.isSuccess

    /** Returns a Korean error message, or null when the job was queued. */
    suspend fun start(deleteType: String, config: ProxyJobConfig, userId: String, password: String): String? =
        (submit(deleteType, config, userId, password) as? ProxyStartResult.Failed)?.message

    /** [start]와 같지만, 이번 요청을 서버가 받았는지와 이미 있던 작업을 찾아 보여 주는 것을 구분한다. */
    suspend fun submit(deleteType: String, config: ProxyJobConfig, userId: String, password: String): ProxyStartResult {
        if (isStarting) return ProxyStartResult.Failed(null)
        if (hasActiveJob) return ProxyStartResult.Failed(proxyCleanerErrorMessage("job_already_active"))
        isStarting = true
        accountId = userId
        backend.logEvent("proxy_start_click", mapOf("delete_type" to deleteType))
        return try {
            userCode = backend.ensureUserCode()
            val jobId = backend.startJob(deleteType, config, userId, password)
            // Acceptance must survive a failed follow-up read so polling and cancellation retain the ID.
            job = ProxyJob(
                id = jobId, status = "queued", deleteType = deleteType,
                reservedCount = balance ?: 0L, consumedCount = null,
                deletedCount = 0, skippedCount = 0, progress = 0f,
                galleriesDone = 0, galleriesTotal = 0, currentGallery = "",
                speedPerHour = 0, recentSpeedPerHour = 0, etaSeconds = 0L,
                statusMessage = "작업이 접수되었습니다. 진행 상태를 확인하고 있습니다.",
                logTail = emptyList(), cancelRequested = false, error = null
            )
            showProgress = true
            try {
                backend.logEvent("proxy_start_success", mapOf("delete_type" to deleteType))
                backend.latestJob(accountId)?.takeIf { it.id == jobId }?.let { job = it }
                balance = backend.balance()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The job is already queued; pollActiveJob retries without submitting it again.
            }
            ProxyStartResult.Started
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ProxyCleanerException && e.code in setOf("network", "job_already_active") && recoverActiveJob()) ProxyStartResult.Recovered
            else ProxyStartResult.Failed(e.toProxyMessage(), maybeStarted = !(e is ProxyCleanerException && e.isDefiniteRefusal()))
        } finally {
            isStarting = false
        }
    }

    suspend fun cancel(): String? {
        val jobId = job?.id ?: return null
        if (isCancelling) return null
        isCancelling = true
        return try {
            backend.cancelJob(jobId)
            job = backend.latestJob(accountId) ?: job
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toProxyMessage()
        } finally {
            isCancelling = false
        }
    }

    fun logEvent(name: String) = backend.logEvent(name)
}

sealed interface ProxyStartResult {
    /** 서버가 이번 요청으로 새 작업을 받았다. */
    data object Started : ProxyStartResult
    /** 응답을 놓쳤거나 이미 돌던 작업을 찾아 보여 준다. 이번 요청이 받아졌는지는 모른다. */
    data object Recovered : ProxyStartResult
    /**
     * 시작하지 못했다. 이미 시작하는 중이면 [message]가 null이다.
     * [maybeStarted]면 응답이 끊기거나 서버 오류라, 서버가 이미 받았거나 다른 작업이 돌고 있을 수 있다.
     */
    data class Failed(val message: String?, val maybeStarted: Boolean = false) : ProxyStartResult
}

/** 서버가 요청을 분명히 거절했다(받지 않았다). 응답이 끊겼거나 서버 오류·이미 진행 중인 작업이면 받았을 수도 있다. */
private fun ProxyCleanerException.isDefiniteRefusal(): Boolean =
    code !in setOf("network", "invalid_server_response", "job_already_active") && !code.startsWith("http_5")

private fun Exception.toProxyMessage(): String = when (this) {
    is ProxyCleanerException -> proxyCleanerErrorMessage(code)
    is IllegalArgumentException -> "삭제 조건을 확인해 주세요."
    else -> proxyCleanerErrorMessage("unknown")
}

/** 초고속 삭제가 끝났을 때 알림 문구. Android 백그라운드 확인과 PC 트레이 알림이 같이 쓴다. */
fun proxyJobFinishedMessage(job: ProxyJob): String {
    val deleted = "${formatNumber(job.deletedCount.toLong())}개"
    return when (job.status) {
        "completed" -> "초고속 삭제 완료 · $deleted 삭제했어요"
        "cancelled" -> "초고속 삭제를 중단했어요 · $deleted 삭제했어요"
        else -> "초고속 삭제가 멈췄어요 · $deleted 삭제했어요. 앱에서 확인해 주세요."
    }
}
