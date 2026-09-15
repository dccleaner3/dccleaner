package com.dccleaner.app.storage

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class GuestbookSentUserCacheTest {
    @Test
    fun cacheBatchTextWritesOneCompletedLinePerUser() {
        assertEquals(
            "user1\nuser2\n",
            cacheBatchText(listOf(" user1 ", "", "user2"))
        )
    }

    @Test
    fun completeCacheLinesDropsPartialLastLineAndDuplicates() {
        assertEquals(
            setOf("user1", "user2"),
            completeCacheLines("user1\nuser2\nuser1\npartial")
        )
    }

    @Test
    fun completeCacheLinesKeepsLastLineWhenBatchIsComplete() {
        assertEquals(
            setOf("user1", "user2"),
            completeCacheLines("user1\nuser2\n")
        )
    }

    @Test
    fun truncatePartialLastLineRepairsFileBeforeNextAppend() {
        val file = File.createTempFile("guestbook-cache", ".txt")
        try {
            file.writeText("user1\nuser2\npartial")

            truncatePartialLastLine(file)
            file.appendText("user3\n")

            assertEquals("user1\nuser2\nuser3\n", file.readText())
        } finally {
            file.delete()
        }
    }
}
