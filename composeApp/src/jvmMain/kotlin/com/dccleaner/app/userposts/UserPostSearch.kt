package com.dccleaner.app.userposts

import com.dccleaner.app.ranking.GalleryKind
import com.dccleaner.app.ranking.GalleryRankingCrawler
import com.dccleaner.app.ranking.RankingGallery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/*
 * 반고닉 글 찾기: 디시 글쓴이 검색(공개 검색 결과)으로 닉네임이 같은 글을 받고, 그중 식별코드(또는 유동 IP)가 같은 글만 남긴다.
 * 2026-10 실제 페이지로 확인한 구조:
 * - PC 검색 결과 한 페이지는 20개, 글마다 data-uid·data-ip·data-nick과 작성 시각(title)이 있다. list_num은 무시된다.
 * - 닉네임은 부분 일치로 찾아진다("ㅇㅇ" → "ㅇㅇㅇ"). 그래서 식별코드·IP로 다시 거른다.
 * - 한 번에 글 약 1만 개 범위만 찾는다. 범위 안은 page로, 다음 범위는 "다음 검색" 링크의 search_pos로 간다(계산하지 않는다).
 *   결과가 0개인 범위에도 다음 검색 링크가 있고, 링크가 없으면 처음 글까지 다 본 것이다.
 * - 마지막 페이지 너머를 달라면 마지막 페이지로 돌려보낸다(같은 글이 다시 온다).
 * - 닉네임은 글마다 쓸 때 값으로 남아, 닉네임을 바꾸기 전에 쓴 글은 지금 닉네임으로 찾지 못한다.
 */

/** 찾을 사람: 고닉·반고닉 식별코드, 또는 유동 IP 앞 두 자리. */
enum class UserPostTargetKind { UID, IP }

/** [value]는 식별코드나 IP. IP로 찾을 때는 디시 검색이 닉네임으로만 되므로 [nickname]이 필요하다(유동 기본은 ㅇㅇ). */
data class UserPostSearchRequest(
    val galleryInput: String,
    val kind: UserPostTargetKind,
    val value: String,
    val nickname: String = ""
)

data class UserPostTarget(
    val gallery: RankingGallery,
    val kind: UserPostTargetKind,
    val value: String,
    /** 검색에 쓴 닉네임. 식별코드면 갤로그에서 가져온 지금 닉네임이다. */
    val nickname: String
) {
    fun matches(post: FoundPost) = when (kind) {
        UserPostTargetKind.UID -> post.uid == value
        UserPostTargetKind.IP -> post.uid.isEmpty() && post.ip == value
    }
}

enum class PostKind { TEXT, IMAGE, MOVIE }

/** 검색 결과에서 찾은 글 하나. 목록에 보이는 정보만 담는다(본문은 받지 않는다). */
data class FoundPost(
    val no: Long,
    val title: String,
    /** 말머리. 말머리를 쓰지 않는 갤러리는 빈칸. */
    val subject: String,
    val kind: PostKind,
    /** 개념글(추천 글) 표시. */
    val recommended: Boolean,
    val commentCount: Int,
    val nick: String,
    val uid: String,
    val ip: String,
    val time: LocalDateTime,
    val views: Int,
    val recommends: Int
)

class UserPostSearchException(message: String) : Exception(message)

/** PC 글 주소. 브라우저로 열 때 쓴다. */
fun RankingGallery.pcPostUrl(no: Long) = "https://gall.dcinside.com/${kind.listPath}/view/?id=$id&no=$no"

/** 모바일 글 주소. */
fun RankingGallery.mobilePostUrl(no: Long) =
    if (kind == GalleryKind.MINI) "https://m.dcinside.com/mini/$id/$no" else "https://m.dcinside.com/board/$id/$no"

/**
 * 디시 앱으로 여는 주소. 디시 모바일 웹이 앱으로 보낼 때 쓰는 형식(dcapp://m.dcinside.com/view.php?id=&no=)이다.
 * 미니 갤러리는 이 형식으로 열리는지 확인하지 못해 null(브라우저로 연다).
 */
fun RankingGallery.dcAppUri(no: Long): String? =
    if (kind == GalleryKind.MINI) null else "dcapp://m.dcinside.com/view.php?id=$id&no=$no"

