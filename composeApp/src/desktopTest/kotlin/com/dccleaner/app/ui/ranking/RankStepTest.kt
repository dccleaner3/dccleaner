package com.dccleaner.app.ui.ranking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RankStepTest {
    @Test
    fun stepsUpThroughCommonCountsThenByHundreds() {
        assertEquals(listOf(5, 10, 20, 30, 50, 100, 200, 300), generateSequence(3, ::nextRankStep).drop(1).take(8).toList())
        assertEquals(50, nextRankStep(37)) // 직접 넣은 값은 가까운 위 단계로
        assertEquals(200, nextRankStep(150))
    }

    @Test
    fun stepsDownToTheNearestLowerStepAndStopsAtFive() {
        assertEquals(listOf(200, 100, 50, 30, 20, 10, 5), generateSequence(300, ::previousRankStep).drop(1).toList())
        assertEquals(30, previousRankStep(37))
        assertEquals(50, previousRankStep(73)) // 집계 인원이 73명이면 73 → 50
        assertEquals(100, previousRankStep(101))
        assertNull(previousRankStep(5))
        assertNull(previousRankStep(3))
    }
}
