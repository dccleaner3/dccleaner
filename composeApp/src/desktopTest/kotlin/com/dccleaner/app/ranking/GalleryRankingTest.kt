package com.dccleaner.app.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class GalleryRankingTest {
    private val gallery = RankingGallery("test", GalleryKind.MINOR, "테스트 마이너 갤러리")
    private val day = LocalDate.of(2026, 10, 17)
    private fun at(h: Int, m: Int, date: LocalDate = day) = date.atTime(h, m)
    private fun post(no: Long, nick: String, uid: String, time: LocalDateTime, comments: Int = 0, ip: String = "") =
        RankingPost(no, nick, uid, ip, if (uid.isBlank()) NickType.ANON else NickType.HALF, time, comments)
    private fun comment(nick: String, uid: String, time: LocalDateTime, content: String = "ㅋㅋ", ip: String = "") =
        RankingComment(nick, uid, ip, if (uid.isBlank()) NickType.ANON else NickType.HALF, time, content)

    @Test
    fun mergesActivityWithinFiveMinutesLikeAutoM() {
        assertEquals(listOf(600 until 607), GalleryRankingAggregator.mergeBlocks(listOf(600, 603, 606)))
        // 606 → 끝 607, 613은 607+5 넘어서 새 구간
        assertEquals(listOf(600 until 607, 613 until 614), GalleryRankingAggregator.mergeBlocks(listOf(600, 606, 613)))
    }

    @Test
    fun guestbookIdsKeepEveryMemberOnceAndSkipAnonymousIps() {
        val posts = listOf(
            post(1, "철수", "chulsoo", at(10, 0)),
            post(2, "철수2", "chulsoo", at(11, 0)),         // 닉만 바꾼 같은 아이디 → 한 번만
            post(3, "지나가던", "", at(12, 0), ip = "5.6"), // 유동 IP는 받을 수 없다
            post(4, "영희", "younghee", at(13, 0))
        )
        val all = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day, posts, emptyList(),
            topN = Int.MAX_VALUE, genericNicks = emptySet())
        assertEquals(setOf("chulsoo", "younghee"), all.guestbookIds().toSet())
        assertEquals(2, all.guestbookIds().size)
    }

    @Test
    fun ranksByTimePostsAndCommentsAndSkipsGenericAnonymousNicks() {
        val posts = listOf(
            post(10, "철수", "chulsoo", at(10, 0), comments = 4),
            post(11, "철수", "chulsoo", at(10, 3), comments = 2),
            post(12, "영희", "younghee", at(20, 0)),
            post(13, "ㅇㅇ", "", at(21, 0), ip = "1.2"),           // 기본 닉 유동 → 제외
            post(14, "ㅇㅇ12", "", at(21, 5), ip = "1.3"),         // 기본 닉+숫자 → 제외
            post(15, "갤기본닉", "", at(21, 6), ip = "1.4"),       // 갤러리 기본 닉 → 제외
            post(16, "지나가던", "", at(22, 0), ip = "5.6"),       // 직접 정한 유동 닉은 센다
            post(17, "철수", "chulsoo", at(1, 0, day.plusDays(1))) // 기간 밖 → 제외
        )
        val comments = listOf(
            comment("영희", "younghee", at(20, 1)),
            comment("영희", "younghee", at(20, 2)),
            comment("영희", "younghee", at(20, 30)),
            comment("영희", "younghee", at(20, 33)),            // 20:00~20:03 + 20:30~20:34 = 7분
            comment("ㅇㅇ", "", at(20, 31), ip = "1.2")
        )
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day, posts, comments,
            topN = 10, genericNicks = setOf("갤기본닉"))

        assertEquals(listOf("영희(younghee)", "철수(chulsoo)", "지나가던(5.6)"), result.byTime.map { it.displayName })
        assertEquals(3, result.byPost.size) // 철수 2, 영희 1, 지나가던 1
        assertEquals("철수(chulsoo)", result.byPost.first().displayName)
        assertEquals(7, result.byTime.first().activityMinutes)
        assertEquals(listOf("영희(younghee)"), result.byComment.map { it.displayName })
        val chulsoo = result.byTime.single { it.nick == "철수" }
        assertEquals(4, chulsoo.activityMinutes) // 10:00~10:04
        assertEquals(3, chulsoo.avgCommentsPerPost) // (4+2)/2
        assertEquals(1, chulsoo.postRank)
        assertEquals(4, result.postCount)  // 철수 2, 영희 1, 지나가던 1
        assertEquals(4, result.commentCount)
    }

    @Test
    fun periodRankingKeepsTotalsAndNoTimeline() {
        val today = LocalDate.of(2026, 10, 18)
        val week = RankingPeriod.WEEK.range(RankingPeriod.WEEK.defaultAnchor(today), today)
        assertEquals(LocalDate.of(2026, 10, 5)..LocalDate.of(2026, 10, 11), week)
        val posts = (0 until 7).map { post(100L + it, "철수", "chulsoo", week.start.plusDays(it.toLong()).atTime(9, 0)) }
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.WEEK, week, posts, emptyList(),
            topN = 10, genericNicks = emptySet())
        val user = result.byTime.single()
        assertEquals(7, user.posts)
        assertEquals(7, user.activityMinutes) // 하루 1분씩 7일
        assertTrue(user.dayBlocks.isEmpty())
        assertNull(user.avgCommentsPerPost)
    }

    @Test
    fun activityAcrossMidnightCountsPerDay() {
        val week = day..day.plusDays(6)
        val posts = listOf(post(1, "철수", "chulsoo", at(23, 58)), post(2, "철수", "chulsoo", at(0, 1, day.plusDays(1))))
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.WEEK, week, posts, emptyList(),
            topN = 10, genericNicks = emptySet())
        // 이어 붙이면 23:58~00:02로 4분이 되지만, 날짜마다 따로 묶어 1분씩 2분이다.
        assertEquals(2, result.byTime.single().activityMinutes)
    }

    @Test
    fun activityMatchesPerDayMergeForEveryPeriod() {
        val random = kotlin.random.Random(7)
        for (days in listOf(1, 7, 31)) {
            val range = day..day.plusDays(days - 1L)
            val comments = List(5_000) {
                val u = random.nextInt(40)
                comment("닉$u", "id$u", range.start.atStartOfDay().plusMinutes(random.nextLong(days * 1440L)))
            }
            val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.MONTH, range, emptyList(), comments,
                topN = Int.MAX_VALUE, genericNicks = emptySet())
            // 바꾸기 전 계산(사람·날짜별로 분을 모아 따로 묶기)과 같아야 한다.
            val expected = comments.groupBy { it.nick }.mapValues { (_, cs) ->
                cs.groupBy { it.time.toLocalDate() }.values.sumOf { dayComments ->
                    GalleryRankingAggregator.mergeBlocks(dayComments.map { it.time.hour * 60 + it.time.minute })
                        .sumOf { it.last - it.first + 1 }
                }
            }
            assertEquals(expected, result.byTime.associate { it.nick to it.activityMinutes }, "${days}일")
            if (days == 1) {
                val blocks = result.byTime.associate { it.nick to it.dayBlocks }
                comments.groupBy { it.nick }.forEach { (nick, cs) ->
                    assertEquals(GalleryRankingAggregator.mergeBlocks(cs.map { it.time.hour * 60 + it.time.minute }), blocks[nick])
                }
            }
        }
    }

    @Test
    fun periodsSnapToDayWeekAndMonthAndStopAtToday() {
        val today = LocalDate.of(2026, 10, 18) // 일요일
        val wed = LocalDate.of(2026, 10, 14)
        assertEquals(wed..wed, RankingPeriod.DAY.range(wed, today))
        assertEquals(LocalDate.of(2026, 10, 12)..today, RankingPeriod.WEEK.range(wed, today))
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2026, 9, 30), RankingPeriod.MONTH.range(LocalDate.of(2026, 9, 20), today))
        assertEquals(LocalDate.of(2026, 10, 1)..today, RankingPeriod.MONTH.range(today, today))
        assertEquals(LocalDate.of(2026, 10, 17), RankingPeriod.DAY.defaultAnchor(today))
        assertEquals(LocalDate.of(2026, 9, 18), RankingPeriod.MONTH.defaultAnchor(today))
        assertEquals(LocalDate.of(2026, 10, 7), RankingPeriod.WEEK.shift(wed, -1))
        assertEquals(7, RankingPeriod.WEEK.range(LocalDate.of(2026, 10, 7), today).days)
    }

    @Test
    fun allowsTopNBeyondFifty() {
        val posts = (1..120).map { post(it.toLong(), "닉$it", "u$it", at(it / 60, it % 60)) }
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day, posts, emptyList(),
            topN = 100, genericNicks = emptySet())
        assertEquals(100, result.byTime.size)
    }

    @Test
    fun limitsEachListToTopN() {
        val posts = (1..30).map { post(it.toLong(), "닉$it", "u$it", at(10, it)) }
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day, posts, emptyList(),
            topN = 5, genericNicks = emptySet())
        assertEquals(5, result.byTime.size)
        assertEquals(5, result.byPost.size)
        assertTrue(result.byComment.isEmpty())
    }

    @Test
    fun parsesGalleryInputs() {
        val parse = GalleryRankingCrawler::parseGalleryInput
        assertEquals("github" to GalleryKind.MINOR, parse("https://gall.dcinside.com/mgallery/board/lists/?id=github"))
        assertEquals("programming" to null, parse("https://gall.dcinside.com/board/lists/?id=programming&page=2"))
        assertEquals("tt" to GalleryKind.MINI, parse("gall.dcinside.com/mini/board/lists/?id=tt"))
        assertEquals("github" to null, parse("https://m.dcinside.com/board/github"))
        assertEquals("tt" to GalleryKind.MINI, parse("https://m.dcinside.com/mini/tt"))
        assertEquals("baseball_new13" to null, parse(" baseball_new13 "))
        assertNull(parse("https://example.com/board/lists/?id=github"))
        assertNull(parse("갤러리"))
    }

    @Test
    fun parsesListRowsAndSkipsNotices() {
        val html = """
            <table class="gall_list"><tbody class="listwrap2">
            <tr class="ub-content us-post" data-no="70096" data-type="icon_notice"><td class="gall_num">70096</td>
              <td class="gall_writer ub-writer" data-nick="운영" data-uid="op" data-ip=""></td>
              <td class="gall_date" title="2024-09-14 15:12:10">24.09.14</td></tr>
            <tr class="ub-content us-post" data-no="88451" data-type="icon_txt"><td class="gall_num">88451</td>
              <td class="gall_tit"><a class="reply_numbox"><span class="reply_num">[12/3]</span></a></td>
              <td class="gall_writer ub-writer" data-nick="노트탭" data-uid="" data-ip="1.237"><em>노트탭</em></td>
              <td class="gall_date" title="2026-10-18 13:00:39">13:00</td></tr>
            <tr class="ub-content us-post" data-no="88450" data-type="icon_txt"><td class="gall_num">88450</td>
              <td class="gall_writer ub-writer" data-nick="고닉" data-uid="fixed1">
                <a class="writer_nikcon"><img src="https://nstatic.dcinside.com/dc/w/images/fix_nik.gif"></a></td>
              <td class="gall_date" title="2026-10-18 06:17:28">06:17</td></tr>
            </tbody></table>
        """.trimIndent()
        val posts = GalleryRankingCrawler.parseListPage(html)
        assertEquals(listOf(88451L, 88450L), posts.map { it.no })
        assertEquals(12, posts[0].commentCount)
        assertEquals("1.237", posts[0].ip)
        assertEquals(NickType.ANON, posts[0].nickType)
        assertEquals(NickType.FIXED, posts[1].nickType)
        assertEquals(LocalDateTime.of(2026, 10, 18, 6, 17, 28), posts[1].time)
        assertEquals("https://nstatic.dcinside.com/dc/w/images/fix_nik.gif", posts[1].iconUrl)
        assertEquals("", posts[0].iconUrl)
        assertEquals("", GalleryRankingCrawler.iconUrl("<img src='https://evil.example/x.gif'>")) // 디시 밖 주소는 안 받는다
    }

    @Test
    fun removesBumpedPostsFromTop() {
        fun p(no: Long) = post(no, "n", "u", at(10, 0))
        // 맨 위 50은 끌올된 옛 글
        assertEquals(listOf(105L, 104L, 103L), GalleryRankingCrawler.removeBumpedPosts(listOf(p(50), p(105), p(104), p(103))).map { it.no })
    }

    @Test
    fun parsesCommentsAndInfersYear() {
        val now = LocalDateTime.of(2026, 1, 2, 9, 0)
        val json = """{"comments":[
            {"name":"ㅇㅇ","user_id":"","ip":"118.235","reg_date":"01.02 08:52:32","memo":"하이","del_yn":"N","nicktype":"00","gallog_icon":""},
            {"name":"작년","user_id":"old","ip":"","reg_date":"12.31 23:59:00","memo":"끝","del_yn":"N","nicktype":"00","gallog_icon":"<img src='nik.gif'>"},
            {"name":"지움","user_id":"x","ip":"","reg_date":"01.02 08:00:00","memo":"","del_yn":"Y","nicktype":"00"},
            {"name":"댓글돌이","user_id":"","ip":"","reg_date":"01.02 08:00:00","memo":"","del_yn":"N","nicktype":"COMMENT_BOY"}
        ]}"""
        val comments = GalleryRankingCrawler.parseComments(json, now)
        assertEquals(listOf("ㅇㅇ", "작년"), comments.map { it.nick })
        assertEquals(LocalDateTime.of(2025, 12, 31, 23, 59), comments[1].time)
        assertEquals(NickType.HALF, comments[1].nickType)
        assertEquals("118.235", comments[0].ip)
        assertEquals("https://gall.dcinside.com/nik.gif", comments[1].iconUrl)
    }

    @Test
    fun commentsCarryTheirRequestOrderEvenWhenRepliesArriveOutOfOrder() {
        // 앞 요청일수록 늦게 답하는 가짜 서버. 댓글은 도착하는 대로 넘어오지만, 같이 넘어온 순서가 원래 요청 순서여야 한다.
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val form = chain.request().body as okhttp3.FormBody
            val field = { name: String -> (0 until form.size).first { form.name(it) == name }.let(form::value) }
            val no = field("no").toLong()
            val page = field("comment_page").toInt()
            Thread.sleep((no % 4) + if (page == 1) 3 else 0)
            val body = """{"comments":[""" + listOf("a", "b").joinToString(",") {
                """{"name":"n","user_id":"u","ip":"","reg_date":"2026.10.17 10:00:00","memo":"$no-$page-$it","del_yn":"N","nicktype":"00","gallog_icon":""}"""
            } + "]}"
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.Companion.run { body.toResponseBody() }).build()
        }.build()
        val targets = CommentTargets().apply { for (no in 1L..120L) add(no, 1_000) } // 글당 10페이지, 1200장
        val seen = java.util.Collections.synchronizedList(ArrayList<Pair<Long, String>>())
        kotlinx.coroutines.runBlocking {
            GalleryRankingCrawler(client).collectComments(gallery, targets, 120, { comment, order -> seen += order to comment.content }) {}
        }
        val expected = (1L..120L).flatMap { no -> (1..10).flatMap { page -> listOf("$no-$page-a", "$no-$page-b") } }
        assertTrue(seen.map { it.second } != expected, "가짜 서버가 순서를 섞지 못했다")
        assertEquals(expected.size, seen.map { it.first }.toSet().size, "순서가 겹친다")
        assertEquals(expected, seen.sortedBy { it.first }.map { it.second })
    }

    @Test
    fun commentCrawlStopsSoonAfterCancelOrFailure() {
        // 정상 페이지만 계속 오는 가짜 서버. 취소·실패 뒤에는 받던 요청과 막 보내려던 요청(일꾼마다 하나씩)만 나가고 멈춰야 한다.
        // 고치기 전에는 남은 수천 장을 끝까지 받았다.
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Thread.sleep(2)
            val body = """{"comments":[{"name":"n","user_id":"u","ip":"","reg_date":"2026.10.17 10:00:00","memo":"m","del_yn":"N","nicktype":"00","gallog_icon":""}]}"""
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.Companion.run { body.toResponseBody() }).build()
        }.build()
        val targets = CommentTargets().apply { for (no in 1L..500L) add(no, 1_000) } // 5000장
        val crawler = GalleryRankingCrawler(client)
        kotlinx.coroutines.runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { crawler.collectComments(gallery, targets, 500, { _, _ -> }) {} }
            while (requests.get() < 200) kotlinx.coroutines.delay(1)
            val atCancel = requests.get()
            job.cancelAndJoin()
            assertTrue(requests.get() - atCancel <= 120, "취소 뒤 ${requests.get() - atCancel}건 더 요청")
        }
        requests.set(0)
        val failure = runCatching {
            kotlinx.coroutines.runBlocking {
                crawler.collectComments(gallery, targets, 500, { _, _ -> if (requests.get() >= 200) error("집계 실패") }) {}
            }
        }.exceptionOrNull()
        assertEquals("집계 실패", failure?.message)
        assertTrue(requests.get() <= 400, "실패 뒤에도 ${requests.get()}건 요청")
    }

    @Test
    fun streamedCommentsInAnyArrivalOrderGiveTheInOrderResult() {
        // 동점 순위·첫 아이콘·말버릇 동점이 모두 생기게 만든다: 글 없는 사람들이 댓글 수가 같고, 댓글마다 아이콘이 다르며,
        // 하루짜리에서 'ㅋㅋ'와 'ㅇㅇ'를 6번씩 쓴 사람이 있다.
        val random = kotlin.random.Random(11)
        for (range in listOf(day..day, day..day.plusDays(6))) {
            val posts = (1..30L).map { no -> post(no, "글쓴이${no % 7}", "w${no % 7}", range.start.atTime(9, 0).plusMinutes(no * 37), comments = 3) }
            val comments = buildList {
                for (u in 0 until 12) repeat(4) {
                    add(RankingComment("댓글러$u", "c$u", "", NickType.HALF, range.start.atTime(10, 0).plusMinutes(random.nextLong(600)),
                        "말${random.nextInt(3)}", iconUrl = if (random.nextBoolean()) "" else "https://icon/$u/${random.nextInt(3)}"))
                }
                repeat(6) { add(comment("말버릇", "habit", range.start.atTime(12, it), "ㅋㅋ")) }
                repeat(6) { add(comment("말버릇", "habit", range.start.atTime(13, it), "ㅇㅇ")) }
                repeat(5) { add(comment("글쓴이1", "w1", range.start.atTime(14, it))) }
            }.shuffled(random)
            val inOrder = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.WEEK, range, posts, comments, Int.MAX_VALUE, emptySet())
            repeat(5) { round ->
                val streamed = GalleryRankingAggregator.Accumulator(range, emptySet()).apply {
                    posts.forEach(::addPost)
                    comments.withIndex().shuffled(kotlin.random.Random(round)).forEach { (i, c) -> addComment(c, i.toLong()) }
                }.result(gallery, RankingPeriod.WEEK, Int.MAX_VALUE)
                assertEquals(inOrder, streamed, "${range.start}~${range.endInclusive} round $round")
            }
            // 말버릇이 6:6 동점이면 원래 순서에서 먼저 나온 말을 고른다.
            if (range.start == range.endInclusive) assertEquals(comments.first { it.nick == "말버릇" }.content,
                inOrder.byTime.single { it.nick == "말버릇" }.topComment?.first)
        }
    }

    @Test
    fun textListsAllThreeSorts() {
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day,
            listOf(post(1, "철수", "chulsoo", at(10, 0))), listOf(comment("영희", "younghee", at(11, 0))),
            topN = 10, genericNicks = emptySet())
        val text = result.toText()
        assertTrue(text.startsWith("테스트 마이너 갤러리 10월 17일 갤창 리스트"))
        assertTrue("[시간순]" in text && "[글순]" in text && "[댓글순]" in text)
        assertTrue("1. 철수(chulsoo) — 글 1개" in text)
    }
}
