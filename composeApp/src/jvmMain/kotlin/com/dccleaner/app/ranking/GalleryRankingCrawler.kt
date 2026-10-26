package com.dccleaner.app.ranking

import androidx.collection.MutableIntList
import androidx.collection.MutableLongList
import androidx.collection.MutableLongSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

class GalleryRankingException(message: String) : Exception(message)

/** 댓글을 받아야 할 글의 번호·댓글 수. 글 원본 대신 이것만 숫자 목록 두 개로 남겨 메모리를 아낀다. */
class CommentTargets {
    val nos = MutableLongList()
    val commentCounts = MutableIntList()
    val size get() = nos.size

    fun add(no: Long, commentCount: Int) {
        nos.add(no)
        commentCounts.add(commentCount)
    }
}

data class CrawlProgress(
    val stage: Stage,
    val posts: Int = 0,
    /** 글 목록에서 기간 시작까지 얼마나 내려왔는지 0~1. */
    val listFraction: Float = 0f,
    val commentRequestsDone: Int = 0,
    val commentRequestsTotal: Int = 0,
    /** 글 목록에서 어느 날짜까지 내려왔는지. */
    val reachedDate: LocalDate? = null,
    /** 기간 끝이 있는 페이지를 찾아 건너뛰는 중. */
    val seeking: Boolean = false
) {
    enum class Stage { RESOLVING, POSTS, COMMENTS }
}

/** 시작 페이지 탐색에서 본 목록 한 장. 빈 페이지(마지막 페이지 너머)는 시각이 모두 null. */
internal class SeekPage(val page: Int, val median: LocalDateTime?, val oldest: LocalDateTime?, val minutesPerPage: Double) {
    /** 이 페이지에서 기간 끝(end)보다 옛 글이 보이면, 기간이 여기나 앞쪽에서 끝난다. */
    fun reaches(end: LocalDateTime) = oldest?.isBefore(end) ?: true

    companion object {
        /** 끌올·공지가 섞여도 흔들리지 않게 가운데 시각과 사분위 간격(×2 = 한 장 분량)을 쓴다. */
        fun of(page: Int, times: List<LocalDateTime>): SeekPage {
            if (times.isEmpty()) return SeekPage(page, null, null, 0.0)
            val sorted = times.sorted()
            return SeekPage(page, sorted[sorted.size / 2], sorted.first(),
                minutesBetween(sorted[sorted.size / 4], sorted[sorted.size * 3 / 4]) * 2)
        }
    }
}

private fun minutesBetween(older: LocalDateTime, newer: LocalDateTime) =
    java.time.Duration.between(older, newer).seconds / 60.0

/**
 * 갤러리 목록(PC, 100개씩)과 댓글 API로 기간 안의 글·댓글을 모은다. 로그인은 필요 없다.
 * 오래전 기간이면 기간 끝이 있는 페이지를 먼저 찾아 그 사이 글을 건너뛴다.
 * 디시가 막지 않도록 목록은 3장, 댓글은 50개씩만 동시에 요청한다.
 */
