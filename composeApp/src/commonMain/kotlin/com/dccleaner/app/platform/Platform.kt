package com.dccleaner.app.platform

import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.RemoteBannerConfig

enum class PlatformFamily {
    Android,
    Desktop
}

data class RuntimePlatform(
    val family: PlatformFamily,
    val name: String,
    val appDataDescription: String
)

interface ExternalNavigator {
    fun openUrl(url: String): Boolean
}

interface SecureAccountRepository {
    fun getSavedAccountIds(): List<String>
}

interface ProxyCleanerStorage {
    /** Returns null only when no token was ever saved; throws when secure storage can't be read. */
    fun readRefreshToken(): String?
    fun writeRefreshToken(token: String)
    fun hasConsent(): Boolean
    fun saveConsent()
}

interface ProxyCleanerBackend {
    /** Reads require an existing account and throw ProxyCleanerException("no_account") otherwise. */
    suspend fun ensureUserCode(): String
    /** Creates the anonymous account if needed; only called when the user starts charging. */
    suspend fun createUserCode(): String
    /** Available deletion count. Never falls back to the legacy won balance RPC. */
    suspend fun balance(): Long
    suspend fun createPurchase(requestedCount: Int, requestId: String): CreditPurchase
    /** 운영자가 오픈채팅에서 이 신청서를 확인했는지. 이전 서버처럼 알 수 없으면 false. */
    suspend fun purchaseRequestSeen(purchaseId: String): Boolean = false
    /**
     * 아직 대기 중인(만료까지 [minRemainingMillis] 넘게 남은·미충전) 가장 최근 신청과 운영자가 신청서를 확인했는지.
     * [requestedCount]가 있으면 그 개수의 신청만. 앱을 다시 켜도 만들어 둔 신청을 이어 쓴다. 이전 서버처럼 알 수 없으면 null.
     */
    suspend fun pendingPurchase(requestedCount: Int? = null, minRemainingMillis: Long = 0L): Pair<CreditPurchase, Boolean>? = null
    suspend fun ledger(): List<CreditLedgerEntry>
    /** 모든 사용자의 최근 실제 충전 활동. 계정이 없어도 읽는다. 이전 서버처럼 알 수 없으면 null. */
    suspend fun purchaseActivity(): PurchaseActivity? = null
    /** The newest job of this device user, limited to one DC account when [accountId] is given. */
    suspend fun latestJob(accountId: String? = null): ProxyJob?
    suspend fun startJob(deleteType: String, config: ProxyJobConfig, userId: String, password: String): String
    suspend fun cancelJob(jobId: String)
    fun logEvent(name: String, props: Map<String, String> = emptyMap())
}

expect val currentPlatform: RuntimePlatform

expect fun generateDeleteTaskId(): String

expect fun currentTimeMillis(): Long

expect suspend fun fetchRemoteBannerConfig(localJson: String?): RemoteBannerConfig?

expect suspend fun fetchProxyCleanerPricing(): ProxyCleanerPricing?
