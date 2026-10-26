package com.dccleaner.app.userposts

import com.dccleaner.app.ranking.GalleryKind
import com.dccleaner.app.ranking.RankingGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2026-10 실제 디시 PC 검색 결과의 구조(행·페이지 칸·다음 검색 링크)를 본뜬 가짜 페이지로 확인한다. */
class UserPostSearchTest {
    private val gallery = RankingGallery("g", GalleryKind.MAJOR, "테스트 갤러리")

    private fun row(no: Long, nick: String, uid: String = "", ip: String = "", type: String = "icon_txt",
                    subject: String? = null, comments: Int = 0) =
        """<tr class="ub-content us-post" data-no="$no" data-type="$type"><td class="gall_num">${if ("notice" in type) "공지" else no}</td>""" +
            (subject?.let { """<td class="gall_subject">$it</td>""" } ?: "") +
            """<td class="gall_tit ub-word"><a href="/board/view/?id=g&no=$no"><em class="icon_img $type"></em>제목 $no</a>""" +
            (if (comments > 0) """<a class="reply_numbox" href="#"><span class="reply_num">[$comments]</span></a>""" else "") +
            """</td><td class="gall_writer ub-writer" data-nick="$nick" data-uid="$uid" data-ip="$ip" data-loc="list">""" +
            """<span class="nickname"><em>$nick</em></span></td><td class="gall_date" title="2026-10-21 23:28:52">23:28</td>""" +
            """<td class="gall_count">6</td><td class="gall_recommend">1</td></tr>"""

    private fun page(rows: List<String>, window: String, current: Int, last: Int, next: String?): String {
        val pager = (1..last).joinToString("") { p ->
            if (p == current) "<em>$p</em>" else """<a href="/board/lists/?id=g&page=$p&search_pos=$window&s_type=search_name&s_keyword=.E3.85.87">$p</a>"""
        } + (next?.let { """<a href="/board/lists/?id=g&page=1&search_pos=$it&s_type=search_name&s_keyword=.E3.85.87" class="search_next">다음 검색</a>""" } ?: "")
        // 위쪽 즐겨찾기 칸에도 빈 페이지 칸이 있다(실제 페이지와 같게).
        return """<html><body><table class="gall_list"><tbody>${rows.joinToString("")}</tbody></table>""" +
            """<div class="bottom_paging_box iconpaging"></div><div class="bottom_paging_wrap re"><div class="bottom_paging_box iconpaging">$pager</div></div></body></html>"""
    }

    @Test
    fun parsesSearchRowsAndPaging() {
        val html = page(listOf(
            row(10, "공지", type = "icon_notice"),
            row(9, "fila", uid = "fila1", type = "icon_pic", subject = "일반", comments = 12),
            row(8, "ㅇㅇ", ip = "118.235", type = "icon_recomimg"),
            row(7, "fila", uid = "fila1", type = "icon_movie")
        ), window = "", current = 1, last = 3, next = "-2869301")
        val parsed = UserPostSearchCrawler.parseSearchPage(html)
        assertEquals(listOf(9L, 8L, 7L), parsed.posts.map { it.no }) // 공지는 뺀다
        val first = parsed.posts[0]
        assertEquals("제목 9", first.title)
        assertEquals("일반", first.subject)
        assertEquals(PostKind.IMAGE, first.kind)
        assertEquals(12, first.commentCount)
        assertEquals("fila1", first.uid)
        assertEquals("", first.ip)
        assertEquals(LocalDateTime.of(2026, 10, 21, 23, 28, 52), first.time)
        assertEquals(6, first.views)
        assertEquals(1, first.recommends)
        assertTrue(parsed.posts[1].recommended)
        assertEquals("118.235", parsed.posts[1].ip)
        assertEquals(PostKind.MOVIE, parsed.posts[2].kind)
        assertTrue(parsed.hasNextPage)
        assertEquals("-2869301", parsed.nextWindow)

        // 범위의 마지막 페이지: 뒤 페이지 링크가 없고(다음 검색 링크는 페이지로 치지 않는다), 다음 범위는 있다.
        val last = UserPostSearchCrawler.parseSearchPage(page(listOf(row(1, "a", uid = "a")), "", current = 3, last = 3, next = "-5"))
        assertFalse(last.hasNextPage)
        assertEquals("-5", last.nextWindow)
        // 처음 글까지 다 본 범위: 다음 검색 링크도 없다.
        assertNull(UserPostSearchCrawler.parseSearchPage(page(emptyList(), "-5", current = 1, last = 1, next = null)).nextWindow)
    }

