package com.dccleaner.app.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentAutoCleanerTest {
    @Test
    fun matchesAnyKeywordIgnoringCaseAndBlanks() {
        assertTrue(commentContainsBlockedKeyword("광고 TeSt 댓글", listOf("", "test")))
        assertFalse(commentContainsBlockedKeyword("일반 댓글", listOf("광고", "도배")))
    }

    @Test
    fun parsesVisibleCommentsAndSkipsDeletedComments() {
        val page = parseMonitoredCommentPage(
            """{"total_cnt":"101","comments":[{"no":"7","memo":"광고<br>댓글","del_yn":"N"},{"no":"8","memo":"삭제됨","del_yn":"Y"}]}"""
        )

        assertTrue(page != null && page.totalCount == 101)
        assertTrue(page.comments.single() == MonitoredPostComment("7", "광고 댓글"))
    }
}