/** 검색 결과 한 페이지. */
internal data class SearchPage(
    val posts: List<FoundPost>,
    /** 이 범위 안에 다음 페이지가 있는지. */
    val hasNextPage: Boolean,
    /** 페이지 칸에 보이는 이 범위의 가장 뒤 페이지 번호('끝' 포함). 모르면 지금 페이지. */
    val lastPage: Int,
    /** 다음 범위의 search_pos. 없으면 처음 글까지 다 봤다. */
    val nextWindow: String?
)

internal class UserPostSearchCrawler(private val client: OkHttpClient = defaultClient) {
    private val galleryResolver = GalleryRankingCrawler(client)

    suspend fun resolveGallery(input: String): RankingGallery = galleryResolver.resolve(input)

    /** 갤로그의 지금 닉네임. 없는 식별코드면 404가 온다. */
    suspend fun gallogNickname(uid: String): String {
        val html = requireHtml("https://gallog.dcinside.com/$uid", notFound = "이 식별코드의 갤로그를 찾지 못했어요. 식별코드가 틀렸거나 탈퇴한 계정이에요")
        return Jsoup.parse(html).selectFirst(".nick_name")?.text()?.trim()?.takeIf(String::isNotEmpty)
            ?: throw UserPostSearchException("갤로그에서 닉네임을 읽지 못했어요. 잠시 후 다시 시도해 주세요")
    }

    /** [window]가 빈칸이면 가장 최근 범위. 마지막 페이지 너머라 돌려보내졌으면 빈 페이지로 본다. */
    suspend fun searchPage(gallery: RankingGallery, nickname: String, window: String, page: Int): SearchPage {
        val url = "https://gall.dcinside.com/${gallery.kind.listPath}/lists/?id=${gallery.id}&s_type=search_name" +
            "&s_keyword=${dcKeyword(nickname)}&page=$page&search_pos=$window"
        // 오류를 200과 알림 스크립트로 주는 일이 있다. 목록이 없는 페이지를 '끝'으로 보지 않고 실패로 다시 시도한다.
        val fetched = requireFetched(url, isComplete = { "gall_list" in it })
        val parsed = parseSearchPage(fetched.body.orEmpty())
        // 범위의 페이지 수가 그새 줄어 다른 페이지로 돌려보내졌다면, 이 범위는 끝난 것이다. 다음 범위 링크는 그대로 쓴다.
        return if (fetched.redirected) parsed.copy(posts = emptyList(), hasNextPage = false, lastPage = page) else parsed
    }

    /** 갤러리 일반 목록 첫 페이지의 글이 모두 식별코드·IP 없이 오면 작성자를 숨긴 갤러리다(검색 결과가 0개여도 알 수 있게). */
    suspend fun hidesWriters(gallery: RankingGallery): Boolean {
        val html = requireHtml("https://gall.dcinside.com/${gallery.kind.listPath}/lists/?id=${gallery.id}&page=1&list_num=50",
            isComplete = { "gall_list" in it })
        val posts = parseSearchPage(html).posts
        return posts.isNotEmpty() && posts.none { it.uid.isNotEmpty() || it.ip.isNotEmpty() }
    }

    /** 일시적인 실패는 잠깐 쉬었다가 다시 시도하고, 그래도 안 되면 멈춘다. */
    private suspend fun requireHtml(url: String, notFound: String? = null, isComplete: (String) -> Boolean = { true }): String =
        requireFetched(url, notFound, isComplete).body.orEmpty()

    private suspend fun requireFetched(url: String, notFound: String? = null, isComplete: (String) -> Boolean = { true }): Fetched {
        repeat(RETRIES) { attempt ->
            val result = fetch(url)
            if (result.code == 404 && notFound != null) throw UserPostSearchException(notFound)
            if (result.body != null && isComplete(result.body)) return result
            // 마지막 시도 뒤에는 기다리지 않고 바로 알린다.
            if (attempt < RETRIES - 1) delay(1_500L * (attempt + 1))
        }
        throw UserPostSearchException("디시인사이드가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요")
    }

    private class Fetched(val code: Int, val body: String?, val redirected: Boolean = false)

