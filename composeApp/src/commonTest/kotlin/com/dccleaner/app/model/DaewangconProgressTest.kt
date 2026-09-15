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
    fun acceptsServerProvidedRequirements() {
        val progress = DaewangconProgress(
            postCount = 15,
            commentCount = 30,
            requiredPostCount = 15,
            requiredCommentCount = 30
        )

        assertTrue(progress.requirementsMet)
    }

    @Test
    fun detectsEnabledStatusIgnoringCase() {
        assertTrue(DaewangconProgress(0, 0, status = "ENABLED").isEnabled)
        assertFalse(DaewangconProgress(0, 0, status = "disabled").isEnabled)
    }
}
