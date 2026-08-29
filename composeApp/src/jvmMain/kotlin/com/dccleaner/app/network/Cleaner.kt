package com.dccleaner.app.network

import com.dccleaner.app.model.*
import com.dccleaner.app.util.SensitiveLogSanitizer
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.twocaptcha.captcha.ReCaptcha
import com.twocaptcha.TwoCaptcha
import java.net.CookieManager
import java.net.CookiePolicy
import okhttp3.JavaNetCookieJar
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

interface CleanerDebugLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

object NoOpCleanerDebugLogger : CleanerDebugLogger {
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}

fun interface CleanerLogSink {
    suspend fun addLog(tag: String, message: String)
}

internal fun gallogListItemText(linkElement: Element?, postType: String): String =
    if (postType == "posting") {
        linkElement?.selectFirst(".galltit strong")?.text().orEmpty()
    } else {
        linkElement?.selectFirst("p.txt")?.text().orEmpty()
    }

class Cleaner private constructor(
    private val logSink: CleanerLogSink? = null,
    private val debugLogger: CleanerDebugLogger = NoOpCleanerDebugLogger,
    private val loginBoxUrl: String,
    private val loginSubmitUrl: String
) : CleanerPort {
    companion object {

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.66 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; SM-G973N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Mobile Safari/537.36"
        private const val DCINSIDE_SITE_KEY =
            "6LcJyr4UAAAAAOy9Q_e9sDWPSHJ_aXus4UnYLfgL"


        private const val MAX_DELAY = 900L // milliseconds
        const val POST_REQUEST_DELAY = 1000L // 글 요청 간 최소 1초 딜레이
        const val PAGE_REQUEST_DELAY = 900L
        private const val MAX_ATTEMPT = 5
        private const val TIMEOUT_SECONDS = 30L


        private const val BASE_URL = "https://www.dcinside.com"
        private const val GALL_BASE_URL = "https://gall.dcinside.com"
        private const val MOBILE_BASE_URL = "https://m.dcinside.com"
        private const val UPLOAD_URL = "https://mupload.dcinside.com/write_new.php"
        private const val GALLOG_URL = "https://gallog.dcinside.com"
        private const val TWOCAPTCHA_CHECK_URL = "https://2captcha.com/in.php"

        private const val SUCCESS_MESSAGE = "success"
        private const val ERROR_MESSAGE = "error"
        private const val FAILED_MESSAGE = "failed"
        private const val BLOCKED_MESSAGE = "blocked"
        private val DC_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }

    constructor(
        logSink: CleanerLogSink? = null,
        debugLogger: CleanerDebugLogger = NoOpCleanerDebugLogger
    ) : this(
        logSink,
        debugLogger,
        "$BASE_URL/main/login_box",
        "https://sign.dcinside.com/login/member_check"
    )

    internal constructor(
        loginBoxUrl: String,
        loginSubmitUrl: String,
        logSink: CleanerLogSink? = null,
        debugLogger: CleanerDebugLogger = NoOpCleanerDebugLogger
    ) : this(logSink, debugLogger, loginBoxUrl, loginSubmitUrl)


    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private val client = createHttpClient(cookieManager)
    private val loginMutex = Mutex()

    private var userId: String = ""
    private val postList = ArrayDeque<String>()
    private val postUrlMap = mutableMapOf<String, String>() // postNo -> URL 매핑
    private val postDcconMap = mutableMapOf<String, Boolean>() // postNo -> DCcon 여부
    private val postTextMap = mutableMapOf<String, String>() // postNo -> 댓글 텍스트
    private val postDateMap = mutableMapOf<String, LocalDate>() // postNo -> 목록 날짜
    private var twocaptchaKey: String = ""


    private fun createHttpClient(cookieManager: CookieManager): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun createLoginHeaders(): Map<String, String> = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to BASE_URL,
        "User-Agent" to USER_AGENT
    )

    private fun createLoginBoxHeaders(): Map<String, String> = mapOf(
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to "$BASE_URL/",
        "Accept" to "text/html, */*; q=0.01",
        "User-Agent" to USER_AGENT
    )

    private fun createDeleteHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "Accept-Language" to "ko-KR,ko;q=0.9",
        "Connection" to "keep-alive",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "Host" to "gallog.dcinside.com",
        "Origin" to GALLOG_URL,
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "Sec-Fetch-User" to "?1",
        "Upgrade-Insecure-Requests" to "1",
        "X-Requested-With" to "XMLHttpRequest",
        "User-Agent" to USER_AGENT
    )

    private fun createMobileAjaxHeaders(
        csrfToken: String,
        referer: String,
        userAgent: String = MOBILE_USER_AGENT
    ): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "ko,en-US;q=0.9,en;q=0.8",
        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "X-Requested-With" to "XMLHttpRequest",
        "X-CSRF-Token" to csrfToken,
        "Referer" to referer,
        "User-Agent" to userAgent
    )

    private fun isCaptchaError(message: String): Boolean {
        return message.contains("captcha") ||
                message.contains("g-recaptcha") ||
                message.contains("recaptcha")
    }

    private fun describeTwoCaptchaResponse(responseText: String): String =
        when (responseText) {
            "ERROR_KEY_DOES_NOT_EXIST", "ERROR_WRONG_USER_KEY" -> responseText
            else -> "응답 길이: ${responseText.length}"
        }

    private fun safeFailureMessage(action: String, statusCode: Int, responseText: String): String {
        val responseBody = responseText.trim().ifEmpty { "<빈 응답>" }
        return "$action 실패 - HTTP $statusCode\n응답: ${SensitiveLogSanitizer.sanitize(responseBody)}"
    }


    override fun getPostListSize(): Int = postList.size

    override fun getPostList(): List<String> = postList.toList()

    override fun getFirstPost(): String? = postList.firstOrNull()

    fun setPostList(posts: List<String>) {
        postList.clear()
        postList.addAll(posts)
    }

    override fun removeFirstPost() {
        if (postList.isNotEmpty()) {
            val postNo = postList.removeFirst()
            postUrlMap.remove(postNo)
            postDcconMap.remove(postNo)
            postTextMap.remove(postNo)
            postDateMap.remove(postNo)
        }
    }

    override fun clearPostData() {
        postList.clear()
        postUrlMap.clear()
        postDcconMap.clear()
        postTextMap.clear()
        postDateMap.clear()
    }

    fun exportCollectedPosts(): List<CollectedPost> = exportCollectedPosts(postList.toList())

    override fun exportCollectedPosts(postNumbers: List<String>): List<CollectedPost> =
        postNumbers.map { postNo ->
            CollectedPost(
                postNo = postNo,
                postUrl = postUrlMap[postNo],
                isDccon = postDcconMap[postNo] ?: false,
                text = postTextMap[postNo] ?: "",
                date = postDateMap[postNo]?.toString()
            )
        }

    override fun importCollectedPosts(posts: List<CollectedPost>) {
        clearPostData()
        posts.forEach { post ->
            postList.addLast(post.postNo)
            post.postUrl?.let { postUrlMap[post.postNo] = it }
            postDcconMap[post.postNo] = post.isDccon
            postTextMap[post.postNo] = post.text
            post.date?.let { date ->
                runCatching { LocalDate.parse(date) }.getOrNull()?.let {
                    postDateMap[post.postNo] = it
                }
            }
        }
    }

    override fun getPostUrl(postNo: String): String? = postUrlMap[postNo]

    override fun isPostDccon(postNo: String): Boolean = postDcconMap[postNo] ?: false

    override fun getPostText(postNo: String): String = postTextMap[postNo] ?: ""

    fun getPostDate(postNo: String): LocalDate? = postDateMap[postNo]

    override fun getUserId(): String = userId

    override fun getPostAgeDays(postNo: String): Long? {
        val postDate = getPostDate(postNo) ?: return null
        return ChronoUnit.DAYS.between(postDate, LocalDate.now(DC_ZONE_ID))
    }

    /**
     * 개별 글 삭제 (실시간 UI 업데이트용)
     */
    suspend fun deleteSinglePost(postType: String): DeletePostResult? =
        withContext(Dispatchers.IO) {
            if (postList.isEmpty()) return@withContext null

            val postNo = postList.first()
            val deleteResult = deletePost(postNo, postType, solveCaptcha = false)

            return@withContext when (deleteResult) {
                is DeleteResult.Success -> {
                    postList.removeFirst()
                    DeletePostResult(true, SUCCESS_MESSAGE, PostDeleteData(postNo, 0.0, false))
                }

                is DeleteResult.Error -> handleDeleteError(deleteResult, postNo, postType)
                is DeleteResult.Failed -> DeletePostResult(false, FAILED_MESSAGE, null)
                is DeleteResult.Blocked -> DeletePostResult(false, BLOCKED_MESSAGE, null)
            }
        }

    private suspend fun handleDeleteError(
        error: DeleteResult.Error,
        postNo: String,
        postType: String
    ): DeletePostResult {
        return if (isCaptchaError(error.message) && twocaptchaKey.isNotEmpty()) {
            val captchaResult = deletePost(postNo, postType, solveCaptcha = true)
            if (captchaResult is DeleteResult.Success) {
                postList.removeAt(0)
                DeletePostResult(true, SUCCESS_MESSAGE, PostDeleteData(postNo, 0.0, true))
            } else {
                DeletePostResult(false, ERROR_MESSAGE, null)
            }
        } else {
            DeletePostResult(false, ERROR_MESSAGE, null)
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, responseToClose, _ -> responseToClose.close() }
                } else {
                    response.close()
                }
            }
        })
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
        userAgent: String = USER_AGENT
    ): Request {
        return Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .apply {
                headers.forEach { (key, value) -> addHeader(key, value) }
                body?.let { post(it) }
            }
            .build()
    }

    private fun serializeForm(inputElements: List<Element>): MutableMap<String, String> {
        return inputElements.fold(mutableMapOf()) { form, element ->
            val name = element.attr("name")
            val value = element.attr("value")
            if (name.isNotEmpty()) {
                form[name] = value
            }
            form
        }
    }


    suspend fun set2CaptchaKey(key: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$TWOCAPTCHA_CHECK_URL?key=$key"
        val request = buildRequest(url)

        try {
            logSink?.addLog("Cleaner", "2Captcha 키 검증 시작")
            execute(request).use { response ->
                val responseText = response.body.string()
                logSink?.addLog("Cleaner", "2Captcha 응답 수신 - ${describeTwoCaptchaResponse(responseText)}")

                if (responseText in listOf("ERROR_KEY_DOES_NOT_EXIST", "ERROR_WRONG_USER_KEY")) {
                    logSink?.addLog("Cleaner", "2Captcha 키 검증 실패 - ${describeTwoCaptchaResponse(responseText)}")
                    return@withContext false
                }

                twocaptchaKey = key
                logSink?.addLog("Cleaner", "2Captcha 키 검증 성공")
                return@withContext true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logSink?.addLog("Cleaner", "2Captcha 키 검증 예외: ${e.message}")
            return@withContext false
        }
    }

    override fun has2CaptchaKey(): Boolean = twocaptchaKey.isNotBlank()

    /** Restores the key captured with a deletion task without another network validation. */
    override fun restore2CaptchaKey(key: String) {
        twocaptchaKey = key
    }


    override fun resetCaptchaState() {
        debugLogger.d("Cleaner", "Captcha state reset - clearing internal states")
    }

    private fun clearAuthenticationState() {
        userId = ""
        cookieManager.cookieStore.removeAll()
    }

    fun clearSession() {
        clearAuthenticationState()
        clearPostData()
        resetCaptchaState()
    }

    suspend fun login(userId: String, userPw: String): Boolean =
        loginMutex.withLock {
            withContext(Dispatchers.IO) {
                val normalizedUserId = userId.lowercase()
                clearSession()
                logSink?.addLog("Cleaner", "로그인 시작: $userId")

                try {

                    val request = buildRequest(loginBoxUrl, createLoginBoxHeaders())
                    val response = execute(request)

                    val html = response.body.string()
                    val doc = Jsoup.parse(html)
                    val inputElements = doc.select("#login_process input[type=hidden]")

                    val loginData = serializeForm(inputElements)
                    loginData["user_id"] = userId
                    loginData["pw"] = userPw
                    loginData["ci_t"] = cookieManager.cookieStore.cookies
                        .find { it.name == "ci_c" }
                        ?.value ?: ""

                    val formBody = FormBody.Builder().apply {
                        loginData.forEach { (k, v) -> add(k, v) }
                    }.build()

                    val loginRequest = buildRequest(
                        loginSubmitUrl,
                        createLoginBoxHeaders(),
                        formBody
                    )
                    execute(loginRequest).close()


                    val checkRequest = buildRequest(loginBoxUrl, createLoginBoxHeaders())
                    val checkResponse = execute(checkRequest)
                    val checkHtml = checkResponse.body.string()
                    val checkDoc = Jsoup.parse(checkHtml)

                    val loginSuccess = checkDoc.select("#login_box .logout").isNotEmpty()
                    if (loginSuccess) {
                        this@Cleaner.userId = normalizedUserId
                    } else {
                        clearAuthenticationState()
                    }
                    logSink?.addLog("Cleaner", "로그인 결과: ${if (loginSuccess) "성공" else "실패"}")
                    return@withContext loginSuccess
                } catch (e: CancellationException) {
                    clearAuthenticationState()
                    throw e
                } catch (e: Exception) {
                    clearAuthenticationState()
                    logSink?.addLog("Cleaner", "로그인 예외: ${e.message}")
                    return@withContext false
                }
            }
        }

    suspend fun getUserInfo(): UserInfo = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(
                "https://gallog.dcinside.com/$userId",
                createLoginHeaders()
            )
            val response = execute(request)
            val html = response.body.string()
            val doc = Jsoup.parse(html)

            val nickname =
                doc.selectFirst("#top_bg > div.galler_info > strong")?.text() ?: ""
            val articleNum = doc.selectFirst(
                "#container > article > div > div.wrap_right > section > section:nth-child(2) > div > header > div > h2 > span"
            )?.text() ?: ""
            val commentNum = doc.selectFirst(
                "#container > article > div > div.wrap_right > section > section:nth-child(3) > div > header > div > h2 > span"
            )?.text() ?: ""

            val removeBracket: (String) -> String = { text ->
                if (text.length > 2 && text.startsWith("(") && text.endsWith(")")) {
                    text.substring(1, text.length - 1)
                } else text
            }

            return@withContext UserInfo(
                nickname = nickname,
                article_num = removeBracket(articleNum),
                comment_num = removeBracket(commentNum)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext UserInfo(
                nickname = "",
                article_num = "0",
                comment_num = "0"
            )
        }
    }

    override suspend fun deletePost(
        postNo: String,
        postType: String,
        solveCaptcha: Boolean
    ): DeleteResult = withContext(Dispatchers.IO) {
        logSink?.addLog("Cleaner", "글 삭제 시작 - postNo: $postNo, type: $postType, captcha: $solveCaptcha")
        try {
            val gallogUrl = "$GALLOG_URL/$userId/$postType"

            val formData = mutableMapOf<String, String>()


            val ci_t = cookieManager.cookieStore.cookies
                .find { it.name == "ci_c" }
                ?.value ?: ""

            formData["ci_t"] = ci_t
            formData["no"] = postNo
            formData["service_code"] = "undefined"


            if (solveCaptcha) {
                logSink?.addLog("Cleaner", "캡챠 해결 시도 중 - postNo: $postNo")
                val captchaResponse = solveCaptchaInternal(gallogUrl)
                debugLogger.d("Cleaner", "Captcha response received: ${captchaResponse?.length ?: 0} chars")
                logSink?.addLog("Cleaner", "캡챠 응답 수신: ${captchaResponse?.length ?: 0}자")

                if (!captchaResponse.isNullOrEmpty()) {
                    formData["g-recaptcha-response"] = captchaResponse
                    logSink?.addLog("Cleaner", "캡챠 토큰 추가됨")
                } else {
                    logSink?.addLog("Cleaner", "캡챠 해결 실패 - 응답 없음")
                }
            }

            debugLogger.d("Cleaner", "Delete form data prepared for post $postNo: ${formData.keys}")

            val formBody = FormBody.Builder().apply {
                formData.forEach { (k, v) -> add(k, v) }
            }.build()

            val deleteHeaders = createDeleteHeaders().toMutableMap()
            deleteHeaders["Referer"] = userId

            debugLogger.d("Cleaner", "Delete headers prepared: ${deleteHeaders.keys}")

            var data: JsonObject? = null
            var requestSuccessful = false

            for (attempt in 0 until MAX_ATTEMPT) {
                debugLogger.d("Cleaner", "Delete attempt ${attempt + 1}/$MAX_ATTEMPT for post $postNo")

                try {
                    val deleteRequest = buildRequest(
                        "$GALLOG_URL/$userId/ajax/log_list_ajax/delete",
                        deleteHeaders,
                        formBody
                    )
                    val deleteResponse = execute(deleteRequest)
                    val responseText = deleteResponse.body.string()

                    debugLogger.d("Cleaner", "Delete response status=${deleteResponse.code}, length=${responseText.length}")

                    if (isCaptchaError(responseText)) {
                        debugLogger.w("Cleaner", "Captcha required detected - skip retry")
                        return@withContext DeleteResult.Error("captcha required")
                    }


                    if (deleteResponse.isSuccessful) {
                        try {
                            data = Json.parseToJsonElement(responseText).jsonObject
                            debugLogger.d("Cleaner", "Delete JSON parsing successful")
                            logSink?.addLog("Cleaner", "삭제 응답 파싱 성공")


                            val result = data["result"]?.jsonPrimitive?.content
                            val msg = data["msg"]?.jsonPrimitive?.content
                            val isAlreadyDeleted = result == "fail" && msg == "글 번호가 올바르지 않습니다."

                            if (!msg.isNullOrBlank() && isCaptchaError(msg)) {
                                debugLogger.w("Cleaner", "Captcha required in response msg - skip retry")
                                logSink?.addLog("Cleaner", "캡챠 필요 감지 - postNo: $postNo")
                                return@withContext DeleteResult.Error("captcha required")
                            }

                            if (result == "success" || isAlreadyDeleted) {
                                debugLogger.d("Cleaner", "Delete result: SUCCESS (or already deleted)")
                                logSink?.addLog("Cleaner", "삭제 성공 - postNo: $postNo ${if (isAlreadyDeleted) "(이미 삭제됨)" else ""}")
                                requestSuccessful = true
                                break
                            } else {
                                logSink?.addLog("Cleaner", "삭제 실패 응답 - result: $result, msg: $msg")
                            }

                        } catch (e: CancellationException) {
                            throw e
                        } catch (jsonException: Exception) {
                            debugLogger.e("Cleaner", "JSON parsing failed (length=${responseText.length})", jsonException)
                            logSink?.addLog("Cleaner", "JSON 파싱 실패 - postNo: $postNo: ${jsonException.message}")

                        }
                    } else {
                        debugLogger.e("Cleaner", "HTTP request failed with status: ${deleteResponse.code}")
                        logSink?.addLog("Cleaner", "HTTP 요청 실패 - status: ${deleteResponse.code}")

                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    debugLogger.e("Cleaner", "Network error on attempt ${attempt + 1}", e)
                    logSink?.addLog("Cleaner", "네트워크 오류 (시도 ${attempt + 1}): ${e.message}")
                }


                if (attempt < MAX_ATTEMPT - 1) {
                    debugLogger.d("Cleaner", "Retrying in 60 seconds...")
                    logSink?.addLog("Cleaner", "60초 후 재시도...")
                    delay(60_000)
                }
            }

            if (!requestSuccessful) {
                debugLogger.e("Cleaner", "All $MAX_ATTEMPT attempts failed for post $postNo")
            }

            data?.let { jsonData ->
                val result = jsonData["result"]?.jsonPrimitive?.content
                val msg = jsonData["msg"]?.jsonPrimitive?.content
                val isAlreadyDeleted = result == "fail" && msg == "글 번호가 올바르지 않습니다."

                return@withContext if (result == "success" || isAlreadyDeleted) {
                    DeleteResult.Success(emptyMap())
                } else {
                    DeleteResult.Error(jsonData.toString())
                }
            }

            return@withContext DeleteResult.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext DeleteResult.Failed
        }
    }

    override suspend fun getPageCount(gno: String, postType: String): Int =
        withContext(Dispatchers.IO) {
            val gallogUrl =
                "$GALLOG_URL/$userId/$postType/index?" +
                        (if (gno.isNotEmpty()) "cno=$gno&" else "") + "p=1"

            try {
                val request = buildRequest(gallogUrl)
                val response = execute(request)
                if (!response.isSuccessful) {
                    return@withContext -1
                }
                val html = response.body.string()
                val doc = Jsoup.parse(html)

                var pages = 1
                val pagingElements = doc.select(".bottom_paging_box > a")

                try {
                    if (pagingElements.isNotEmpty()) {
                        val last = pagingElements.last() ?: return@withContext -1
                        if (last.text() == "끝") {
                            val href = last.attr("href")
                            pages = href.split("&p=").lastOrNull()?.toIntOrNull() ?: 1
                        } else {
                            pages = last.text().toIntOrNull() ?: 1
                        }
                    } else {
                        val emElement = doc.selectFirst(".bottom_paging_box > em")
                        if (emElement?.text() == "1") {
                            pages = 1
                        }
                    }
                } catch (_: Exception) {
                    return@withContext -1
                }

                return@withContext pages
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext -1
            }
        }

    override suspend fun getPostList(
        gno: String,
        postType: String,
        page: Int
    ): PostListResult = withContext(Dispatchers.IO) {
        val gallogUrl =
            "$GALLOG_URL/$userId/$postType/index?" +
                    (if (gno.isNotEmpty()) "cno=$gno&" else "") + "p=$page"

        try {
            val request = buildRequest(gallogUrl)
            val response = execute(request)
            if (!response.isSuccessful) {
                return@withContext if (response.code == 403) {
                    PostListResult.Blocked
                } else {
                    PostListResult.Failed
                }
            }
            val html = response.body.string()
            val doc = Jsoup.parse(html)

            if (doc.selectFirst("body") == null) {
                return@withContext PostListResult.Blocked
            }

            val requestedGalleryMatched = gallogResponseMatchesRequestedGallery(html, gno)
            val postListElements = doc.select(".cont_listbox > li")
            if (postListElements.isEmpty()) {
                return@withContext PostListResult.Success(
                    emptyList(),
                    requestedGalleryMatched = requestedGalleryMatched
                )
            }

            val posts = postListElements.reversed().mapNotNull { element ->
                val postNoAttr = element.attr("data-no")
                val linkElement = element.selectFirst(".gall_linkbox .link")
                val postUrl = linkElement?.attr("href")

                if (postNoAttr.isNotEmpty()) {
                    if (postUrl != null && postUrl.isNotEmpty()) {
                        postUrlMap[postNoAttr] = postUrl
                    }
                    postDcconMap[postNoAttr] = linkElement?.selectFirst(".comment_dccon") != null
                    postTextMap[postNoAttr] = gallogListItemText(linkElement, postType)
                    parseGallogDate(linkElement?.selectFirst(".date")?.text())?.let { date ->
                        postDateMap[postNoAttr] = date
                    }
                    postNoAttr
                } else {
                    null
                }
            }

            return@withContext PostListResult.Success(
                posts,
                requestedGalleryMatched = requestedGalleryMatched
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext PostListResult.Failed
        }
    }

    suspend fun aggregatePosts(
        gno: String,
        postType: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<AggregateResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AggregateResult>()
        val pages = getPageCount(gno, postType)
        postList.clear()
        postUrlMap.clear()
        postDcconMap.clear()
        postTextMap.clear()
        postDateMap.clear()

        if (pages <= 0) {
            results.add(AggregateResult(false, "network", null))
            return@withContext results
        }

        for (idx in pages downTo 1) {
            val currentPage = pages - idx + 1
            onProgress?.invoke(currentPage, pages)

            val startTime = System.currentTimeMillis()
            delay(MAX_DELAY)
            val postListResult = getPostList(gno, postType, idx)
            val delayTime = System.currentTimeMillis() - startTime
            val delaySec = String.format("%.1f", delayTime / 1000.0).toDouble()

            when (postListResult) {
                is PostListResult.Blocked -> {
                    results.add(AggregateResult(false, "ipblocked", null))
                    break
                }

                is PostListResult.Failed -> {
                    results.add(AggregateResult(false, "network", null))
                    break
                }

                is PostListResult.Success -> {
                    postList.addAll(postListResult.posts)
                    results.add(
                        AggregateResult(
                            true,
                            "success",
                            AggregateData(idx, delaySec)
                        )
                    )
                }
            }
        }

        return@withContext results
    }

    private fun parseGallogDate(dateText: String?): LocalDate? {
        val text = dateText?.trim().orEmpty()
        if (text.isEmpty()) return null
        val today = LocalDate.now(DC_ZONE_ID)

        return when {
            Regex("""\d{4}\.\d{2}\.\d{2}""").matches(text) -> {
                runCatching {
                    LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                }.getOrNull()
            }

            Regex("""\d{2}\.\d{2}\.\d{2}""").matches(text) -> {
                runCatching {
                    LocalDate.parse(text, DateTimeFormatter.ofPattern("yy.MM.dd"))
                }.getOrNull()
            }

            Regex("""\d{2}\.\d{2}""").matches(text) -> {
                val month = text.substringBefore(".").toIntOrNull()
                val day = text.substringAfter(".").toIntOrNull()
                if (month == null || day == null) {
                    null
                } else {
                    runCatching {
                        val date = LocalDate.of(today.year, month, day)
                        if (date.isAfter(today)) date.minusYears(1) else date
                    }.getOrNull()
                }
            }

            Regex("""\d{2}:\d{2}""").matches(text) -> today
            else -> try {
                LocalDate.parse(text)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    override suspend fun getPostDetails(postUrl: String): PostDetails? = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(postUrl)
            execute(request).use { response ->
                if (response.code == 404) {
                    debugLogger.d("Cleaner", "Post details returned 404, treating counts as zero - URL: $postUrl")
                    return@withContext PostDetails(recommendCount = 0, commentCount = 0)
                }

                if (!response.isSuccessful) {
                    debugLogger.w("Cleaner", "Post details request failed - URL: $postUrl, code: ${response.code}")
                    return@withContext null
                }

                val html = response.body.string()
                if (html.isBlank()) return@withContext null
                val doc = Jsoup.parse(html)
                val recommendElement = doc.selectFirst(".fr .gall_reply_num")
                val commentElement = doc.selectFirst(".fr .gall_comment a")

                if (recommendElement == null && commentElement == null) {
                    debugLogger.w("Cleaner", "Post details DOM not found - URL: $postUrl")
                    return@withContext null
                }

                val recommendCount = recommendElement?.text()
                    ?.replace("[^0-9]".toRegex(), "")
                    ?.toIntOrNull() ?: if (recommendElement != null) 0 else null
                val commentCount = commentElement?.text()
                    ?.replace("[^0-9]".toRegex(), "")
                    ?.toIntOrNull() ?: if (commentElement != null) 0 else null

                debugLogger.d("Cleaner", "Post details - URL: $postUrl, 추천: $recommendCount, 댓글: $commentCount")
                return@withContext PostDetails(recommendCount, commentCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "Failed to get post details", e)
            return@withContext null
        }
    }

    override suspend fun getPostWriterUid(postUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(postUrl)
            val response = execute(request)
            if (response.code == 404) {
                debugLogger.d("Cleaner", "Post returned 404, treating as my post (URL: $postUrl)")
                return@withContext userId
            }
            val html = response.body.string()
            val doc = Jsoup.parse(html)
            val writerUid = doc.selectFirst(".gall_writer[data-uid]")?.attr("data-uid") ?: ""
            debugLogger.d("Cleaner", "Post writer UID: $writerUid (URL: $postUrl)")
            return@withContext writerUid.ifEmpty { null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "Failed to get post writer uid", e)
            return@withContext null
        }
    }

    suspend fun getGallList(postType: String): GallListResult =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    buildRequest("$GALLOG_URL/$userId/$postType")
                val response = execute(request)
                val html = response.body.string()
                debugLogger.d("Cleaner", "getGallList response code: ${response.code}")
                val doc = Jsoup.parse(html)

                if (doc.selectFirst("body") == null) {
                    return@withContext GallListResult.Blocked
                }

                val gallListElements =
                    doc.select("div.option_sort.gallog > div > ul > li")
                if (gallListElements.size <= 1) {
                    return@withContext GallListResult.Success(emptyMap())
                }

                val gallList = mutableMapOf<String, String>()
                gallListElements.drop(1).forEach { element ->
                    val gno = element.attr("data-value")
                    val gname = element.text()
                    if (gno.isNotEmpty()) {
                        gallList[gno] = gname
                    }
                }

                return@withContext GallListResult.Success(gallList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLogger.e("Cleaner", "getGallList Exception", e)
                return@withContext GallListResult.Success(emptyMap())
            }
        }

    suspend fun solveCaptchaInternal(pageUrl: String): String? =
        withContext(Dispatchers.IO) {
            logSink?.addLog("Cleaner", "2Captcha 해결 시작 - URL: $pageUrl")
            try {
                val solver = TwoCaptcha(twocaptchaKey)
                solver.setDefaultTimeout(180)
                solver.setPollingInterval(10)

                val captcha = ReCaptcha()
                captcha.setSiteKey(DCINSIDE_SITE_KEY)
                captcha.setUrl(pageUrl)
                captcha.setInvisible(true);
                captcha.setAction("verify");

                logSink?.addLog("Cleaner", "2Captcha 요청 전송 중...")
                solver.solve(captcha)

                debugLogger.d("Cleaner", "2Captcha solved token length=${captcha.code?.length ?: 0}")
                logSink?.addLog("Cleaner", "2Captcha 해결 완료 - 토큰 길이: ${captcha.code?.length ?: 0}")

                return@withContext captcha.code
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logSink?.addLog("Cleaner", "2Captcha 해결 실패: ${e.message}")
                debugLogger.e("Cleaner", "2Captcha solve failed", e)
                return@withContext null
            }
        }

    override suspend fun writePost(
        galleryId: String,
        subject: String,
        content: String
    ): WriteResult = withContext(Dispatchers.IO) {
        logSink?.addLog("Cleaner", "글 작성 시작 - 갤러리: $galleryId, 제목: $subject")
        try {
            val writeUrl = "$MOBILE_BASE_URL/write/$galleryId"
            val boardReferer = "$MOBILE_BASE_URL/board/$galleryId"

            debugLogger.d("Cleaner", "Write post request prepared for gallery=$galleryId")

            val writePageRequest = buildRequest(
                url = writeUrl,
                headers = mapOf(
                    "Referer" to boardReferer,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                ),
                userAgent = MOBILE_USER_AGENT
            )

            val writePageResponse = execute(writePageRequest)
            val html = writePageResponse.body.string()
            val doc = Jsoup.parse(html)

            val csrfToken = doc.select("meta[name=csrf-token]").attr("content")
            if (csrfToken.isEmpty()) {
                return@withContext WriteResult.Failed("CSRF 토큰을 찾을 수 없습니다")
            }

            val fields = mutableMapOf<String, String>()
            doc.select("#writeForm input[name], #writeForm textarea[name], #writeForm select[name]")
                .forEach { element ->
                    val name = element.attr("name")
                    if (name.isNotEmpty()) {
                        fields[name] = element.attr("value")
                    }
                }

            fields["id"] = galleryId
            fields["route_id"] = galleryId
            fields["subject"] = subject
            fields["memo"] = content

            val honeyFieldName = fields.keys.find { it.startsWith("honey_") }
            if (honeyFieldName != null) {
                fields["GEY3JWF"] = honeyFieldName
            }

            val ajaxHeaders = createMobileAjaxHeaders(csrfToken, writeUrl)

            val accessFormData = FormBody.Builder()
                .add("token_verify", "dc_check2")
                .build()

            val accessRequest = buildRequest(
                url = "$MOBILE_BASE_URL/ajax/access",
                headers = ajaxHeaders,
                body = accessFormData,
                userAgent = MOBILE_USER_AGENT
            )

            val accessResponse = execute(accessRequest)
            val accessJson = accessResponse.body.string()

            try {
                val accessData = Json.parseToJsonElement(accessJson).jsonObject
                val blockKey = accessData["Block_key"]?.toString()?.trim('\"')
                    ?: accessData["block_key"]?.toString()?.trim('\"')
                if (blockKey != null) {
                    fields["Block_key"] = blockKey
                }
            } catch (e: Exception) {
                debugLogger.w("Cleaner", "Failed to parse access response", e)
            }

            val filterFormData = FormBody.Builder()
                .add("subject", subject)
                .add("memo", content)
                .add("id", galleryId)
                .add("mode", "write")
                .add("is_mini", "0")
                .add("is_person", "0")
                .build()

            val filterRequest = buildRequest(
                url = "$MOBILE_BASE_URL/ajax/w_filter",
                headers = ajaxHeaders,
                body = filterFormData,
                userAgent = MOBILE_USER_AGENT
            )

            val filterResponse = execute(filterRequest)
            val filterJson = filterResponse.body.string()

            try {
                val filterData = Json.parseToJsonElement(filterJson).jsonObject
                val blockKey = filterData["Block_key"]?.toString()?.trim('\"')
                    ?: filterData["block_key"]?.toString()?.trim('\"')
                if (blockKey != null) {
                    fields["Block_key"] = blockKey
                }
            } catch (e: Exception) {
                debugLogger.w("Cleaner", "Failed to parse filter response", e)
            }

            val dcblockCookie = cookieManager.cookieStore.cookies
                .find { cookie ->
                    cookie.name.length >= 30 && cookie.name.all { it.isLetterOrDigit() }
                }
            if (dcblockCookie != null) {
                fields["dcblock"] = dcblockCookie.value
                if (!fields.containsKey("Block_key")) {
                    fields["Block_key"] = dcblockCookie.value
                }
            }

            val finalFormData = FormBody.Builder()
            fields.forEach { (key, value) ->
                if (key != "files") {
                    finalFormData.add(key, value)
                }
            }
            val formBody = finalFormData.build()

            val submitRequest = buildRequest(
                url = UPLOAD_URL,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Referer" to writeUrl,
                    "X-CSRF-Token" to csrfToken
                ),
                body = formBody,
                userAgent = MOBILE_USER_AGENT
            )

            val response = execute(submitRequest)
            val responseText = response.body.string()

            debugLogger.d("Cleaner", "Write post response status=${response.code}, length=${responseText.length}")
            logSink?.addLog("Cleaner", "글 작성 응답 - status: ${response.code}, 길이: ${responseText.length}")

            val success = response.isSuccessful &&
                    (responseText.contains("등록되었습니다") || responseText.contains("refresh") || responseText.contains(
                        "url="
                    ))

            if (success) {
                logSink?.addLog("Cleaner", "글 작성 성공 - 갤러리: $galleryId")
                return@withContext WriteResult.Success
            } else {
                val failureMessage = safeFailureMessage("글 작성", response.code, responseText)
                logSink?.addLog("Cleaner", "갤러리: $galleryId, $failureMessage")
                return@withContext WriteResult.Failed(failureMessage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "Write post error", e)
            logSink?.addLog("Cleaner", "글 작성 예외 - 갤러리: $galleryId: ${e.message}")
            return@withContext WriteResult.Failed(e.message ?: "Unknown error")
        }
    }

    override suspend fun writeComment(
        galleryId: String,
        postNo: String,
        content: String
    ): WriteResult = withContext(Dispatchers.IO) {
        logSink?.addLog("Cleaner", "댓글 작성 시작 - 갤러리: $galleryId, 글번호: $postNo")
        try {
            val postUrl = "$MOBILE_BASE_URL/board/$galleryId/$postNo"
            val boardReferer = "$MOBILE_BASE_URL/board/$galleryId"

            val viewRequest = buildRequest(
                url = postUrl,
                headers = mapOf(
                    "Referer" to boardReferer,
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                ),
                userAgent = MOBILE_USER_AGENT
            )

            val viewResponse = execute(viewRequest)
            val html = viewResponse.body.string()
            val doc = Jsoup.parse(html)

            val csrfToken = doc.select("meta[name=csrf-token]").attr("content")
            if (csrfToken.isEmpty()) {
                return@withContext WriteResult.Failed("CSRF 토큰을 찾을 수 없습니다")
            }

            val userId = doc.select("#user_id").attr("value")
            val boardId = doc.select("#board_id").attr("value")
            val repleId = doc.select("#reple_id").attr("value")
            val bestChk = doc.select("#best_chk").attr("value")
            val commentNo = doc.select("#comment_no").attr("value")
            val cpage = doc.select("#cpage").attr("value").ifEmpty { "1" }
            val hideRobotName = doc.select(".comment-write .hide-robot").attr("name")
            val subject = doc.select(".gallview-tit-box .tit").first()?.text() ?: ""
            val gallNickname = doc.select("#gall_nickname").attr("value")
            val useGallNickname = doc.select("#use_gall_nickname").attr("value")

            val ajaxHeaders = createMobileAjaxHeaders(csrfToken, postUrl)

            val accessFormData = FormBody.Builder()
                .add("token_verify", "com_submit")
                .build()

            val accessRequest = buildRequest(
                url = "$MOBILE_BASE_URL/ajax/access",
                headers = ajaxHeaders,
                body = accessFormData,
                userAgent = MOBILE_USER_AGENT
            )

            val accessResponse = execute(accessRequest)
            val accessJson = accessResponse.body.string()

            var conKey = ""
            try {
                val accessData = Json.parseToJsonElement(accessJson).jsonObject
                conKey = accessData["Block_key"]?.toString()?.trim('\"')
                    ?: accessData["block_key"]?.toString()?.trim('\"')
                            ?: accessData["con_key"]?.toString()?.trim('\"')
                            ?: accessData["Con_key"]?.toString()?.trim('\"') ?: ""
            } catch (e: Exception) {
                debugLogger.w("Cleaner", "Failed to parse access response", e)
            }

            if (conKey.isEmpty()) {
                return@withContext WriteResult.Failed("댓글 작성용 키(con_key)를 얻지 못했습니다")
            }

            try {
                val cmtwChkCookie = "cmtw_chk=$conKey; Max-Age=180; Path=/"
                val cookieUrl = java.net.HttpCookie.parse(cmtwChkCookie).firstOrNull()
                if (cookieUrl != null) {
                    cookieUrl.domain = "m.dcinside.com"
                    cookieUrl.path = "/"
                    cookieUrl.maxAge = 180
                    val uri = java.net.URI(MOBILE_BASE_URL)
                    cookieManager.cookieStore.add(uri, cookieUrl)
                    debugLogger.d("Cleaner", "Set cmtw_chk cookie with con_key length=${conKey.length}")
                }
            } catch (e: Exception) {
                debugLogger.w("Cleaner", "Failed to set cmtw_chk cookie", e)
            }

            val commentFormData = FormBody.Builder()
                .add("comment_memo", content)
                .add("mode", "com_write")
                .add("comment_no", commentNo)
                .add("comment_nick", "")
                .add("comment_pw", "")
                .add("id", galleryId)
                .add("no", postNo)
                .add("best_chk", bestChk)
                .add("board_id", boardId)
                .add("reple_id", repleId)
                .add("cpage", cpage)

            if (subject.isNotEmpty()) {
                commentFormData.add("subject", subject)
            }
            commentFormData.add("con_key", conKey)

            val robotField = hideRobotName.ifEmpty { "bbcdd3" }
            commentFormData.add(robotField, "1")

            if (useGallNickname.isNotEmpty()) {
                commentFormData.add("use_gall_nickname", useGallNickname)
            }
            if (gallNickname.isNotEmpty() && useGallNickname == "1") {
                commentFormData.add("gall_nickname", gallNickname)
            }

            val finalCommentBody = commentFormData.build()

            val commentRequest = buildRequest(
                url = "$MOBILE_BASE_URL/ajax/comment-write",
                headers = ajaxHeaders,
                body = finalCommentBody,
                userAgent = MOBILE_USER_AGENT
            )

            val response = execute(commentRequest)
            val responseText = response.body.string()

            debugLogger.d("Cleaner", "Write comment response status=${response.code}, length=${responseText.length}")
            logSink?.addLog("Cleaner", "댓글 작성 응답 - status: ${response.code}, 길이: ${responseText.length}")

            var success = false
            try {
                val responseData = Json.parseToJsonElement(responseText).jsonObject
                val result = responseData["result"]?.toString()?.trim('\"')
                success = result == "1" || result == "true"
            } catch (e: Exception) {
                success = response.isSuccessful
            }

            if (success) {
                logSink?.addLog("Cleaner", "댓글 작성 성공 - 갤러리: $galleryId, 글번호: $postNo")
                return@withContext WriteResult.Success
            } else {
                val failureMessage = safeFailureMessage("댓글 작성", response.code, responseText)
                logSink?.addLog("Cleaner", "갤러리: $galleryId, 글번호: $postNo, $failureMessage")
                return@withContext WriteResult.Failed(failureMessage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "Write comment error", e)
            logSink?.addLog("Cleaner", "댓글 작성 예외 - 갤러리: $galleryId: ${e.message}")
            return@withContext WriteResult.Failed(e.message ?: "Unknown error")
        }
    }

    suspend fun writeGuestbook(targetUserId: String, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val phpSessId = cookieManager.cookieStore.cookies
                    .find { it.name == "PHPSESSID" }
                    ?.value ?: ""
                val ciT = cookieManager.cookieStore.cookies
                    .find { it.name == "ci_c" }
                    ?.value.orEmpty()

                val url = "$GALLOG_URL/$targetUserId/ajax/guestbook_ajax/write"

                val formBodyBuilder = FormBody.Builder()
                if (ciT.isNotBlank()) {
                    formBodyBuilder.add("ci_t", ciT)
                }
                val formBody = formBodyBuilder
                    .add("memo", content)
                    .add("is_secret", "0")
                    .build()

                val headers = mutableMapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "Origin" to GALLOG_URL,
                    "Referer" to "$GALLOG_URL/$targetUserId/guestbook",
                    "X-Requested-With" to "XMLHttpRequest"
                )
                if (phpSessId.isNotEmpty()) {
                    headers["Cookie"] = "PHPSESSID=$phpSessId;"
                }

                val request = buildRequest(url, headers, formBody)
                val response = execute(request)
                val responseText = response.body.string()
                debugLogger.d("Cleaner", "writeGuestbook[$targetUserId] status=${response.code}, length=${responseText.length}")
                return@withContext response.isSuccessful
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                debugLogger.e("Cleaner", "writeGuestbook[$targetUserId] error: ${e.message}")
                return@withContext false
            }
        }

    override suspend fun recordCleanerRunGuestbookLog(
        deletedPosts: Int,
        deletedComments: Int,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetUserId = userId
        if (targetUserId.isBlank()) return@withContext false
        if (deletedPosts <= 0 && deletedComments <= 0) return@withContext true

        onProgress("🔎 기존 방명록 가동 기록 확인 중")
        val existingEntries = loadCleanerRunLogEntries(targetUserId) ?: run {
            onProgress("⚠️ 기존 방명록 가동 기록 조회 실패")
            return@withContext false
        }
        onProgress("📌 기존 가동 기록 ${existingEntries.size}개 파악")
        val existingLog = existingEntries.firstOrNull()
            ?.let { parseCleanerRunLog(it.memo) }
            .orEmpty()
        val rows = existingLog.toMutableMap()
        val currentMonth = YearMonth.now(DC_ZONE_ID)
        val current = rows[currentMonth] ?: CleanerRunLogCount()
        rows[currentMonth] = current.copy(
            postCount = current.postCount + deletedPosts,
            commentCount = current.commentCount + deletedComments
        )

        val newLogText = formatCleanerRunLog(rows.toSortedMap())
        onProgress("📤 방명록 가동 기록 작성 요청 전송 중")
        if (!writeGuestbook(targetUserId, newLogText)) {
            onProgress("⚠️ 방명록 가동 기록 작성 요청 실패")
            return@withContext false
        }
        onProgress("✅ 방명록 가동 기록 작성 요청 전송 완료")

        onProgress("🔎 방명록 등록 확인 중")
        val newLogSignature = normalizeCleanerRunLogText(newLogText)
        val existingHeadnums = existingEntries.map { it.headnum }.toSet()
        var logEntries: List<CleanerRunLogEntry> = emptyList()
        var newEntry: CleanerRunLogEntry? = null
        for (attempt in 0 until 5) {
            if (attempt > 0) delay(1_500L)
            logEntries = loadCleanerRunLogEntries(targetUserId) ?: continue
            newEntry = logEntries.firstOrNull { it.headnum !in existingHeadnums }
                ?: logEntries.firstOrNull {
                    normalizeCleanerRunLogText(it.memo) == newLogSignature
                }
            if (newEntry != null) break
        }
        val confirmedNewEntry = newEntry ?: run {
            debugLogger.w("Cleaner", "Cleaner run guestbook log was written, but confirmation lookup missed it")
            onProgress("⚠️ 방명록 등록 확인을 못 했지만 작성 요청은 완료됨")
            return@withContext true
        }
        onProgress("✅ 방명록 등록 확인 완료")

        val oldEntries = logEntries
            .filter { it.headnum in existingHeadnums && it.headnum != confirmedNewEntry.headnum }
        if (oldEntries.isEmpty()) {
            onProgress("ℹ️ 삭제할 이전 가동 기록 없음")
            return@withContext true
        }

        onProgress("🧹 이전 가동 기록 ${oldEntries.size}개 삭제 중")
        val deletedCount = oldEntries.count { deleteGuestbook(targetUserId, it.headnum) }
        if (deletedCount == oldEntries.size) {
            onProgress("✅ 이전 가동 기록 ${deletedCount}개 삭제 완료")
        } else {
            onProgress("⚠️ 이전 가동 기록 삭제 일부 실패 ($deletedCount/${oldEntries.size})")
        }
        true
    }

    private suspend fun loadCleanerRunLogEntries(targetUserId: String): List<CleanerRunLogEntry>? {
        val entries = mutableListOf<CleanerRunLogEntry>()
        for (page in 1..5) {
            val pageUrl = if (page == 1) {
                "$GALLOG_URL/$targetUserId/guestbook"
            } else {
                "$GALLOG_URL/$targetUserId/guestbook?p=$page"
            }
            val request = buildRequest(
                pageUrl,
                headers = mapOf("Referer" to "$GALLOG_URL/$targetUserId")
            )
            val html = runCatching {
                execute(request).use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body.string()
                }
            }.getOrNull()
            if (html == null) {
                if (page == 1) return null else break
            }
            if (html.isBlank()) {
                if (page == 1) return null else break
            }

            val doc = Jsoup.parse(html)
            doc.select("#gb_comments li").mapNotNullTo(entries) { item ->
                val writerHref = item.selectFirst(".user_data_list a[href]")?.attr("href").orEmpty()
                val writerId = writerHref.trim('/').substringBefore('/').substringBefore('?')
                val memo = item.selectFirst(".txt.memo") ?: item.selectFirst(".memo")
                val memoText = memo?.let(::memoTextWithLineBreaks).orEmpty()
                val headnum = item.attr("data-headnum")
                val isMyLog = writerId == targetUserId &&
                        (memoText.contains("[디시클리너 모바일 가동 내역]") ||
                                memoText.contains("[디시클리너 가동내역]") ||
                                memoText.contains("디시클리너 가동로그"))
                if (isMyLog && headnum.isNotBlank()) {
                    CleanerRunLogEntry(headnum = headnum, memo = memoText)
                } else {
                    null
                }
            }
        }
        return entries
    }

    private suspend fun deleteGuestbook(targetUserId: String, headnum: String): Boolean =
        withContext(Dispatchers.IO) {
            val phpSessId = cookieManager.cookieStore.cookies
                .find { it.name == "PHPSESSID" }
                ?.value.orEmpty()
            val ciT = cookieManager.cookieStore.cookies
                .find { it.name == "ci_c" }
                ?.value.orEmpty()
            if (ciT.isBlank() || headnum.isBlank()) return@withContext false

            val formBody = FormBody.Builder()
                .add("ci_t", ciT)
                .add("headnum", headnum)
                .build()
            val headers = mutableMapOf(
                "Accept" to "*/*",
                "Origin" to GALLOG_URL,
                "Referer" to "$GALLOG_URL/$targetUserId/guestbook",
                "X-Requested-With" to "XMLHttpRequest"
            )
            if (phpSessId.isNotBlank()) {
                headers["Cookie"] = "PHPSESSID=$phpSessId;"
            }
            val request = buildRequest(
                url = "$GALLOG_URL/$targetUserId/ajax/guestbook_ajax/delete",
                headers = headers,
                body = formBody
            )
            runCatching {
                execute(request).use { response ->
                    val responseText = response.body.string()
                    debugLogger.d("Cleaner", "deleteGuestbook[$targetUserId:$headnum] status=${response.code}, length=${responseText.length}")
                    response.isSuccessful
                }
            }.getOrDefault(false)
    }

    private fun memoTextWithLineBreaks(element: Element): String {
        val html = element.html()
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
        return html.lines()
            .joinToString("\n") { Jsoup.parseBodyFragment(it).text() }
            .trim()
    }

    private fun normalizeCleanerRunLogText(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()

    private fun parseCleanerRunLog(text: String): Map<YearMonth, CleanerRunLogCount> {
        val lineRegex = Regex("""(20\d{2})\.(\d{2})\s*[-–]\s*글\s*([\d,]+)개,\s*댓글\s*([\d,]+)개""")
        return lineRegex.findAll(text).mapNotNull { match ->
            val (year, month, postCount, commentCount) = match.destructured
            val yearMonth = runCatching {
                YearMonth.of(year.toInt(), month.toInt())
            }.getOrNull() ?: return@mapNotNull null
            yearMonth to CleanerRunLogCount(
                postCount = postCount.replace(",", "").toIntOrNull() ?: 0,
                commentCount = commentCount.replace(",", "").toIntOrNull() ?: 0
            )
        }.toMap()
    }

    private fun formatCleanerRunLog(rows: Map<YearMonth, CleanerRunLogCount>): String {
        val totalPosts = rows.values.sumOf { it.postCount }
        val totalComments = rows.values.sumOf { it.commentCount }
        return buildString {
            appendLine("[디시클리너 모바일 가동 내역]")
            rows.forEach { (month, count) ->
                appendLine(
                    "%04d.%02d - 글 %s개, 댓글 %s개".format(
                        Locale.KOREA,
                        month.year,
                        month.monthValue,
                        formatCount(count.postCount),
                        formatCount(count.commentCount)
                    )
                )
            }
            appendLine()
            append(
                "총합 – 글 %s개, 댓글 %s개".format(
                    Locale.KOREA,
                    formatCount(totalPosts),
                    formatCount(totalComments)
                )
            )
        }
    }

    private fun formatCount(count: Int): String = "%,d".format(Locale.KOREA, count)

    override suspend fun getDaewangconProgress(): DaewangconProgress? = withContext(Dispatchers.IO) {
        try {
            val phpSessId = cookieManager.cookieStore.cookies
                .find { it.name == "PHPSESSID" }
                ?.value.orEmpty()
            val ciT = cookieManager.cookieStore.cookies
                .find { it.name == "ci_c" }
                ?.value.orEmpty()

            if (phpSessId.isBlank() || ciT.isBlank()) {
                logSink?.addLog("Cleaner", "대왕콘 진행도 확인 실패 - 로그인 세션 쿠키 없음")
                return@withContext null
            }

            val formBody = FormBody.Builder()
                .add("ci_t", ciT)
                .add("target", "icon")
                .build()
            val headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Cookie" to "ci_c=$ciT; PHPSESSID=$phpSessId;",
                "Origin" to GALL_BASE_URL,
                "Referer" to "$GALL_BASE_URL/",
                "X-Requested-With" to "XMLHttpRequest"
            )
            val request = buildRequest(
                url = "$GALL_BASE_URL/dccon/lists",
                headers = headers,
                body = formBody
            )

            execute(request).use { response ->
                val responseText = response.body.string()
                debugLogger.d("Cleaner", "getDaewangconProgress status=${response.code}, length=${responseText.length}")
                if (!response.isSuccessful) {
                    logSink?.addLog("Cleaner", "대왕콘 진행도 확인 실패 - HTTP ${response.code}")
                    return@withContext null
                }

                val root = Json.parseToJsonElement(responseText).jsonObject
                val bigcon = root["bigcon"]?.jsonObject ?: return@withContext null
                val config = bigcon["config"]?.jsonObject ?: return@withContext null

                val postCount = bigcon["article"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext null
                val commentCount = bigcon["comment"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext null
                val requiredPostCount = config["article"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext null
                val requiredCommentCount = config["comment"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext null
                val durationHours = config["hours"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val status = bigcon["status"]?.jsonPrimitive?.contentOrNull.orEmpty()

                if (
                    postCount < 0 || commentCount < 0 ||
                    requiredPostCount <= 0 || requiredCommentCount <= 0
                ) {
                    return@withContext null
                }

                logSink?.addLog(
                    "Cleaner",
                    "대왕콘 진행도 확인 - 글 $postCount/$requiredPostCount, 댓글 $commentCount/$requiredCommentCount"
                )
                DaewangconProgress(
                    postCount = postCount,
                    commentCount = commentCount,
                    requiredPostCount = requiredPostCount,
                    requiredCommentCount = requiredCommentCount,
                    durationHours = durationHours,
                    status = status
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "getDaewangconProgress error", e)
            logSink?.addLog("Cleaner", "대왕콘 진행도 확인 예외: ${e.message}")
            null
        }
    }

    override suspend fun setBigcon(): WriteResult = withContext(Dispatchers.IO) {
        try {
            val phpSessId = cookieManager.cookieStore.cookies
                .find { it.name == "PHPSESSID" }
                ?.value.orEmpty()
            val ciT = cookieManager.cookieStore.cookies
                .find { it.name == "ci_c" }
                ?.value.orEmpty()

            if (phpSessId.isBlank()) {
                return@withContext WriteResult.Failed("PHPSESSID 쿠키를 찾을 수 없습니다")
            }
            if (ciT.isBlank()) {
                return@withContext WriteResult.Failed("ci_t 값을 찾을 수 없습니다")
            }

            val formBody = FormBody.Builder()
                .add("ci_t", ciT)
                .build()
            val headers = mapOf(
                "Accept" to "*/*",
                "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "Cookie" to "PHPSESSID=$phpSessId;",
                "Origin" to GALL_BASE_URL,
                "Referer" to "$GALL_BASE_URL/board/view/?id=kingcon&no=1400",
                "X-Requested-With" to "XMLHttpRequest"
            )
            val request = buildRequest(
                url = "$GALL_BASE_URL/dccon/set_bigcon",
                headers = headers,
                body = formBody
            )
            val response = execute(request)
            val responseText = response.body.string()

            debugLogger.d("Cleaner", "setBigcon status=${response.code}, length=${responseText.length}")
            logSink?.addLog("Cleaner", "대왕콘 설정 응답 - status: ${response.code}, 길이: ${responseText.length}")

            if (response.isSuccessful) {
                WriteResult.Success
            } else {
                val failureMessage = safeFailureMessage("대왕콘 설정", response.code, responseText)
                logSink?.addLog("Cleaner", failureMessage)
                WriteResult.Failed(failureMessage)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            debugLogger.e("Cleaner", "setBigcon error", e)
            logSink?.addLog("Cleaner", "대왕콘 설정 예외: ${e.message}")
            WriteResult.Failed(e.message ?: "Unknown error")
        }
    }
}

internal fun gallogResponseMatchesRequestedGallery(html: String, requestedGalleryNo: String): Boolean {
    if (requestedGalleryNo.isEmpty()) return true
    val selectedGalleryNo = Regex(
        """select_ul\([^;]*['"]filter['"]\s*,\s*['"]([^'"]*)['"]\s*\)"""
    ).find(html)?.groupValues?.getOrNull(1)
    return selectedGalleryNo == requestedGalleryNo
}

private data class CleanerRunLogCount(
    val postCount: Int = 0,
    val commentCount: Int = 0
)

private data class CleanerRunLogEntry(
    val headnum: String,
    val memo: String
)
