package com.dccleaner.app.ui.cleaner

import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteFilterOptionEditorTest {
    @Test
    fun postSummaryShowsTwoDeleteStages() {
        assertEquals(
            "1차 필터 · 삭제 후보\n작성 후 12일 이상 그리고 제목이 ‘test’ 정규식과 일치\n\n" +
                "2차 필터 · 최종 대상\n추천수 10개 미만 그리고 댓글수 20개 미만\n\n" +
                "두 필터를 모두 통과한 글만 삭제합니다.",
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
            "삭제 필터\n작성 후 12일 이상",
            postDeleteConditionSummary(
                listOf(DeleteFilterOption(DeleteFilterType.AGE_DAYS, "12"))
            )
        )
        assertEquals(
            "1차 필터 · 삭제 후보\n작성 후 12일 이상 그리고 내용이 ‘spam’ 정규식과 일치\n\n" +
                "2차 필터 · 최종 대상\n내 글에 단 댓글 또는 디시콘 댓글\n\n" +
                "두 필터를 모두 통과한 댓글만 삭제합니다.",
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
