package com.dccleaner.app.model

object DeleteTimeEstimator {
    fun estimateHourlyDeletionRate(
        elapsedMillis: Long,
        initialDeletedCount: Int,
        deletedCount: Int,
        skippedCount: Int = 0
    ): Int {
        val measuredDeleted = (deletedCount - initialDeletedCount).coerceAtLeast(0)
        val processedCount = measuredDeleted + skippedCount.coerceAtLeast(0)
        val adjustedElapsedMillis = elapsedMillis + skippedCount.coerceAtLeast(0) * 1_000L
        if (adjustedElapsedMillis <= 0L || processedCount == 0) return 0
        return (processedCount * 3_600_000.0 / adjustedElapsedMillis).toInt().coerceAtLeast(0)
    }

    fun estimateRemainingSeconds(
        deletionsPerHour: Int,
        deletedCount: Int,
        skippedCount: Int,
        totalCount: Int
    ): Long {
        val remainingCount = totalCount - deletedCount - skippedCount
        if (remainingCount <= 0 || deletionsPerHour <= 0) return 0L
        return (remainingCount * 3_600L + deletionsPerHour - 1) / deletionsPerHour
    }
}
