package com.dccleaner.app.proxy

import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.ProxyCleanerException
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.RecentCreditPurchase
import com.dccleaner.app.platform.ProxyCleanerBackend
import com.dccleaner.app.platform.ProxyCleanerStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

const val SUPABASE_URL = "https://swmfyjvwrvpmtbvhtirc.supabase.co"
const val SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN3bWZ5anZ3cnZwbXRidmh0aXJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTExMDc5MjYsImV4cCI6MjEwNjY4MzkyNn0.He-DXWlk57fDf4FQsItHD9479CTBxBNFQ6itACH8Aq0"

private val JSON = "application/json".toMediaType()

/**
 * Talks to the proxy cleaner's Supabase project over plain REST. Deletion itself runs on the
 * operator's private server; this client only requests jobs and reads their rows.
 */
class SupabaseProxyCleanerBackend(
    private val storage: ProxyCleanerStorage,
    private val baseUrl: String = SUPABASE_URL,
    private val anonKey: String = SUPABASE_ANON_KEY,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
        .build()
) : ProxyCleanerBackend, AutoCloseable {
    private val sessionMutex = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun ensureUserCode(): String = rpc("ensure_profile").stringValue()

    override suspend fun createUserCode(): String =
        send("rest/v1/rpc/ensure_profile", JsonObject(emptyMap()), allowSignup = true).stringValue()

    override suspend fun balance(): Long = rpc("my_deletion_balance").jsonPrimitive.content.toLongOrNull()
        ?.takeIf { it >= 0 } ?: throw ProxyCleanerException("invalid_server_response")

    override suspend fun createPurchase(requestedCount: Int, requestId: String): CreditPurchase {
        require(requestedCount > 0)
        val purchase = rpc("create_credit_purchase", buildJsonObject {
            put("p_requested_count", requestedCount)
            put("p_request_id", requestId)
        }).jsonObject.toCreditPurchase()
        if (purchase.requestedCount != requestedCount) throw ProxyCleanerException("invalid_server_response")
        return purchase
    }

    override suspend fun pendingPurchase(requestedCount: Int?, minRemainingMillis: Long): Pair<CreditPurchase, Boolean>? {
        val now = java.time.Instant.now().plusMillis(minRemainingMillis.coerceAtLeast(0)).toString()
        val row = (get("credit_purchases?select=id,requested_count,credited_count,amount_won,pricing_version," +
            "code:short_code,request_seen_at&status=eq.pending&expires_at=gt.$now" +
            (requestedCount?.let { "&requested_count=eq.$it" } ?: "") + "&order=created_at.desc&limit=1") as JsonArray)
            .firstOrNull()?.jsonObject ?: return null
        return row.toCreditPurchase() to ((row["request_seen_at"] as? JsonPrimitive)?.contentOrNull != null)
    }

    override suspend fun purchaseRequestSeen(purchaseId: String): Boolean = purchaseProgress(purchaseId)?.second == true

    /** 신청의 상태(pending/credited)와 운영자가 신청서를 확인했는지. 신청을 못 찾으면 null. */
    suspend fun purchaseProgress(purchaseId: String): Pair<String, Boolean>? {
        val id = java.util.UUID.fromString(purchaseId).toString()
        val row = (get("credit_purchases?select=status,request_seen_at&id=eq.$id") as JsonArray).firstOrNull()?.jsonObject ?: return null
        val status = (row["status"] as? JsonPrimitive)?.contentOrNull ?: return null
        return status to ((row["request_seen_at"] as? JsonPrimitive)?.contentOrNull != null)
    }

    override suspend fun ledger(): List<CreditLedgerEntry> =
        (get("deletion_ledger?select=delta_count,kind,note,created_at&order=created_at.desc&limit=50") as JsonArray)
            .map { it.jsonObject.toLedgerEntry() }

    override suspend fun purchaseActivity(): PurchaseActivity {
        // 계정 없는 사람에게도 보여 주는 공개 기록이라 세션 없이 anon 키로 읽는다(가입·토큰 갱신을 일으키지 않는다).
        val body = execute("rest/v1/rpc/credit_purchase_activity", JsonObject(emptyMap()), prefer = null, bearer = anonKey)
            .parse() as? JsonObject ?: throw ProxyCleanerException("invalid_server_response")
        fun count(key: String) = (body[key] as? JsonPrimitive)?.longOrNull?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0
        val now = System.currentTimeMillis()
        val recent = (body["recent"] as? JsonArray).orEmpty().take(5).mapNotNull { row ->
            val fields = row as? JsonObject ?: return@mapNotNull null
            // 서버는 7일 안의 기록만 준다. 그 밖의 값(음수·터무니없이 큰 값)은 시각 계산이 넘쳐 '방금'으로 보일 수 있어 버린다.
            val secondsAgo = (fields["seconds_ago"] as? JsonPrimitive)?.longOrNull?.takeIf { it in 0..MAX_ACTIVITY_AGE_SECONDS }
                ?: return@mapNotNull null
            RecentCreditPurchase(creditedAtMillis = now - secondsAgo * 1_000)
        }
        return PurchaseActivity(buyers24h = count("buyers_24h"), buyers7d = count("buyers_7d"), recent = recent)
    }

    override suspend fun latestJob(accountId: String?): ProxyJob? =
        (get("jobs?select=$JOB_COLUMNS" + (accountId?.takeIf(String::isNotBlank)
            ?.let { "&dc_account_hash=eq.${accountHash(it)}" } ?: "") + "&order=created_at.desc&limit=1") as JsonArray)
            .firstOrNull()?.jsonObject?.toProxyJob()

    override suspend fun startJob(deleteType: String, config: ProxyJobConfig, userId: String, password: String): String {
        require(deleteType == "posting" || deleteType == "comment")
        if (config.deleteNewestFirst) throw ProxyCleanerException("unsupported_newest_first")
        // 이어 삭제면 두 번째 단계 종류의 정규식도 막는다(서버도 거절하지만 보내기 전에 막는다).
        val types = listOfNotNull(deleteType, config.followUp?.deleteType)
        if (config.followUp != null && config.followUp.deleteType !in setOf("posting", "comment") - deleteType) {
            throw ProxyCleanerException("invalid_config")
        }
        if (("posting" in types && config.postContentFilterEnabled) ||
            ("comment" in types && config.commentContentFilterEnabled)) {
            throw ProxyCleanerException("unsupported_regex_filter")
        }
        val envelope = sealCredentials(userId, password)
        return rpc("start_deletion_job", buildJsonObject {
            put("p_delete_type", deleteType)
            put("p_config", config.toJson())
            put("p_credentials", envelope)
            // 같은 DC 계정의 중복 시작만 막는다. 서버가 인수 후 같은 방식(소문자 아이디의 SHA-256)으로 다시 기록한다.
            put("p_account_hash", accountHash(userId))
        }).stringValue()
    }

    override suspend fun cancelJob(jobId: String) {
        rpc("cancel_deletion_job", buildJsonObject { put("p_job", jobId) })
    }

    private suspend fun sealCredentials(userId: String, password: String): String =
        withContext(Dispatchers.Default) { CredentialEnvelope.seal(userId, password) }

    override fun logEvent(name: String, props: Map<String, String>) {
        eventScope.launch {
            runCatching {
                send("rest/v1/events", buildJsonObject {
                    put("name", name)
                    put("props", JsonObject(props.mapValues { JsonPrimitive(it.value) }))
                }, prefer = "return=minimal")
            }
        }
    }

    private suspend fun rpc(name: String, args: JsonObject = JsonObject(emptyMap())): JsonElement =
        send("rest/v1/rpc/$name", args)

    private suspend fun get(path: String): JsonElement = send("rest/v1/$path", body = null)

    private suspend fun send(
        path: String,
        body: JsonObject?,
        prefer: String? = null,
        allowSignup: Boolean = false
    ): JsonElement {
        val first = execute(path, body, prefer, authorizedToken(allowSignup = allowSignup))
        if (first.code != 401) return first.parse()
        // The access token can expire between our clock check and the request; refresh once.
        return execute(path, body, prefer, authorizedToken(forceRefresh = true)).parse()
    }

    private suspend fun authorizedToken(forceRefresh: Boolean = false, allowSignup: Boolean = false): String = sessionMutex.withLock {
        val current = accessToken
        if (!forceRefresh && current != null && System.currentTimeMillis() < accessTokenExpiresAt) {
            return@withLock current
        }
        val stored = try {
            withContext(Dispatchers.IO) { storage.readRefreshToken() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw ProxyCleanerException("token_read_failed")
        }
        // Accounts are created only when the user asks to charge credits; until then account reads report
        // "no_account" without touching the network (only the public purchaseActivity is read without one). Signing up again would also hand out a new
        // code and orphan purchased credits, so a failing refresh token is reported, not replaced.
        if (stored.isNullOrBlank() && !allowSignup) throw ProxyCleanerException("no_account")
        // Once the server rotates the refresh token, saving the new one must not be cancelled
        // (e.g. by a tab change), or the old token stops working and the balance is unreachable.
        withContext(NonCancellable) {
            val session = if (stored.isNullOrBlank()) {
                authRequest("auth/v1/signup", JsonObject(emptyMap()), failureCode = "signup_failed")
            } else {
                authRequest(
                    "auth/v1/token?grant_type=refresh_token",
                    buildJsonObject { put("refresh_token", stored) },
                    failureCode = "session_invalid"
                )
            }
            val refreshToken = session["refresh_token"]?.jsonPrimitive?.contentOrNull
            val token = session["access_token"]?.jsonPrimitive?.contentOrNull
            if (refreshToken.isNullOrBlank() || token.isNullOrBlank()) throw ProxyCleanerException("session_invalid")
            try {
                withContext(Dispatchers.IO) { storage.writeRefreshToken(refreshToken) }
            } catch (_: Exception) {
                throw ProxyCleanerException("token_storage_failed")
            }
            val expiresIn = session["expires_in"]?.jsonPrimitive?.longOrNull ?: 3_600L
            accessToken = token
            accessTokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60).coerceAtLeast(30) * 1_000
            token
        }
    }

    private suspend fun authRequest(path: String, body: JsonObject, failureCode: String): JsonObject {
        val response = execute(path, body, prefer = null, bearer = null)
        if (response.code in 400..499) {
            val errorCode = runCatching {
                Json.parseToJsonElement(response.text).jsonObject["error_code"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            // A disabled anonymous provider is an operator setting, not a lost session.
            throw ProxyCleanerException(if (errorCode == "anonymous_provider_disabled") errorCode else failureCode)
        }
        return response.parse().jsonObject
    }

    private suspend fun execute(path: String, body: JsonObject?, prefer: String?, bearer: String?): RawResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/$path")
                .header("apikey", anonKey)
                .apply {
                    if (bearer != null) header("Authorization", "Bearer $bearer")
                    if (prefer != null) header("Prefer", prefer)
                    if (body != null) post(body.toString().toRequestBody(JSON)) else get()
                }
                .build()
            try {
                client.newCall(request).execute().use { RawResponse(it.code, it.body.string()) }
            } catch (_: IOException) {
                throw ProxyCleanerException("network")
            }
        }

    private class RawResponse(val code: Int, val text: String) {
        fun parse(): JsonElement {
            val element = runCatching { Json.parseToJsonElement(text) }.getOrNull()
            if (code in 200..299) return element ?: JsonObject(emptyMap())
            if (code == 404) throw ProxyCleanerException("server_upgrade_required")
            val message = (element as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            throw ProxyCleanerException(message?.takeIf(String::isNotBlank) ?: "http_$code")
        }
    }

    override fun close() {
        eventScope.cancel()
        // 화면 재생성 중에도 NonCancellable 토큰 갱신/저장을 마치게 한다. 취소하면 기존 잔고 계정을 잃을 수 있다.
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

/** 서버와 같은 DC 계정 식별자: 소문자 아이디의 SHA-256 hex. 아이디 원문은 보내지 않는다. */
internal fun accountHash(userId: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(userId.lowercase().toByteArray())
        .joinToString("") { "%02x".format(it) }

/** 최근 충전 활동의 경과 시간 상한(서버는 7일 안만 준다). 하루 여유를 둔다. */
private const val MAX_ACTIVITY_AGE_SECONDS = 8L * 86_400
