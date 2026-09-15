package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors

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
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    val activeOptions = buildList {
        if (commentContentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.COMMENT_CONTENT_REGEX, commentContentRegex))
        if (dateFilterEnabled) add(DeleteFilterOption(DeleteFilterType.AGE_DAYS, minPostAgeDaysToDelete.ifBlank { "5" }))
    }

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = uiColors.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("댓글 삭제 조건", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            BooleanDeleteOption(
                uiColors, "내 글 필터", "내가 작성한 글에 단 댓글만 삭제합니다.",
                myPostFilterEnabled, onMyPostFilterEnabledChange
            )
            Spacer(Modifier.height(12.dp))
            BooleanDeleteOption(
                uiColors, "디시콘만 삭제", "디시콘(이미지) 댓글만 삭제합니다.",
                dcconOnlyFilterEnabled, onDcconOnlyFilterEnabledChange
            )
            Spacer(Modifier.height(16.dp))
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
                            onCommentContentRegexChange(value); onCommentContentFilterEnabledChange(true)
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
            Spacer(Modifier.height(20.dp))
            NewestFirstOption(uiColors, "최근 댓글부터 삭제", deleteNewestFirst, onDeleteNewestFirstChange)
            Spacer(Modifier.height(20.dp))
            DeleteSpeedSummary(uiColors, if (myPostFilterEnabled) "900" else "1,800", myPostFilterEnabled)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = uiColors.outline)
            Spacer(Modifier.height(20.dp))
            DeleteStartControls(
                uiColors,
                recordGuestbookLog,
                onRecordGuestbookLogChange,
                onShowDeleteDialog,
                onOpenProxyCleaner
            )
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
            "1차 필터 · 삭제 후보\n${firstStage.joinToString(" 그리고 ")}\n\n" +
                "2차 필터 · 최종 대상\n${secondStage.joinToString(" 또는 ")}\n\n" +
                "두 필터를 모두 통과한 댓글만 삭제합니다."
        firstStage.isNotEmpty() -> "삭제 필터\n${firstStage.joinToString(" 그리고 ")}"
        secondStage.isNotEmpty() -> "삭제 필터\n${secondStage.joinToString(" 또는 ")}"
        else -> null
    }
}

@Composable
private fun BooleanDeleteOption(
    uiColors: UiColors,
    title: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = uiColors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Medium)
        }
        Switch(enabled, onEnabledChange, colors = dccleanerSwitchColors(uiColors))
    }
    if (enabled) DeleteOptionDescription(description)
}