    @Test
    fun normalizesInputs() {
        assertEquals("118.235", UserPostSearchCrawler.normalizeIp("118.235"))
        assertEquals("118.235", UserPostSearchCrawler.normalizeIp(" (118.235) "))
        assertEquals("118.235", UserPostSearchCrawler.normalizeIp("118.235.10.4"))
        assertEquals("1.2", UserPostSearchCrawler.normalizeIp("001.02"))
        assertNull(UserPostSearchCrawler.normalizeIp("118"))
        assertNull(UserPostSearchCrawler.normalizeIp("300.1"))
        assertNull(UserPostSearchCrawler.normalizeIp("118.235.1"))
        assertEquals("fila_12", UserPostSearchCrawler.normalizeUid(" fila_12 "))
        // 휴대폰 키보드가 첫 글자를 대문자로 바꿔도 디시 식별코드(소문자)로 맞춘다.
        assertEquals("cling2258", UserPostSearchCrawler.normalizeUid("Cling2258"))
        // PC·모바일 갤로그 주소를 붙여 넣어도 식별코드만 꺼낸다.
        assertEquals("cling2258", UserPostSearchCrawler.normalizeUid("https://gallog.dcinside.com/cling2258"))
        assertEquals("cling2258", UserPostSearchCrawler.normalizeUid("gallog.dcinside.com/Cling2258/posting?cno=2"))
        assertEquals("cling2258", UserPostSearchCrawler.normalizeUid(" https://m.dcinside.com/gallog/cling2258 "))
        assertEquals("cling2258", UserPostSearchCrawler.normalizeUid("http://m.dcinside.com/gallog/cling2258/home#top"))
        assertNull(UserPostSearchCrawler.normalizeUid("https://gall.dcinside.com/board/lists/?id=programming"))
        assertNull(UserPostSearchCrawler.normalizeUid("https://gallog.dcinside.com/"))
        // 디시는 검색어의 '.'을 '%'로 바꿔 한 번 더 풀기 때문에, 모든 바이트를 '.XX'로 보낸다.
        assertEquals(".E3.85.87.E3.85.87", UserPostSearchCrawler.dcKeyword("ㅇㅇ"))
        assertEquals(".67.75.69.2E.6F.72.67.2B.20", UserPostSearchCrawler.dcKeyword("gui.org+ "))
        assertNull(UserPostSearchCrawler.normalizeUid("닉네임"))
        assertNull(UserPostSearchCrawler.normalizeUid("a"))
    }

    @Test
    fun postUrlsPerGalleryKind() {
        assertEquals("https://gall.dcinside.com/mgallery/board/view/?id=g&no=5", gallery.copy(kind = GalleryKind.MINOR).pcPostUrl(5))
        assertEquals("https://m.dcinside.com/board/g/5", gallery.mobilePostUrl(5))
        assertEquals("https://m.dcinside.com/mini/g/5", gallery.copy(kind = GalleryKind.MINI).mobilePostUrl(5))
        assertEquals("dcapp://m.dcinside.com/view.php?id=g&no=5", gallery.dcAppUri(5))
        assertNull(gallery.copy(kind = GalleryKind.MINI).dcAppUri(5))
    }