internal class GalleryRankingCrawler(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun resolve(input: String): RankingGallery = withContext(Dispatchers.IO) {
        val (id, kind) = parseGalleryInput(input)
            ?: throw GalleryRankingException("갤러리 주소나 ID를 확인해 주세요")
        val candidates = kind?.let(::listOf) ?: listOf(GalleryKind.MAJOR, GalleryKind.MINOR, GalleryKind.MINI)
        for (candidate in candidates) {
            val html = get(listUrl(id, candidate, page = 1, listNum = 1)) ?: continue
            // 정식 주소로 마이너 갤러리를 열면 마이너 주소로 보내는 짧은 페이지가 온다.
            if (candidate == GalleryKind.MAJOR && "mgallery/board/lists" in html && "us-post" !in html) {
                if (kind == null) return@withContext RankingGallery(id, GalleryKind.MINOR, galleryName(id, GalleryKind.MINOR))
                continue
            }
            if ("us-post" in html) return@withContext RankingGallery(id, candidate, titleName(html) ?: id)
        }
        throw GalleryRankingException("갤러리를 찾지 못했어요. 주소나 ID를 확인해 주세요")
    }

    /** 갤러리마다 정한 기본 닉(유동 기본값)도 '아무나'로 보고 순위에서 뺀다. */
    suspend fun defaultNick(gallery: RankingGallery): String? = withContext(Dispatchers.IO) {
        runCatching {
            get("https://gall.dcinside.com/${gallery.kind.listPath}/write/?id=${gallery.id}")
                ?.let { Jsoup.parse(it).selectFirst("#gall_nick_name")?.attr("value")?.trim() }
                ?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }

    /**
     * 기간의 글을 받는 대로 [onPost]에 넘기고 원본은 버린다. 돌려주는 건 댓글을 받아야 할 글(번호·댓글 수)뿐이다.
     */
    suspend fun collectPosts(
        gallery: RankingGallery,
        dates: ClosedRange<LocalDate>,
        onPost: (RankingPost) -> Unit,
        onProgress: (CrawlProgress) -> Unit
    ): CommentTargets = coroutineScope {
        val start = dates.start.atStartOfDay()
        val end = dates.endInclusive.plusDays(1).atStartOfDay()
        val spanMinutes = java.time.Duration.between(start, end).toMinutes().toFloat()
        // 찾는 사이 페이지가 밀려 같은 글이 두 번 올 수 있어 번호만 기억한다.
        val seen = MutableLongSet()
        val targets = CommentTargets()
        suspend fun seekPage(p: Int) = SeekPage.of(p,
            removeBumpedPosts(parseListPage(requireHtml(listUrl(gallery.id, gallery.kind, p, LIST_NUM)))).map { it.time })
        val first = seekPage(1)
        if (!first.reaches(end)) onProgress(CrawlProgress(CrawlProgress.Stage.POSTS, seeking = true))
        // 찾는 사이 글이 지워져 페이지가 밀릴 수 있어 한 장 앞부터 읽는다. 겹친 글은 번호로 거른다.
        var page = (seekStartPage(first, end, ::seekPage) - 1).coerceAtLeast(1)
        while (true) {
            val batch = (page until page + LIST_BATCH).map { p ->
                async(Dispatchers.IO) { requireHtml(listUrl(gallery.id, gallery.kind, p, LIST_NUM)) }
            }.awaitAll()
            page += LIST_BATCH
            for (html in batch) {
                val parsed = removeBumpedPosts(parseListPage(html))
                if (parsed.isEmpty()) return@coroutineScope targets
                for (post in parsed) {
                    if (post.time.isBefore(start)) return@coroutineScope targets
                    if (post.time.isBefore(end) && seen.add(post.no)) {
                        onPost(post)
                        if (post.commentCount > 0) targets.add(post.no, post.commentCount)
                    }
                }
                val oldest = parsed.last().time
                val covered = java.time.Duration.between(oldest, end).toMinutes().coerceIn(0, spanMinutes.toLong())
                onProgress(CrawlProgress(CrawlProgress.Stage.POSTS, seen.size, covered / spanMinutes,
                    reachedDate = oldest.toLocalDate().coerceAtMost(dates.endInclusive)))
            }
        }
        @Suppress("UNREACHABLE_CODE")
        targets
    }

    /**
     * 댓글을 [COMMENT_CONCURRENCY]명의 일꾼이 차례로 받아 받는 즉시 [onComment]에 넘긴다. 쌓아 두지 않아 메모리가 늘지 않고,
     * 느린 페이지 하나가 나머지를 막지도 않는다. 도착 순서는 실행마다 다르므로 원래 순서(요청 번호·페이지 안 위치)를 함께
     * 넘긴다. [postCount]는 진행 표시에 쓰는 전체 글 수다.
     */
    suspend fun collectComments(
        gallery: RankingGallery,
        targets: CommentTargets,
        postCount: Int,
        onComment: (comment: RankingComment, order: Long) -> Unit,
        onProgress: (CrawlProgress) -> Unit
    ) = coroutineScope {
        val done = AtomicInteger()
        val next = AtomicInteger()
        val now = LocalDateTime.now(KST)
        // 글마다 100개씩 나뉜 댓글 페이지를 하나의 줄로 펼친다.
        val pageStarts = IntArray(targets.size + 1).also { starts ->
            for (i in 0 until targets.size) starts[i + 1] = starts[i] + (targets.commentCounts[i] + 99) / 100
        }
        val total = pageStarts.last()
        onProgress(CrawlProgress(CrawlProgress.Stage.COMMENTS, postCount, 1f, 0, total))
        repeat(COMMENT_CONCURRENCY) {
            launch(Dispatchers.IO) {
                // 잘 받아지는 페이지에는 멈출 틈(delay 등)이 없어, 취소·다른 일꾼의 실패를 매번 직접 확인하지 않으면 끝까지 받는다.
                while (isActive) {
                    val request = next.getAndIncrement()
                    if (request >= total) break
                    val index = pageStarts.binarySearch(request).let { if (it >= 0) it else -it - 2 }
                    val comments = parseComments(postForm(gallery, targets.nos[index], request - pageStarts[index] + 1), now)
                    // 받는 사이 취소됐다면 버려진 집계에 더하지 않는다.
                    ensureActive()
                    comments.forEachIndexed { i, comment -> onComment(comment, request.toLong() shl 16 or i.toLong()) }
                    onProgress(CrawlProgress(CrawlProgress.Stage.COMMENTS, postCount, 1f, done.incrementAndGet(), total))
                }
            }
        }
    }

    private fun listUrl(id: String, kind: GalleryKind, page: Int, listNum: Int) =
        "https://gall.dcinside.com/${kind.listPath}/lists/?id=$id&page=$page&list_num=$listNum"

    private suspend fun galleryName(id: String, kind: GalleryKind): String =
        get(listUrl(id, kind, 1, 1))?.let(::titleName) ?: id

    /** 막히거나 실패하면 잠깐 쉬었다가 다시 시도하고, 그래도 안 되면 멈춘다. */
    private suspend fun requireHtml(url: String): String {
        repeat(RETRIES) { attempt ->
            get(url)?.let { return it }
            delay(1_500L * (attempt + 1))
        }
        throw GalleryRankingException("디시인사이드가 잠시 응답하지 않아요. 잠시 후 다시 시도해 주세요")
    }

    /** 아이콘은 종류가 몇 개 안 돼서 순서대로 받는다. 못 받은 아이콘은 빼고 그린다. */
    suspend fun fetchIcons(urls: Collection<String>): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        urls.filter(String::isNotEmpty).distinct().mapNotNull { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { response ->
                    if (response.isSuccessful) url to response.body.bytes() else null
                }
            }.getOrNull()
        }.toMap()
    }

    /** 마지막 페이지 너머를 달라면 디시가 마지막 페이지로 돌려보내니, 다른 페이지가 오면 빈 페이지로 본다. */
    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            when {
                !response.isSuccessful -> null
                response.request.url.queryParameter("page") != request.url.queryParameter("page") -> ""
                else -> response.body.string()
            }
        }
    }.getOrNull()

    private suspend fun postForm(gallery: RankingGallery, no: Long, page: Int): String {
        val body = FormBody.Builder()
            .add("id", gallery.id).add("cmt_id", gallery.id).add("no", no.toString()).add("cmt_no", no.toString())
            .add("focus_cno", "").add("focus_pno", "").add("e_s_n_o", "zzz").add("comment_page", page.toString())
            .add("sort", "D").add("prevCnt", "").add("board_type", "").add("_GALLTYPE_", gallery.kind.commentType)
            .add("secret_article_key", "").add("clean", "").add("nptest", "")
            .build()
        val request = Request.Builder().url("https://gall.dcinside.com/board/comment/").post(body)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://gall.dcinside.com")
            .header("Referer", "https://gall.dcinside.com/${gallery.kind.listPath}/view/?id=${gallery.id}&no=$no")
            .build()
        repeat(RETRIES) { attempt ->
            val text = runCatching {
                client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
            }.getOrNull()
            if (text != null) return text
            delay(1_500L * (attempt + 1))
        }
        // 댓글 한 페이지를 못 받아도 전체를 버리지 않는다.
        return ""
    }

    companion object {
        private const val LIST_NUM = 100
        private const val LIST_BATCH = 3
        private const val COMMENT_CONCURRENCY = 50
        private const val RETRIES = 3
        /** 위쪽 경계를 모를 때 한 번에 건너뛰는 최대 페이지 수. */
        private const val MAX_SEEK_STEP = 1 shl 16
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        private val listDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val json = Json { ignoreUnknownKeys = true }

        /** PC·모바일 주소, 또는 ID만 받는다. 종류를 알 수 없으면 kind가 null. */
        internal fun parseGalleryInput(input: String): Pair<String, GalleryKind?>? {
            val text = input.trim()
            if (text.matches(ID_REGEX)) return text to null
            val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val path = uri.path.orEmpty()
            val id = uri.rawQuery?.split('&')?.firstNotNullOfOrNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "id" }?.get(1)
                    ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            }
            return when {
                host == "gall.dcinside.com" && id != null -> id to when {
                    path.startsWith("/mgallery/") -> GalleryKind.MINOR
                    path.startsWith("/mini/") -> GalleryKind.MINI
                    else -> null // 정식 주소로 마이너 갤러리를 열 수도 있어 확인한다.
                }
                host == "m.dcinside.com" -> {
                    val segments = path.split('/').filter(String::isNotEmpty)
                    when (segments.firstOrNull()) {
                        "mini" -> segments.getOrNull(1)?.let { it to GalleryKind.MINI }
                        "board", "write" -> segments.getOrNull(1)?.let { it to null }
                        else -> null
                    }
                }
                else -> null
            }?.takeIf { it.first.matches(ID_REGEX) }
        }

        private val ID_REGEX = Regex("[A-Za-z0-9_]{2,40}")

        /**
         * 옛 글이 처음 보이는(oldest < end) 가장 앞 페이지를 찾는다. 1페이지에서 보이면 1.
         * lo(모두 end보다 새 글)와 hi(end에 닿았거나 빈 페이지) 사이를 좁혀 hi == lo+1이면 hi.
         */
        internal suspend fun seekStartPage(first: SeekPage, end: LocalDateTime, probe: suspend (Int) -> SeekPage): Int {
            if (first.reaches(end)) return 1
            var lo = first
            var hi: SeekPage? = null
            var expansions = 0
            var streak = 0
            var stalls = 0
            while (true) {
                hi?.takeIf { it.page == lo.page + 1 }?.let { return it.page }
                val width = hi?.let { it.page - lo.page }
                val next = probe(nextSeekPage(first, lo, hi, end, expansions, streak, stalls))
                if (next.reaches(end)) {
                    hi = next
                    streak = if (streak > 0) streak + 1 else 1
                } else {
                    lo = next
                    streak = if (streak < 0) streak - 1 else -1
                    if (hi == null) expansions++
                }
                val narrowed = hi?.let { it.page - lo.page }
                if (width != null && narrowed != null) stalls = if (narrowed > (width + 1) / 2) stalls + 1 else 0
            }
        }

        /**
         * 다음에 볼 페이지. 경계가 없으면 글 쓰는 속도로 end까지 거리를 어림하고, 못 미칠 때마다 두 배씩 더 간다.
         * 경계가 있으면 lo·hi의 가운데 시각으로 보간하되, 같은 쪽에 streak번 연달아 떨어졌으면 반대쪽으로
         * 2^streak-1장 더 옮긴다(보간이 조금씩 빗나갈 때 한 장씩 기어가지 않게). 끝을 넘거나 세 번 연속
         * 절반도 못 줄이면 반으로 가른다.
         */
        internal fun nextSeekPage(
            first: SeekPage, lo: SeekPage, hi: SeekPage?, end: LocalDateTime, expansions: Int, streak: Int, stalls: Int
        ): Int {
            val loTime = lo.median!!
            val minutesPerPage = if (lo.page > first.page) minutesBetween(loTime, first.median!!) / (lo.page - first.page)
                else first.minutesPerPage
            val pagesToEnd = minutesBetween(end, loTime) / minutesPerPage.coerceAtLeast(0.1)
            if (hi == null) {
                val step = ceil(pagesToEnd * (1 shl expansions.coerceAtMost(16)))
                return lo.page + step.coerceIn(1.0, MAX_SEEK_STEP.toDouble()).toInt()
            }
            val bisect = (lo.page + hi.page) / 2
            if (stalls >= 3) return bisect
            val hiTime = hi.median
            val interpolated = when {
                hiTime == null -> lo.page + ceil(pagesToEnd)
                hiTime < loTime -> lo.page + (hi.page - lo.page) * minutesBetween(end, loTime) / minutesBetween(hiTime, loTime)
                else -> return bisect
            }
            // 보간이 이미 경계 밖이면(빈 페이지 너머까지 어림하는 등) 옮겨 봐야 소용없다.
            if (interpolated <= lo.page || interpolated >= hi.page + 1) return bisect
            val shift = (1 shl abs(streak).coerceAtMost(20)) - 1
            val guess = interpolated - shift * streak.sign
            return if (guess > lo.page && guess < hi.page) guess.toInt().coerceIn(lo.page + 1, hi.page - 1) else bisect
        }

        internal fun titleName(html: String): String? =
            Jsoup.parse(html).title().substringBefore(" - ").trim().takeIf(String::isNotEmpty)

        internal fun parseListPage(html: String): List<RankingPost> =
            Jsoup.parse(html).select("tbody.listwrap2 tr.us-post, .gall_list tbody tr.us-post").mapNotNull { row ->
                if (row.attr("data-type") == "icon_notice" || row.selectFirst("em.icon_notice") != null) return@mapNotNull null
                val no = row.selectFirst(".gall_num")?.text()?.trim()?.toLongOrNull() ?: return@mapNotNull null
                val writer = row.selectFirst(".gall_writer") ?: return@mapNotNull null
                val time = row.selectFirst(".gall_date")?.attr("title")
                    ?.let { runCatching { LocalDateTime.parse(it, listDate) }.getOrNull() } ?: return@mapNotNull null
                val uid = writer.attr("data-uid").trim()
                val icon = writer.selectFirst("a.writer_nikcon img")?.attr("src").orEmpty()
                RankingPost(
                    no = no,
                    nick = writer.attr("data-nick").ifBlank { writer.selectFirst("em")?.text().orEmpty() }.trim(),
                    uid = uid,
                    ip = if (uid.isBlank()) writer.attr("data-ip").trim() else "",
                    nickType = nickType(uid, icon),
                    iconUrl = iconUrl(icon),
                    time = time,
                    commentCount = row.selectFirst(".reply_numbox .reply_num")?.text()
                        ?.trim('[', ']', ' ')?.substringBefore('/')?.toIntOrNull() ?: 0
                )
            }.distinctBy { it.no }

        /** 끌올된 글(아래 글보다 번호가 작은데 위에 있는 글)과 페이지 끝의 동떨어진 옛 글을 뺀다. */
        internal fun removeBumpedPosts(posts: List<RankingPost>): List<RankingPost> {
            val kept = ArrayList<RankingPost>()
            var maxBelow = Long.MIN_VALUE
            for (post in posts.asReversed()) {
                if (post.no < maxBelow) continue
                maxBelow = post.no
                kept += post
            }
            kept.reverse()
            if (kept.size < 7) return kept
            val gaps = kept.dropLast(1).zipWithNext { a, b -> a.no - b.no }.filter { it > 0 }
            if (gaps.size < 5) return kept
            val boundaryGap = kept[kept.size - 2].no - kept.last().no
            return if (boundaryGap > gaps.average() * 10) kept.dropLast(1) else kept
        }

        internal fun parseComments(text: String, now: LocalDateTime): List<RankingComment> {
            if (text.isBlank()) return emptyList()
            val comments = runCatching { json.parseToJsonElement(text).jsonObject["comments"] as? JsonArray }
                .getOrNull() ?: return emptyList()
            return comments.mapNotNull { element ->
                val c = element.jsonObject
                fun s(key: String) = c[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                val name = s("name")
                if (name.isBlank() || s("del_yn") == "Y" || s("nicktype") == "COMMENT_BOY") return@mapNotNull null
                val time = commentTime(s("reg_date"), now) ?: return@mapNotNull null
                val uid = s("user_id")
                RankingComment(
                    nick = name,
                    uid = uid,
                    ip = if (uid.isBlank()) s("ip") else "",
                    nickType = nickType(uid, s("gallog_icon")),
                    time = time,
                    content = s("memo"),
                    iconUrl = iconUrl(s("gallog_icon"))
                )
            }
        }

        /** "10.18 10:52:32"는 올해로 보되, 미래가 되면 작년 글이다. "2025.10.18 ..."도 받는다. */
        internal fun commentTime(raw: String, now: LocalDateTime): LocalDateTime? = runCatching {
            val (date, time) = raw.trim().split(' ', limit = 2)
            val parts = date.split('.').map(String::toInt)
            val (h, m, sec) = time.split(':').map(String::toInt).let { Triple(it[0], it[1], it.getOrElse(2) { 0 }) }
            if (parts.size == 3) LocalDateTime.of(parts[0], parts[1], parts[2], h, m, sec)
            else LocalDateTime.of(now.year, parts[0], parts[1], h, m, sec).let { if (it.isAfter(now.plusDays(1))) it.minusYears(1) else it }
        }.getOrNull()

        /** img 태그나 주소에서 디시 도메인 아이콘 주소만 뽑는다. 다른 곳 주소는 내려받지 않는다. */
        internal fun iconUrl(raw: String): String {
            val src = if ('<' in raw) Jsoup.parse(raw).selectFirst("img")?.attr("src").orEmpty() else raw.trim()
            if (src.isEmpty()) return ""
            val uri = runCatching { URI("https://gall.dcinside.com/").resolve(src) }.getOrNull() ?: return ""
            val host = uri.host.orEmpty()
            val dc = host == "dcinside.com" || host.endsWith(".dcinside.com") || host == "dcinside.co.kr" || host.endsWith(".dcinside.co.kr")
            return if (dc) uri.toString().replaceFirst("http://", "https://") else ""
        }

        private fun nickType(uid: String, iconHtml: String): NickType = when {
            uid.isBlank() -> NickType.ANON
            "fix_" in iconHtml || "bestcon_fix" in iconHtml -> NickType.FIXED
            else -> NickType.HALF
        }
    }
}
