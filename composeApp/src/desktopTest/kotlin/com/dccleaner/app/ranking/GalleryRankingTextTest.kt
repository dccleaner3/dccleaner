package com.dccleaner.app.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalleryRankingTextTest {
    private val gallery = RankingGallery("test", GalleryKind.MINOR, "테스트 마이너 갤러리")
    private val day = LocalDate.of(2026, 10, 17)
    private val week = LocalDate.of(2026, 10, 5)..LocalDate.of(2026, 10, 11)
    private fun post(no: Long, nick: String, uid: String, time: LocalDateTime) =
        RankingPost(no, nick, uid, "", NickType.HALF, time, 0)
    private fun comment(nick: String, uid: String, time: LocalDateTime) =
        RankingComment(nick, uid, "", NickType.HALF, time, "ㅋㅋ")

    /** 영희: 7일 동안 하루 글 1개·댓글 10개, 매일 10분. 철수: 4일 동안 글 1개씩, 하루 1분. */
    private val weekResult = run {
        val dates = (0 until 7).map { week.start.plusDays(it.toLong()) }
        val posts = dates.mapIndexed { i, d -> post(100L + i, "영희", "younghee", d.atTime(10, 0)) } +
            dates.take(4).mapIndexed { i, d -> post(200L + i, "철수", "chulsoo", d.atTime(9, 0)) }
        val comments = dates.flatMap { d -> (0 until 10).map { comment("영희", "younghee", d.atTime(10, it)) } }
        GalleryRankingAggregator.aggregate(gallery, RankingPeriod.WEEK, week, posts, comments,
            topN = 10, genericNicks = emptySet())
    }

    private fun section(text: String, sort: RankingSort): String =
        text.substringAfter("[${sort.label}]").substringBefore("\n\n[")

    @Test
    fun metricOnlySectionsShowOnlyTheirOwnMetric() {
        val text = weekResult.toText(metricOnly = true)
        assertEquals("1. 영희(younghee) — 1시간 10분\n2. 철수(chulsoo) — 4분", section(text, RankingSort.TIME).trim())
        assertEquals("1. 영희(younghee) — 글 7개\n2. 철수(chulsoo) — 글 4개", section(text, RankingSort.POST).trim())
        assertEquals("1. 영희(younghee) — 댓글 70개", section(text, RankingSort.COMMENT).trim())
    }

    @Test
    fun periodShowsTotalsByDefault() {
        val text = weekResult.toText()
        assertFalse("하루 평균" in text)
        assertTrue("1. 영희(younghee) — 1시간 10분 · 글 7개 · 댓글 70개" in text)
        assertTrue("2. 철수(chulsoo) — 글 4개 · 4분 · 댓글 0개" in text)
    }

    @Test
    fun periodPerDayShowsAveragesWithDecimalRule() {
        val text = weekResult.toText(perDay = true)
        assertTrue(text.lines()[1].endsWith("집계 · 하루 평균"))
        assertTrue("1. 영희(younghee) — 10분 · 글 1개 · 댓글 10개" in text)   // 7/7 → .0 뗌, 70/7 = 10 → 정수
        assertTrue("2. 철수(chulsoo) — 1분 미만 · 글 0.6개 · 댓글 0개" in text) // 4/7 ≈ 0.57
        val chulsoo = weekResult.byTime.single { it.nick == "철수" }
        assertEquals("0.6개", weekResult.metricValue(chulsoo, RankingSort.POST, perDay = true))
        assertEquals("4개", weekResult.metricValue(chulsoo, RankingSort.POST, perDay = false))
        assertEquals("0개", weekResult.metricValue(chulsoo, RankingSort.COMMENT, perDay = true))
    }

    @Test
    fun singleDayIgnoresPerDay() {
        val result = GalleryRankingAggregator.aggregate(gallery, RankingPeriod.DAY, day..day,
            listOf(post(1, "철수", "chulsoo", day.atTime(10, 0))), listOf(comment("영희", "younghee", day.atTime(11, 0))),
            topN = 10, genericNicks = emptySet())
        assertEquals(result.toText(), result.toText(perDay = true))
        assertEquals(result.toText(metricOnly = true), result.toText(perDay = true, metricOnly = true))
        assertTrue("1. 철수(chulsoo) — 글 1개 · 1분 · 댓글 0개" in result.toText())
    }
}
