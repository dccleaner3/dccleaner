package com.dccleaner.app.ui.cleaner

import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteFilterOptionEditorTest {
    @Test
    fun postSummaryShowsTwoDeleteStages() {
        assertEquals(
            "먼저 고르는 조건\n작성 후 12일 이상 그리고 제목이 ‘test’ 정규식과 일치\n\n" +
                "그중에서 지울 것\n추천수 10개 미만 그리고 댓글수 20개 미만\n\n" +
                "두 조건을 모두 만족하는 글만 지워요",
            postDeleteConditionSummary(
                listOf(
                    DeleteFilterOption(DeleteFilterType.RECOMMEND, "10"),
                    DeleteFilterOption(DeleteFilterType.COMMENT_COUNT, "20"),
                    DeleteFilterOption(DeleteFilterType.POST_TITLE_REGEX, "test"),
                    DeleteFilterOption(DeleteFilterType.AGE_DAYS, "12")
                )
            )
        )
        assertEquals(
            "지울 조건\n작성 후 12일 이상",
            postDeleteConditionSummary(
                listOf(DeleteFilterOption(DeleteFilterType.AGE_DAYS, "12"))
            )
        )
        assertEquals(
            "먼저 고르는 조건\n작성 후 12일 이상 그리고 내용이 ‘spam’ 정규식과 일치\n\n" +
                "그중에서 지울 것\n내 글에 단 댓글 또는 디시콘 댓글\n\n" +
                "두 조건을 모두 만족하는 댓글만 지워요",
            commentDeleteConditionSummary(
                listOf(
                    DeleteFilterOption(DeleteFilterType.COMMENT_CONTENT_REGEX, "spam"),
                    DeleteFilterOption(DeleteFilterType.AGE_DAYS, "12")
                ),
                myPostFilterEnabled = true,
                dcconOnlyFilterEnabled = true
            )
        )
    }
}
