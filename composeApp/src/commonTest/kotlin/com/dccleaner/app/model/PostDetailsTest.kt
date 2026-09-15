package com.dccleaner.app.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostDetailsTest {
    @Test
    fun requiresViewCountOnlyWhenViewFilterIsEnabled() {
        val details = PostDetails(recommendCount = 1, commentCount = 2, viewCount = null)

        assertTrue(details.hasCountsRequiredBy(true, true, false))
        assertFalse(details.hasCountsRequiredBy(false, false, true))
    }

    @Test
    fun acceptsAllAvailableCounts() {
        val details = PostDetails(recommendCount = 1, commentCount = 2, viewCount = 15)

        assertTrue(details.hasCountsRequiredBy(true, true, true))
    }
}
