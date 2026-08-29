package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaewangconProgressTest {
    @Test
    fun calculatesOnlyMissingRequirements() {
        val progress = DaewangconProgress(postCount = 3, commentCount = 27)

        assertEquals(7, progress.remainingPostCount)
        assertEquals(0, progress.remainingCommentCount)
        assertFalse(progress.requirementsMet)
    }

    @Test
    fun usesServerProvidedRequirements() {
        val progress = DaewangconProgress(
            postCount = 12,
            commentCount = 24,
            requiredPostCount = 15,
            requiredCommentCount = 30,
            durationHours = 72,
            status = "disabled"
        )

        assertEquals(3, progress.remainingPostCount)
        assertEquals(6, progress.remainingCommentCount)
        assertFalse(progress.requirementsMet)
    }

    @Test
    fun clampsRemainingRequirementsAtZero() {
        val progress = DaewangconProgress(postCount = 15, commentCount = 25)

        assertEquals(0, progress.remainingPostCount)
        assertEquals(0, progress.remainingCommentCount)
        assertTrue(progress.requirementsMet)
    }
}
