package com.dccleaner.app.proxy

import com.dccleaner.app.model.ProxyCleanerException
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.platform.ProxyCleanerStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseProxyCleanerBackendTest {
    private class MemoryStorage(var token: String? = null, val failRead: Boolean = false) : ProxyCleanerStorage {
        override fun readRefreshToken(): String? = if (failRead) error("keystore unavailable") else token
        override fun writeRefreshToken(token: String) { this.token = token }
        override fun hasConsent() = false
        override fun saveConsent() = Unit
    }

    private val requests = mutableListOf<Request>()

    private fun backend(storage: ProxyCleanerStorage, respond: (Request) -> Pair<Int, String>): SupabaseProxyCleanerBackend {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val (code, body) = respond(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("x").body(body.toResponseBody()).build()
        }.build()
        return SupabaseProxyCleanerBackend(storage, baseUrl = "https://example.test", anonKey = "anon", client = client)
    }

    private fun session(refresh: String) =
        """{"access_token":"access-$refresh","refresh_token":"$refresh","expires_in":3600,"user":{"id":"u1"}}"""

    @Test
    fun readsWithoutAccountNeverCreateOne(): Unit = runBlocking {
        val storage = MemoryStorage()
        val backend = backend(storage) { 200 to session("r1") }

        assertEquals("no_account", assertFailsWith<ProxyCleanerException> { backend.balance() }.code)
        assertEquals("no_account", assertFailsWith<ProxyCleanerException> { backend.ensureUserCode() }.code)
        backend.logEvent("proxy_tab_view")
        Thread.sleep(200)
        assertTrue(requests.isEmpty())
        assertEquals(null, storage.token)
    }

    @Test
    fun purchaseActivityIsReadWithoutAccountAndDropsMalformedRows(): Unit = runBlocking {
        val storage = MemoryStorage()
        val backend = backend(storage) {
            200 to """{"buyers_24h":12,"buyers_7d":"x","recent":[{"seconds_ago":30},{"seconds_ago":-1},""" +
                """{"seconds_ago":9223372036854775807},{"seconds_ago":"x"},{"seconds_ago":7200}]}"""
        }
        val before = System.currentTimeMillis()

        val activity = backend.purchaseActivity()
        val rows = activity.recent

        assertEquals(12, activity.buyers24h)
        assertEquals(0, activity.buyers7d) // 숫자가 아니면 0으로 보고 요약에서 빠진다.
        // 경과 시간이 음수·터무니없는 값·숫자가 아닌 줄은 버린다. 개수는 서버가 주지 않는다.
        assertEquals(2, rows.size)
        val age = (System.currentTimeMillis() - rows[1].creditedAtMillis) / 1_000
        assertTrue(age in 7_200..7_200 + (System.currentTimeMillis() - before) / 1_000 + 1, "age=$age")
        // 계정을 만들거나 토큰을 갱신하지 않고 anon 키로 공개 함수만 부른다.
        val request = requests.single()
        assertEquals("/rest/v1/rpc/credit_purchase_activity", request.url.encodedPath)
        assertEquals("Bearer anon", request.header("Authorization"))
        assertEquals(null, storage.token)
    }

    @Test
    fun chargeRequestSignsUpOnceAndKeepsRefreshToken(): Unit = runBlocking {
        val storage = MemoryStorage()
        val backend = backend(storage) { request ->
            when (request.url.encodedPath) {
                "/auth/v1/signup" -> 200 to session("r1")
                "/rest/v1/rpc/ensure_profile" -> 200 to "\"K7M2-9PXQ\""
                "/rest/v1/rpc/my_deletion_balance" -> 200 to "1499"
                else -> 404 to "{}"
            }
        }

        assertEquals("K7M2-9PXQ", backend.createUserCode())
        assertEquals(1499L, backend.balance())
        assertEquals("K7M2-9PXQ", backend.createUserCode())
        assertEquals("r1", storage.token)
        assertEquals(1, requests.count { it.url.encodedPath == "/auth/v1/signup" })
        val rpc = requests.last()
        assertEquals("anon", rpc.header("apikey"))
        assertEquals("Bearer access-r1", rpc.header("Authorization"))
    }

    @Test
    fun rejectedRefreshTokenIsNeverReplacedBySignup(): Unit = runBlocking {
        val storage = MemoryStorage(token = "old")
        val backend = backend(storage) { request ->
            if (request.url.encodedPath == "/auth/v1/token") 400 to """{"error":"invalid_grant"}""" else 200 to session("new")
        }

        val error = assertFailsWith<ProxyCleanerException> { backend.ensureUserCode() }
        assertEquals("session_invalid", error.code)
        assertEquals("old", storage.token)
        assertTrue(requests.none { it.url.encodedPath == "/auth/v1/signup" })
    }

    @Test
    fun unreadableStorageStopsBeforeAnyRequest(): Unit = runBlocking {
        val backend = backend(MemoryStorage(failRead = true)) { 200 to session("r1") }

        assertEquals("token_read_failed", assertFailsWith<ProxyCleanerException> { backend.ensureUserCode() }.code)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun startJobSendsEnvelopeAndMapsServerErrorCode(): Unit = runBlocking {
        val backend = backend(MemoryStorage(token = "r0")) { request ->
            when (request.url.encodedPath) {
                "/auth/v1/token" -> 200 to session("r1")
                "/rest/v1/rpc/start_deletion_job" -> 400 to """{"message":"insufficient_credits","code":"P0001"}"""
                else -> 404 to "{}"
            }
        }

        val error = assertFailsWith<ProxyCleanerException> {
            backend.startJob("posting", config(), "Tester", "secret")
        }
        assertEquals("insufficient_credits", error.code)
        val body = Buffer().also { requests.last().body!!.writeTo(it) }.readUtf8()
        val args = Json.parseToJsonElement(body).jsonObject
        assertEquals("posting", args["p_delete_type"]?.jsonPrimitive?.content)
        val envelope = Json.parseToJsonElement(args["p_credentials"]!!.jsonPrimitive.content).jsonObject
        assertEquals(setOf("v", "k", "iv", "c"), envelope.keys)
        assertTrue("secret" !in body)
        // 서버와 같은 계정 식별자(소문자 아이디의 SHA-256)만 보내고 아이디 원문은 보내지 않는다.
        val expectedHash = java.security.MessageDigest.getInstance("SHA-256").digest("tester".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, args["p_account_hash"]?.jsonPrimitive?.content)
        assertTrue("Tester" !in body && "\"tester\"" !in body)
    }

    @Test
    fun localOnlyOptionsAreRejectedBeforeContactingServer(): Unit = runBlocking {
        val backend = backend(MemoryStorage(token = "r0")) { 200 to session("r1") }

        for (type in listOf("posting", "comment")) {
            for (pattern in listOf("test", "[")) {
                val options = if (type == "posting") config().copy(postContentFilterEnabled = true, postContentRegex = pattern)
                    else config().copy(commentContentFilterEnabled = true, commentContentRegex = pattern)
                assertEquals("unsupported_regex_filter", assertFailsWith<ProxyCleanerException> {
                    backend.startJob(type, options, "a", "b")
                }.code)
            }
            assertEquals("unsupported_newest_first", assertFailsWith<ProxyCleanerException> {
                backend.startJob(type, config().copy(deleteNewestFirst = true), "a", "b")
            }.code)
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun phasedJobsRejectSecondPhaseRegexAndBadTypeBeforeContactingServer(): Unit = runBlocking {
        val backend = backend(MemoryStorage(token = "r0")) { 200 to session("r1") }
        val next = com.dccleaner.app.model.ProxyFollowUp("posting", emptyList(), false, 5)
        // 두 번째 단계(게시글)의 정규식도 초고속에서는 못 쓴다.
        assertEquals("unsupported_regex_filter", assertFailsWith<ProxyCleanerException> {
            backend.startJob("comment", config().copy(postContentFilterEnabled = true, postContentRegex = "x", followUp = next), "a", "b")
        }.code)
        // 두 단계가 같은 종류면 잘못된 설정이다.
        assertEquals("invalid_config", assertFailsWith<ProxyCleanerException> {
            backend.startJob("posting", config().copy(followUp = next), "a", "b")
        }.code)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun purchasesOnlySendQuantityAndIdAndUseServerQuote(): Unit = runBlocking {
        val backend = backend(MemoryStorage(token = "r0")) { request ->
            if (request.url.encodedPath == "/auth/v1/token") 200 to session("r1") else 200 to
                """{"id":"purchase-id","requested_count":1000,"credited_count":20000,"amount_won":5999,"pricing_version":"v2"}"""
        }
        val purchase = backend.createPurchase(1_000, "request-id")
        assertEquals(20_000L, purchase.creditedCount)
        assertEquals(5_999, purchase.amountWon)
        val request = requests.last()
        assertEquals("/rest/v1/rpc/create_credit_purchase", request.url.encodedPath)
        val args = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals(setOf("p_requested_count", "p_request_id"), args.keys)
        assertEquals("1000", args["p_requested_count"]?.jsonPrimitive?.content)
        assertEquals("request-id", args["p_request_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun missingNewApiNeverFallsBackToWonRpc(): Unit = runBlocking {
        val backend = backend(MemoryStorage(token = "r0")) { request ->
            if (request.url.encodedPath == "/auth/v1/token") 200 to session("r1") else 404 to "{}"
        }
        assertEquals("server_upgrade_required", assertFailsWith<ProxyCleanerException> { backend.balance() }.code)
        assertEquals("server_upgrade_required", assertFailsWith<ProxyCleanerException> { backend.createPurchase(1_000, "id") }.code)
        assertTrue(requests.none { it.url.encodedPath.endsWith("my_credit_balance") })
    }

    @Test
    fun rejectsFractionalBalancesAndMalformedQuotes(): Unit = runBlocking {
        val fractional = backend(MemoryStorage(token = "r0")) { request ->
            if (request.url.encodedPath == "/auth/v1/token") 200 to session("r1") else 200 to "1499.7"
        }
        assertEquals("invalid_server_response", assertFailsWith<ProxyCleanerException> { fractional.balance() }.code)
        listOf(
            """{"id":"p","requested_count":1000,"credited_count":999,"amount_won":5000,"pricing_version":"v1"}""",
            """{"id":"p","requested_count":1001,"credited_count":20000,"amount_won":5000,"pricing_version":"v1"}""",
            """{"id":"p","requested_count":1000,"credited_count":20000.5,"amount_won":5000,"pricing_version":"v1"}""",
            """{"id":"p","requested_count":1000,"credited_count":20000,"amount_won":0,"pricing_version":"v1"}"""
        ).forEach { response ->
            val malformed = backend(MemoryStorage(token = "r0")) { request ->
                if (request.url.encodedPath == "/auth/v1/token") 200 to session("r1") else 200 to response
            }
            assertEquals("invalid_server_response", assertFailsWith<ProxyCleanerException> { malformed.createPurchase(1000, "id") }.code)
        }
    }

    private fun config() = ProxyJobConfig(
        galleries = emptyList(), deleteNewestFirst = false, deleteQuestionPosts = false,
        recommendFilterEnabled = false, minRecommendToKeep = 1, commentFilterEnabled = false, minCommentToKeep = 1,
        viewFilterEnabled = false, minViewToKeep = 1, postContentFilterEnabled = false, postContentRegex = "",
        commentContentFilterEnabled = false, commentContentRegex = "", myPostFilterEnabled = false,
        dcconOnlyFilterEnabled = false, dateFilterEnabled = false, minPostAgeDays = 5,
        excludedPostNumbers = emptyList(), recordGuestbookLog = false
    )
}