    /** 취소되면 보내 놓은 요청도 바로 끊는다(기다리지 않는다). */
    private suspend fun fetch(url: String): Fetched = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resume(Fetched(0, null))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        when {
                            !it.isSuccessful -> Fetched(it.code, null)
                            // 마지막 페이지 너머를 달라면 다른 페이지로 돌려보낸다.
                            else -> Fetched(it.code, it.body.string(),
                                redirected = it.request.url.queryParameter("page") != request.url.queryParameter("page"))
                        }
                    }
                }.getOrElse { Fetched(0, null) }
                continuation.resume(result)
            }
        })
    }

    companion object {
        private const val RETRIES = 3
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        private val listDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        internal fun parseSearchPage(html: String): SearchPage {
            val doc = Jsoup.parse(html)
            val posts = doc.select(".gall_list tbody tr.us-post").mapNotNull { row ->
                val type = row.attr("data-type")
                if ("notice" in type || "survey" in type) return@mapNotNull null
                val no = row.selectFirst(".gall_num")?.text()?.trim()?.toLongOrNull() ?: return@mapNotNull null
                val writer = row.selectFirst(".gall_writer") ?: return@mapNotNull null
                val time = row.selectFirst(".gall_date")?.attr("title")
                    ?.let { runCatching { LocalDateTime.parse(it, listDate) }.getOrNull() } ?: return@mapNotNull null
                val titleLink = row.select(".gall_tit > a").firstOrNull { !it.hasClass("reply_numbox") } ?: return@mapNotNull null
                val uid = writer.attr("data-uid").trim()
                FoundPost(
                    no = no,
                    title = titleLink.text().trim(),
                    subject = row.selectFirst(".gall_subject")?.text()?.trim().orEmpty(),
                    kind = when {
                        "movie" in type -> PostKind.MOVIE
                        "pic" in type || "img" in type -> PostKind.IMAGE
                        else -> PostKind.TEXT
                    },
                    recommended = "recom" in type,
                    commentCount = row.selectFirst(".reply_numbox .reply_num")?.text()
                        ?.trim('[', ']', ' ')?.substringBefore('/')?.toIntOrNull() ?: 0,
                    nick = writer.attr("data-nick").ifBlank { writer.selectFirst("em")?.text().orEmpty() }.trim(),
                    uid = uid,
                    ip = if (uid.isEmpty()) writer.attr("data-ip").trim() else "",
                    time = time,
                    views = row.selectFirst(".gall_count")?.text()?.trim()?.toIntOrNull() ?: 0,
                    recommends = row.selectFirst(".gall_recommend")?.text()?.trim()?.toIntOrNull() ?: 0
                )
            }.distinctBy { it.no }
            // 아래쪽 페이지 칸(즐겨찾기 칸에도 빈 페이지 칸이 있다)에서 지금 페이지보다 뒤 페이지 링크가 있는지 본다.
            val box = doc.select(".bottom_paging_box").firstOrNull { it.selectFirst("em") != null }
            val current = box?.selectFirst("em")?.text()?.trim()?.toIntOrNull()
            val linkedPages = box?.select("a[href]").orEmpty().filterNot { it.hasClass("search_next") }
                .mapNotNull { query(it.attr("href"), "page")?.toIntOrNull() }
            val hasNextPage = current != null && linkedPages.any { it > current }
            val lastPage = maxOf(current ?: 1, linkedPages.maxOrNull() ?: 1)
            val nextLink = doc.selectFirst("a.search_next")
            val nextWindow = nextLink?.attr("href")?.let { query(it, "search_pos") }?.takeIf { it.isNotEmpty() }
            // 다음 검색 링크가 있는데 위치(숫자)를 못 읽으면 끝으로 보지 않는다. '처음 글까지 다 확인했어요'로 잘못 알리지 않고,
            // 읽은 값을 그대로 주소에 붙이므로 숫자가 아닌 값은 쓰지 않는다.
            if (nextLink != null && nextWindow?.toLongOrNull() == null) throw UserPostSearchException("디시 검색 결과를 읽지 못했어요. 잠시 후 다시 시도해 주세요")
            return SearchPage(posts, hasNextPage, lastPage, nextWindow)
        }

        private fun query(href: String, name: String): String? = runCatching {
            URI("https://gall.dcinside.com").resolve(href.replace(" ", "%20")).rawQuery
        }.getOrNull()?.split('&')?.firstNotNullOfOrNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 && it[0] == name }?.get(1)
                ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrDefault(it) }
        }

        /** 식별코드(영문·숫자·밑줄). */
        // 디시 식별코드는 소문자다(대문자로 넣으면 갤로그가 소문자 주소로 보내고 data-uid도 소문자). 휴대폰 키보드의 첫 글자 대문자를 맞춘다.
        // PC(gallog.dcinside.com/식별코드)·모바일(m.dcinside.com/gallog/식별코드) 갤로그 주소를 붙여 넣어도 식별코드만 꺼낸다.
        internal fun normalizeUid(input: String): String? {
            val text = input.trim()
            val fromGallog = GALLOG_URL.matchEntire(text)?.groupValues?.get(1)
            return (fromGallog ?: text).lowercase().takeIf { it.matches(Regex("[a-z0-9_]{2,40}")) }
        }

        private val GALLOG_URL = Regex("""(?i)(?:https?://)?(?:gallog\.dcinside\.com|m\.dcinside\.com/gallog)/([A-Za-z0-9_]+)(?:[/?#].*)?""")

        /**
         * 디시 검색어 인코딩. 디시는 받은 검색어의 '.'을 '%'로 바꿔 한 번 더 풀어서, 일반 URL 인코딩이면 '.'·'+'가 든 닉네임을
         * 못 찾는다. 디시가 만드는 링크와 같이 모든 바이트를 '.XX'로 쓴다(예: ㅇㅇ → .E3.85.87.E3.85.87).
         */
        internal fun dcKeyword(text: String): String =
            text.toByteArray(Charsets.UTF_8).joinToString("") { ".%02X".format(it.toInt() and 0xFF) }

        /** 디시가 보여 주는 유동 IP는 앞 두 자리다. "(118.235)"나 전체 IP를 넣어도 앞 두 자리로 맞춘다. */
        internal fun normalizeIp(input: String): String? {
            val parts = input.trim().trim('(', ')').split('.')
            if (parts.size != 2 && parts.size != 4) return null
            if (parts.any { p -> p.isEmpty() || p.length > 3 || !p.all(Char::isDigit) || p.toInt() > 255 }) return null
            return "${parts[0].toInt()}.${parts[1].toInt()}"
        }
    }
}

