package com.dccleaner.app.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DeletionPreviewerTest {
    @Test
    fun validatesDeletionRegexBeforeInspection() {
        assertNotNull("/test/i".toDeletionRegex(enabled = true, label = "글 제목"))
        assertFailsWith<IllegalArgumentException> {
            "[".toDeletionRegex(enabled = true, label = "글 제목")
        }
    }
}
