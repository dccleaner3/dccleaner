package com.dccleaner.app.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommentAutoCleanerTest {
    @Test
    fun matchesAnyKeywordIgnoringCaseAndBlanks() {
        assertTrue(commentContainsBlockedKeyword("광고 TeSt 댓글", listOf("", "test")))
        assertFalse(commentContainsBlockedKeyword("일반 댓글", listOf("광고", "도배")))
    }

    @Test
    fun matchesMultipleRegexRulesWithoutChangingLiteralKeywords() {
        val patterns = commentCleanerPatterns(listOf("광고", "정규식:홍보\\d+", "정규식:도배|스팸"))
        assertTrue(commentContainsBlockedKeyword("홍보123 댓글", listOf("광고"), patterns))
        assertTrue(commentContainsBlockedKeyword("SPAM 댓글", emptyList(), commentCleanerPatterns(listOf("정규식:spam"))))
        assertFalse(commentContainsBlockedKeyword("광고123 댓글", listOf("광고\\d+"), emptyList()))
        assertFailsWith<IllegalArgumentException> { commentCleanerPatterns(listOf("정규식:")) }
        assertFailsWith<IllegalArgumentException> { commentCleanerPatterns(listOf("정규식:[")) }
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