/**
 * 한 사람의 글을 범위·페이지 순서로 이어서 찾는다. 성공한 페이지만큼만 앞으로 가서, 실패하면 같은 자리부터 다시 할 수 있다.
 * 빠르게 찾으려고 다음 자리 몇 개를 미리 정해 [CONCURRENCY]개씩 동시에 받는다. 결과는 늘 순서대로 붙이고,
 * 실제 다음 자리가 미리 정한 것과 다르면(여러 페이지짜리 범위였거나 범위 간격이 달랐으면) 그 뒤는 버리고 실제 자리를 따른다.
 */
internal class UserPostSearchSession(private val crawler: UserPostSearchCrawler, val target: UserPostTarget) {
    private var window = ""
    private var page = 1
    /** 지금 범위에서 페이지 칸으로 알게 된 가장 뒤 페이지. 같은 범위의 다음 페이지들을 한꺼번에 받을 때 쓴다. */
    private var windowLastPage = 1
    private var requested = false
    private val seen = HashSet<Long>()
    /** 첫 범위의 다음 검색 위치 + 한 범위. 남은 범위로 대략의 진행률을 낸다. */
    private var totalPositions: Long? = null
    private var rowsSeen = 0
    private var rowsWithWriter = 0

    var exhausted = false
        private set

    /** 최근 글부터 확인한 범위(대략의 글 수). 범위 하나는 글 약 1만 개다. 첫 범위를 다 보기 전에는 0. */
    val checkedPosts: Long
        get() = totalPositions?.let { total ->
            if (exhausted) total else total - (window.toLongOrNull()?.let { -it } ?: total)
        }?.coerceAtLeast(0) ?: 0L

    /** 처음 글까지 확인한 비율(대략). 갤러리가 작아 범위가 하나뿐이면 끝날 때 1. */
    val progress: Float
        get() = when {
            exhausted -> 1f
            else -> totalPositions?.let { total ->
                val remaining = window.toLongOrNull()?.let { -it } ?: total
                (1f - remaining.toFloat() / total).coerceIn(0f, 1f)
            } ?: 0f
        }

