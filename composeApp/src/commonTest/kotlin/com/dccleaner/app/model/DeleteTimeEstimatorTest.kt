package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteTimeEstimatorTest {
    @Test
    fun estimatesActualDeletedItemsPerHour() {
        assertEquals(1_900, DeleteTimeEstimator.estimateHourlyDeletionRate(36_000, 20, 39))
        assertEquals(0, DeleteTimeEstimator.estimateHourlyDeletionRate(36_000, 20, 20))
        assertEquals(3_600, DeleteTimeEstimator.estimateHourlyDeletionRate(10_000, 0, 10, 90))
        assertEquals(1_800, DeleteTimeEstimator.estimateRemainingSeconds(1_800, 50, 50, 1_000))
    }
}