    /** search_pos·page마다 정해 둔 페이지를 돌려주는 가짜 디시. 키에 없으면 마지막 페이지로 돌려보낸다(실제처럼). */
    private class FakeDc(private val pages: Map<Pair<String, Int>, String>, private val lastPage: Map<String, Int> = emptyMap(),
                         private val delayMillis: Long = 0) {
        val requests = AtomicInteger()
        private val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), ::maxOf)
            try { Thread.sleep(if (delayMillis > 0) delayMillis else 20) } finally { inFlight.decrementAndGet() }
            val url = chain.request().url
            val window = url.queryParameter("search_pos").orEmpty()
            val pageNo = url.queryParameter("page")?.toInt() ?: 1
            val served = pages[window to pageNo]
            val last = lastPage[window]
            val (request, body) = when {
                served != null -> chain.request() to served
                // 마지막 페이지 너머는 마지막 페이지로 돌려보낸다(실제처럼).
                last != null -> chain.request().newBuilder().url(url.newBuilder().setQueryParameter("page", last.toString()).build())
                    .build() to pages.getValue(window to last)
                // 정해 두지 않은 범위는 결과 0개인 정상 페이지(실제 디시도 그렇다).
                else -> chain.request() to emptySearchPage(window)
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
    }

    companion object {
        fun emptySearchPage(window: String) =
            """<html><body><table class="gall_list"><tbody></tbody></table><div class="bottom_paging_box iconpaging"><em>1</em></div></body></html>"""
    }

    private fun session(dc: FakeDc, kind: UserPostTargetKind = UserPostTargetKind.UID, value: String = "fila1") =
        UserPostSearchSession(UserPostSearchCrawler(dc.client), UserPostTarget(gallery, kind, value, "fila"))

    private fun fullPage(start: Long, matchAt: Set<Long> = emptySet()) = (0 until 20).map { i ->
        val no = start - i
        if (no in matchAt) row(no, "fila", uid = "fila1") else row(no, "fila", uid = "other$i")
    }

    @Test
    fun walksPagesAndWindowsAndStopsAtFirstFind(): Unit = runBlocking {
        val dc = FakeDc(mapOf(
            // 1범위: 2쪽. 1쪽엔 없고 2쪽(20개 미만)에 하나.
            ("" to 1) to page(fullPage(1000), "", 1, 2, "-200"),
            ("" to 2) to page(listOf(row(970, "fila", uid = "fila1"), row(969, "fila", uid = "x")), "", 2, 2, "-200"),
            // 2범위: 결과 없음(그래도 다음 검색 링크는 있다).
            ("-200" to 1) to page(emptyList(), "-200", 1, 1, "-100"),
            // 3범위: 마지막. 같은 사람 글 둘, 이미 본 글 하나가 다시 와도 한 번만.
            ("-100" to 1) to page(listOf(row(970, "fila", uid = "fila1"), row(500, "fila", uid = "fila1"), row(400, "fila", uid = "fila1")), "-100", 1, 1, null)
        ))
        val s = session(dc)
        val first = mutableListOf<FoundPost>()
        val firstRequests = s.loadMore(40) { first += it }
        assertEquals(listOf(970L), first.map { it.no })
        assertEquals(2, firstRequests) // 1쪽(없음) → 2쪽(찾음)에서 멈춘다
        assertFalse(s.exhausted)
        val progressAfterFirst = s.progress
        assertTrue(progressAfterFirst > 0f, "progress=$progressAfterFirst")

        val second = mutableListOf<FoundPost>()
        s.loadMore(40) { second += it }
        assertEquals(listOf(500L, 400L), second.map { it.no }) // 빈 범위는 건너뛰고, 이미 본 970은 다시 넣지 않는다
        assertTrue(s.exhausted)
        assertEquals(1f, s.progress)
        assertEquals(0, s.loadMore(40) { error("끝난 뒤에는 요청하지 않는다") })
    }

    @Test
    fun fetchesWindowsFiveAtATimeInOrderAndFollowsTheRealLink(): Unit = runBlocking {
        // 범위 간격이 1만인 갤러리. -30000 범위의 다음 링크만 간격이 달라(-15000) 미리 받은 -20000·-10000은 버려야 한다.
        val pages = mutableMapOf(
            ("" to 1) to page(listOf(row(9_000, "fila", uid = "x")), "", 1, 1, "-50000"),
            ("-50000" to 1) to page(emptyList(), "-50000", 1, 1, "-40000"),
            ("-40000" to 1) to page(listOf(row(7_000, "fila", uid = "x")), "-40000", 1, 1, "-30000"),
            ("-30000" to 1) to page(emptyList(), "-30000", 1, 1, "-15000"),
            ("-20000" to 1) to page(listOf(row(5_000, "fila", uid = "fila1")), "-20000", 1, 1, "-10000"),
            ("-10000" to 1) to page(emptyList(), "-10000", 1, 1, null),
            ("-15000" to 1) to page(listOf(row(4_000, "fila", uid = "fila1"), row(3_000, "fila", uid = "fila1")), "-15000", 1, 1, null)
        )
        val dc = FakeDc(pages)
        val s = session(dc)
        val found = mutableListOf<FoundPost>()
        s.loadMore(100) { found += it }
        // 첫 범위 1번 → -50000부터 5개 동시(-20000 결과는 순서가 어긋나 버린다) → 실제 링크 -15000에서 찾는다.
        assertEquals(listOf(4_000L, 3_000L), found.map { it.no })
        // 첫 범위 1 + (-50000~-10000) 5 + (-15000, -5000) 2. 실제 자리와 어긋난 결과(-20000·-10000·-5000)는 받아도 쓰지 않는다.
        assertEquals(1 + 5 + 2, dc.requests.get())
        assertTrue(dc.maxInFlight.get() in 2..5, "동시 ${dc.maxInFlight.get()}개")
        assertTrue(s.exhausted)
    }

    @Test
    fun checkedPostsGrowByWindowAndCoverAllAtTheEnd(): Unit = runBlocking {
        // 첫 범위의 다음 검색이 -30000이면 전체는 약 4만 개. 범위를 넘을 때마다 확인한 글 수가 1만씩 는다.
        val pages = mapOf(
            ("" to 1) to page(listOf(row(900, "fila", uid = "fila1")), "", 1, 1, "-30000"),
            ("-30000" to 1) to page(listOf(row(700, "fila", uid = "fila1")), "-30000", 1, 1, "-20000"),
            ("-20000" to 1) to page(emptyList(), "-20000", 1, 1, "-10000"),
            ("-10000" to 1) to page(listOf(row(100, "fila", uid = "fila1")), "-10000", 1, 1, null)
        )
        val s = session(FakeDc(pages))
        assertEquals(0L, s.checkedPosts)
        s.loadMore(100) {}
        assertEquals(10_000L, s.checkedPosts) // 첫 범위만 봤다
        // 남은 세 범위는 한 묶음으로 같이 받아, 받은 결과는 순서대로 모두 반영하고 끝에 닿는다.
        s.loadMore(100) {}
        assertTrue(s.exhausted)
        assertEquals(40_000L, s.checkedPosts)
    }

    @Test
    fun fetchesRemainingPagesOfABusyWindowTogether(): Unit = runBlocking {
        // 흔한 닉네임: 한 범위가 7쪽. 1쪽을 보고 나면 2~6쪽을 동시에, 그다음 7쪽을 받는다.
        val pages = (1..7).associate { p ->
            ("" to p) to page(if (p < 7) fullPage(10_000L - (p - 1) * 20) else listOf(row(1, "fila", uid = "fila1")), "", p, 7, null)
        }
        val dc = FakeDc(pages)
        val s = session(dc)
        val found = mutableListOf<FoundPost>()
        s.loadMore(100) { found += it }
        assertEquals(listOf(1L), found.map { it.no })
        assertEquals(7, dc.requests.get())
        assertEquals(5, dc.maxInFlight.get())
        assertTrue(s.exhausted)
    }

    @Test
    fun shortPageWithANextLinkKeepsGoingInTheSameWindow(): Unit = runBlocking {
        // 행 수(20개)가 아니라 다음 페이지 링크로 판단한다. 1쪽이 19개여도 2쪽까지 본다.
        val dc = FakeDc(mapOf(
            ("" to 1) to page(fullPage(1000).drop(1), "", 1, 2, null),
            ("" to 2) to page(listOf(row(900, "fila", uid = "fila1")), "", 2, 2, null)
        ))
        val found = mutableListOf<FoundPost>()
        session(dc).loadMore(100) { found += it }
        assertEquals(listOf(900L), found.map { it.no })
    }

    @Test
    fun pageWithoutAListIsAFailureNotTheEnd(): Unit = runBlocking {
        // 디시는 오류를 200과 알림 스크립트로 줄 때가 있다. 그걸 '처음 글까지 다 봤다'로 보면 뒤 범위를 영영 못 본다.
        val dc = FakeDc(mapOf(("" to 1) to """<script>alert('잠시 후 다시 시도해 주세요');history.back();</script>"""))
        val s = session(dc)
        assertFailsWith<UserPostSearchException> { s.loadMore(100) {} }
        assertFalse(s.exhausted)
        assertEquals(3, dc.requests.get()) // 세 번 시도하고 멈춘다
    }

    @Test
    fun detectsGalleriesThatHideWriters(): Unit = runBlocking {
        fun crawlerServing(html: String) = UserPostSearchCrawler(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(html.toResponseBody()).build()
        }.build())
        val hidden = page((0 until 5).map { row(100L - it, "식갤러") }, "", 1, 1, null)
        val shown = page(listOf(row(100, "ㅇㅇ", ip = "1.2"), row(99, "식갤러")), "", 1, 1, null)
        assertTrue(crawlerServing(hidden).hidesWriters(gallery))
        assertFalse(crawlerServing(shown).hidesWriters(gallery))
    }

    @Test
    fun redirectedPageEndsTheWindowButKeepsGoing(): Unit = runBlocking {
        // 1쪽이 20개라 2쪽을 달라고 했는데, 그새 글이 줄어 1쪽으로 돌려보낸다. 다음 범위로 넘어가야지 끝내면 안 된다.
        val dc = FakeDc(mapOf(
            ("" to 1) to page(fullPage(1000), "", 1, 2, "-100"),
            ("-100" to 1) to page(listOf(row(300, "fila", uid = "fila1")), "-100", 1, 1, null)
        ), lastPage = mapOf("" to 1))
        val s = session(dc)
        val found = mutableListOf<FoundPost>()
        s.loadMore(40) { found += it }
        assertEquals(listOf(300L), found.map { it.no })
        assertTrue(s.exhausted)
    }

    @Test
    fun ipTargetMatchesOnlyAnonymousPostsWithThatIp(): Unit = runBlocking {
        val dc = FakeDc(mapOf(("" to 1) to page(listOf(
            row(9, "ㅇㅇ", ip = "118.235"), row(8, "ㅇㅇ", ip = "118.23"), row(7, "ㅇㅇ", uid = "half1"), row(6, "ㅇㅇ", ip = "118.235")
        ), "", 1, 1, null)))
        val found = mutableListOf<FoundPost>()
        session(dc, UserPostTargetKind.IP, "118.235").loadMore(40) { found += it }
        assertEquals(listOf(9L, 6L), found.map { it.no })
    }

    @Test
    fun hiddenWritersAreReported(): Unit = runBlocking {
        val dc = FakeDc(mapOf(("" to 1) to page((0 until 20).map { row(100L - it, "ㅇㅇ") }, "", 1, 1, "-10")))
        val e = assertFailsWith<UserPostSearchException> { session(dc).loadMore(40) {} }
        assertTrue("숨겨서" in e.message.orEmpty())
    }

    @Test
    fun roundStopsAtRequestBudget(): Unit = runBlocking {
        // 못 찾는 빈 범위가 끝없이 이어져도 한 번에 정한 만큼만 요청한다.
        val pages = (0..60).associate { i ->
            val window = if (i == 0) "" else "-${10_000 - i * 100}"
            (window to 1) to page(emptyList(), window, 1, 1, "-${10_000 - (i + 1) * 100}")
        }
        val dc = FakeDc(pages)
        val s = session(dc)
        val requests = s.loadMore(5) {}
        assertEquals(5, requests)
        assertEquals(5, dc.requests.get())
        assertFalse(s.exhausted)
    }

    @Test
    fun cancelStopsImmediatelyEvenDuringASlowRequest(): Unit = runBlocking {
        val dc = FakeDc(mapOf(("" to 1) to page(fullPage(1000), "", 1, 2, "-1")), delayMillis = 3_000)
        val s = session(dc)
        val job = launch(Dispatchers.Default) { s.loadMore(40) {} }
        while (dc.requests.get() == 0) delay(5)
        val started = System.nanoTime()
        job.cancelAndJoin()
        val waitedMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(waitedMillis < 1_000, "취소에 ${waitedMillis}ms")
        assertEquals(1, dc.requests.get())
    }

    @Test
    fun gallogNicknameAndMissingUser(): Unit = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val missing = "nobody" in chain.request().url.encodedPath
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (missing) 404 else 200).message(if (missing) "Not Found" else "OK")
                .body((if (missing) "" else """<div class="gallog_top"><strong class="nick_name">아이똘</strong></div>""").toResponseBody()).build()
        }.build()
        val crawler = UserPostSearchCrawler(client)
        assertEquals("아이똘", crawler.gallogNickname("cling2258"))
        val e = assertFailsWith<UserPostSearchException> { crawler.gallogNickname("nobody") }
        assertTrue("식별코드" in e.message.orEmpty())
    }
}
