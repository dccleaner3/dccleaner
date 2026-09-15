package com.dccleaner.app.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DaewangconProgressParsingTest {
    @Test
    fun parsesServerCountsAndRequirements() {
        val progress = parseDaewangconProgress(
            """
            {
              "bigcon": {
                "article": "7",
                "comment": 18,
                "status": "enabled",
                "config": { "article": "10", "comment": 20, "hours": "24" }
              }
            }
            """.trimIndent()
        )

        requireNotNull(progress)
        assertEquals(7, progress.postCount)
        assertEquals(18, progress.commentCount)
        assertEquals(10, progress.requiredPostCount)
        assertEquals(20, progress.requiredCommentCount)
        assertEquals(24, progress.durationHours)
        assertEquals("enabled", progress.status)
    }

    @Test
    fun treatsMissingCountsAsZeroWhenAlreadyEnabled() {
        val progress = parseDaewangconProgress(
            """
            {
              "bigcon": {
                "status": "enabled",
                "expire": "1788437477",
                "config": { "article": 10, "comment": 20, "hours": 72 }
              }
            }
            """.trimIndent()
        )

        requireNotNull(progress)
        assertEquals(0, progress.postCount)
        assertEquals(0, progress.commentCount)
    }

    @Test
    fun rejectsMissingOrInvalidRequirements() {
        assertNull(parseDaewangconProgress("""{"bigcon":{"article":1,"comment":2}}"""))
        assertNull(
            parseDaewangconProgress(
                """{"bigcon":{"article":1,"comment":2,"config":{"article":0,"comment":20}}}"""
            )
        )
    }
}
