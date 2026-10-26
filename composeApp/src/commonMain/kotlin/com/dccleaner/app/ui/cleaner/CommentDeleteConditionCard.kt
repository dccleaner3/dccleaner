package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors

@Composable
fun CommentDeleteConditionCard(
    uiColors: UiColors,
    myPostFilterEnabled: Boolean,
    onMyPostFilterEnabledChange: (Boolean) -> Unit,
    dcconOnlyFilterEnabled: Boolean,
    onDcconOnlyFilterEnabledChange: (Boolean) -> Unit,
    commentContentFilterEnabled: Boolean,
    onCommentContentFilterEnabledChange: (Boolean) -> Unit,
    commentContentRegex: String,
    onCommentContentRegexChange: (String) -> Unit,
    dateFilterEnabled: Boolean,
    onDateFilterEnabledChange: (Boolean) -> Unit,
    deleteNewestFirst: Boolean,
    onDeleteNewestFirstChange: (Boolean) -> Unit,
    minPostAgeDaysToDelete: String,
    onMinPostAgeDaysToDeleteChange: (String) -> Unit,
    proxySpeedPerHour: Int? = null,
    runOnServer: Boolean = false,
    onRequireLocal: (apply: () -> Unit) -> Unit = { it() }
) {
    val activeOptions = buildList {
        if (commentContentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.COMMENT_CONTENT_REGEX, commentContentRegex))
        if (dateFilterEnabled) add(DeleteFilterOption(DeleteFilterType.AGE_DAYS, minPostAgeDaysToDelete.ifBlank { "5" }))
    }

    // 삭제 설정 시트 안에 놓이므로 카드 바탕·옆 여백 없이 시트의 갤러리 버튼과 줄을 맞춘다.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            DeleteFilterOptionEditor(
                uiColors = uiColors,
                allowedTypes = listOf(DeleteFilterType.COMMENT_CONTENT_REGEX, DeleteFilterType.AGE_DAYS),
                activeOptions = activeOptions,
                summary = commentDeleteConditionSummary(
                    activeOptions,
                    myPostFilterEnabled,
                    dcconOnlyFilterEnabled
                ),
                onApply = { type, value ->
                    when (type) {
                        DeleteFilterType.COMMENT_CONTENT_REGEX -> {
                            val apply = { onCommentContentRegexChange(value); onCommentContentFilterEnabledChange(true) }
                            if (runOnServer) onRequireLocal(apply) else apply()
                        }
                        DeleteFilterType.AGE_DAYS -> {
                            onMinPostAgeDaysToDeleteChange(value); onDateFilterEnabledChange(true)
                        }
                        else -> Unit
                    }
                },
                onRemove = { type ->
                    when (type) {
                        DeleteFilterType.COMMENT_CONTENT_REGEX -> onCommentContentFilterEnabledChange(false)
                        DeleteFilterType.AGE_DAYS -> onDateFilterEnabledChange(false)
                        else -> Unit
                    }
                }
            )
            DeleteSectionDivider(uiColors)
            AdvancedOptions(listOf(myPostFilterEnabled, dcconOnlyFilterEnabled, deleteNewestFirst).count { it }) {
                DeleteSwitchRow(
                    uiColors, "내 글에 단 댓글만", "내가 쓴 글 아래 댓글만 지워요", myPostFilterEnabled, onMyPostFilterEnabledChange
                )
                DeleteSwitchRow(
                    uiColors, "디시콘 댓글만", "디시콘(이미지) 댓글만 지워요", dcconOnlyFilterEnabled, onDcconOnlyFilterEnabledChange
                )
                DeleteSwitchRow(
                    uiColors, "최근 댓글부터 삭제", "최신 댓글부터 한 페이지씩 지워요", deleteNewestFirst
                ) { enabled ->
                    if (runOnServer && enabled) onRequireLocal { onDeleteNewestFirstChange(true) } else onDeleteNewestFirstChange(enabled)
                }
            }
            DeleteSectionDivider(uiColors)
            // The server is halved by the same filter that halves a local run.
            DeleteSpeedSummary(uiColors, myPostFilterEnabled, proxySpeedPerHour)
        }
    }
}

internal fun commentDeleteConditionSummary(
    options: List<DeleteFilterOption>,
    myPostFilterEnabled: Boolean,
    dcconOnlyFilterEnabled: Boolean
): String? {
    val age = options.firstOrNull { it.type == DeleteFilterType.AGE_DAYS }?.value
    val regex = options.firstOrNull { it.type == DeleteFilterType.COMMENT_CONTENT_REGEX }?.value
    val firstStage = buildList {
        if (age != null) add("작성 후 ${age}일 이상")
        if (regex != null) add("내용이 ‘$regex’ 정규식과 일치")
    }
    val secondStage = buildList {
        if (myPostFilterEnabled) add("내 글에 단 댓글")
        if (dcconOnlyFilterEnabled) add("디시콘 댓글")
    }
    return when {
        firstStage.isNotEmpty() && secondStage.isNotEmpty() ->
            "먼저 고르는 조건\n${firstStage.joinToString(" 그리고 ")}\n\n" +
                "그중에서 지울 것\n${secondStage.joinToString(" 또는 ")}\n\n" +
                "두 조건을 모두 만족하는 댓글만 지워요"
        firstStage.isNotEmpty() -> "지울 조건\n${firstStage.joinToString(" 그리고 ")}"
        secondStage.isNotEmpty() -> "지울 조건\n${secondStage.joinToString(" 또는 ")}"
        else -> null
    }
}