    /**
     * 새 글을 하나라도 찾을 때까지(받은 묶음은 끝까지 읽는다), 또는 [maxRequests]번 요청할 때까지, 또는 끝까지 찾는다.
     * 순서대로 붙인 페이지마다 [onPage]로 찾은 글을 알린다. 보낸 요청 수를 돌려준다.
     */
    suspend fun loadMore(maxRequests: Int, concurrency: Int = CONCURRENCY, onPage: (found: List<FoundPost>) -> Unit): Int {
        var requests = 0
        var foundThisRound = 0
        while (!exhausted && requests < maxRequests && foundThisRound == 0) {
            currentCoroutineContext().ensureActive()
            if (requested) delay(REQUEST_GAP_MILLIS)
            requested = true
            val batch = plan(minOf(concurrency, maxRequests - requests))
            val results = coroutineScope {
                batch.map { (w, p) -> async { crawler.searchPage(target.gallery, target.nickname, w, p) } }.awaitAll()
            }
            currentCoroutineContext().ensureActive()
            requests += batch.size
            for ((spot, result) in batch.zip(results)) {
                // 앞 결과로 자리가 예상과 달라졌다면 이 결과는 쓰지 않는다(다음 묶음에서 실제 자리부터 받는다).
                if (spot.first != window || spot.second != page) break
                foundThisRound += accept(result, onPage)
                if (exhausted) break
            }
        }
        return requests
    }

    /** 다음에 받을 자리들. 같은 범위의 뒤 페이지가 남았으면 그 페이지들을, 아니면 다음 범위들의 첫 페이지를. */
    private fun plan(count: Int): List<Pair<String, Int>> {
        val position = window.toLongOrNull()
        return when {
            page > 1 -> (page..minOf(windowLastPage, page + count - 1)).map { window to it }
            // 첫 범위는 위치를 모른다. 첫 페이지의 다음 검색 링크로 알게 된 뒤부터 미리 정한다.
            position == null -> listOf(window to page)
            else -> (0 until count).map { position + it * WINDOW_POSITIONS }.takeWhile { it < 0 }.map { it.toString() to 1 }
        }.ifEmpty { listOf(window to page) }
    }

    /** 받은 페이지 하나를 순서대로 붙이고 다음 자리로 옮긴다. 찾은 새 글 수를 돌려준다. */
    private fun accept(result: SearchPage, onPage: (List<FoundPost>) -> Unit): Int {
        rowsSeen += result.posts.size
        rowsWithWriter += result.posts.count { it.uid.isNotEmpty() || it.ip.isNotEmpty() }
        // 글은 있는데 아무도 식별코드·IP가 없으면 작성자 정보를 숨긴 갤러리다(예: 일부 미니 갤러리).
        if (rowsSeen >= HIDDEN_CHECK_ROWS && rowsWithWriter == 0) {
            exhausted = true
            throw UserPostSearchException("이 갤러리는 글쓴이의 식별코드·IP를 숨겨서 찾을 수 없어요")
        }
        val found = result.posts.filter { target.matches(it) && seen.add(it.no) }
        // 행 수가 아니라 다음 페이지 링크로 판단한다(행을 하나 못 읽어도 그 범위의 나머지를 건너뛰지 않게).
        if (result.hasNextPage) {
            page++
            windowLastPage = maxOf(result.lastPage, page)
        } else {
            val next = result.nextWindow
            if (next == null) {
                exhausted = true
            } else if (next == window) {
                // 다음 범위가 제자리면 끝없이 같은 범위를 받게 된다('처음 글까지' 모드). 멈추고 알린다.
                throw UserPostSearchException("디시 검색이 다음 범위로 넘어가지 않아요. 잠시 후 다시 시도해 주세요")
            } else {
                if (totalPositions == null && window.isEmpty()) totalPositions = (next.toLongOrNull()?.let { -it } ?: 0L) + WINDOW_POSITIONS
                window = next
                page = 1
                windowLastPage = 1
            }
        }
        onPage(found)
        return found.size
    }

    companion object {
        /** 다음 검색 위치는 매번 1만씩 줄었다(프로그래밍·stockus에서 확인). 다르면 실제 링크를 따른다. */
        private const val WINDOW_POSITIONS = 10_000L
        internal const val CONCURRENCY = 5
        private const val REQUEST_GAP_MILLIS = 300L
        private const val HIDDEN_CHECK_ROWS = 20
    }
}
