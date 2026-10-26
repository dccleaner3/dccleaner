package com.dccleaner.app.ranking

import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GalleryRankingSeekTest {
    private val now = LocalDateTime.of(2026, 10, 18, 22, 0)

    /** 최신 글부터 거꾸로. 새벽엔 뜸하고, 2년 넘은 글은 훨씬 뜸하다. */
    private fun gallery(size: Int, seed: Int = 1): List<LocalDateTime> {
        val random = Random(seed)
        var t = now
        return List(size) {
            val night = if (t.hour in 2..7) 6.0 else 1.0
            val old = if (t.isBefore(now.minusYears(2))) 4.0 else 1.0
            t = t.minusSeconds((60 * night * old * random.nextDouble(0.3, 1.7)).toLong())
            t
        }
    }

    // drop/take는 매번 목록을 복사해 40만 개 목록에서 수십 초가 걸린다. 복사 없이 잘라 본다.
    private fun page(times: List<LocalDateTime>, p: Int) =
        SeekPage.of(p, times.subList(minOf((p - 1) * 100, times.size), minOf(p * 100, times.size)))

    /** 끝(end)보다 옛 글이 처음 보이는 페이지를 하나씩 넘겨 찾는다. 없으면 첫 빈 페이지. */
    private fun expected(times: List<LocalDateTime>, end: LocalDateTime) =
        generateSequence(1) { it + 1 }.first { page(times, it).reaches(end) }

    private fun seek(times: List<LocalDateTime>, end: LocalDateTime): Pair<Int, Int> {
        var probes = 0
        val found = runBlocking {
            GalleryRankingCrawler.seekStartPage(page(times, 1), end) { probes++; page(times, it) }
        }
        return found to probes
    }

    @Test
    fun findsStartPageInFewProbes() {
        val probeCounts = (1..5).flatMap { seed ->
            val times = gallery(400_000, seed)
            listOf(1L, 2, 3, 7, 10, 30, 45, 90, 200, 300, 500)
                .map { now.toLocalDate().minusDays(it).atStartOfDay() }
                .filter { it.isAfter(times.last()) }
                .map { end ->
                    val (found, probes) = seek(times, end)
                    assertEquals(expected(times, end), found, "seed $seed, $end")
                    probes
                }
        }
        println("시작 페이지 탐색 ${probeCounts.size}건: 평균 ${"%.1f".format(probeCounts.average())}, 최대 ${probeCounts.max()}번")
        assertTrue(probeCounts.max() <= 8, "탐색 ${probeCounts.max()}번")
    }

    @Test
    fun rangeCoveringTodayStartsAtFirstPage() {
        val (found, probes) = seek(gallery(5_000), now.plusHours(3))
        assertEquals(1 to 0, found to probes)
    }

    @Test
    fun rangeOlderThanGalleryStopsAtFirstEmptyPage() {
        for (size in listOf(12_345, 400_000)) {
            val times = gallery(size)
            val (found, probes) = seek(times, times.last().minusDays(30))
            assertEquals((size + 99) / 100 + 1, found) // 마지막 글이 있는 페이지 다음
            println("갤러리보다 옛 기간 ${size}개: ${probes}번")
            assertTrue(probes <= 20, "탐색 ${probes}번")
        }
    }

    @Test
    fun emptyGalleryStartsAtFirstPage() {
        assertEquals(1 to 0, seek(emptyList(), now.minusDays(10)))
    }
}
