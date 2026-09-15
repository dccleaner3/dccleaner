package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.SwapVert
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
fun PostDeleteConditionCard(
    uiColors: UiColors,
    minRecommendToKeep: String,
    onMinRecommendToKeepChange: (String) -> Unit,
    minCommentToKeep: String,
    onMinCommentToKeepChange: (String) -> Unit,
    minViewToKeep: String,
    onMinViewToKeepChange: (String) -> Unit,
    recommendFilterEnabled: Boolean,
    onRecommendFilterEnabledChange: (Boolean) -> Unit,
    commentFilterEnabled: Boolean,
    onCommentFilterEnabledChange: (Boolean) -> Unit,
    viewFilterEnabled: Boolean,
    onViewFilterEnabledChange: (Boolean) -> Unit,
    postContentFilterEnabled: Boolean,
    onPostContentFilterEnabledChange: (Boolean) -> Unit,
    postContentRegex: String,
    onPostContentRegexChange: (String) -> Unit,
    dateFilterEnabled: Boolean,
    onDateFilterEnabledChange: (Boolean) -> Unit,
    deleteNewestFirst: Boolean,
    onDeleteNewestFirstChange: (Boolean) -> Unit,
    deleteQuestionPosts: Boolean,
    onDeleteQuestionPostsChange: (Boolean) -> Unit,
    minPostAgeDaysToDelete: String,
    onMinPostAgeDaysToDeleteChange: (String) -> Unit,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    val activeOptions = buildList {
        if (recommendFilterEnabled) add(DeleteFilterOption(DeleteFilterType.RECOMMEND, minRecommendToKeep.ifBlank { "1" }))
        if (commentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.COMMENT_COUNT, minCommentToKeep.ifBlank { "1" }))
        if (viewFilterEnabled) add(DeleteFilterOption(DeleteFilterType.VIEW_COUNT, minViewToKeep.ifBlank { "1" }))
        if (postContentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.POST_TITLE_REGEX, postContentRegex))
        if (dateFilterEnabled) add(DeleteFilterOption(DeleteFilterType.AGE_DAYS, minPostAgeDaysToDelete.ifBlank { "5" }))
    }
    val usesSlowFilter = recommendFilterEnabled || commentFilterEnabled || viewFilterEnabled

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
                Text("글 삭제 조건", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            DeleteFilterOptionEditor(
                uiColors = uiColors,
                allowedTypes = listOf(
                    DeleteFilterType.RECOMMEND,
                    DeleteFilterType.COMMENT_COUNT,
                    DeleteFilterType.VIEW_COUNT,
                    DeleteFilterType.POST_TITLE_REGEX,
                    DeleteFilterType.AGE_DAYS
                ),
                activeOptions = activeOptions,
                summary = postDeleteConditionSummary(activeOptions),
                onApply = { type, value ->
                    when (type) {
                        DeleteFilterType.RECOMMEND -> {
                            onMinRecommendToKeepChange(value); onRecommendFilterEnabledChange(true)
                        }
                        DeleteFilterType.COMMENT_COUNT -> {
                            onMinCommentToKeepChange(value); onCommentFilterEnabledChange(true)
                        }
                        DeleteFilterType.VIEW_COUNT -> {
                            onMinViewToKeepChange(value); onViewFilterEnabledChange(true)
                        }
                        DeleteFilterType.POST_TITLE_REGEX -> {
                            onPostContentRegexChange(value); onPostContentFilterEnabledChange(true)
                        }
                        DeleteFilterType.COMMENT_CONTENT_REGEX -> Unit
                        DeleteFilterType.AGE_DAYS -> {
                            onMinPostAgeDaysToDeleteChange(value); onDateFilterEnabledChange(true)
                        }
                    }
                },
                onRemove = { type ->
                    when (type) {
                        DeleteFilterType.RECOMMEND -> onRecommendFilterEnabledChange(false)
                        DeleteFilterType.COMMENT_COUNT -> onCommentFilterEnabledChange(false)
                        DeleteFilterType.VIEW_COUNT -> onViewFilterEnabledChange(false)
                        DeleteFilterType.POST_TITLE_REGEX -> onPostContentFilterEnabledChange(false)
                        DeleteFilterType.COMMENT_CONTENT_REGEX -> Unit
                        DeleteFilterType.AGE_DAYS -> onDateFilterEnabledChange(false)
                    }
                }
            )
            Spacer(Modifier.height(20.dp))
            NewestFirstOption(uiColors, "최근 글부터 삭제", deleteNewestFirst, onDeleteNewestFirstChange)
            Spacer(Modifier.height(20.dp))
            QuestionPostDeleteOption(uiColors, deleteQuestionPosts, onDeleteQuestionPostsChange)
            Spacer(Modifier.height(20.dp))
            DeleteSpeedSummary(uiColors, if (usesSlowFilter) "900" else "1,800", usesSlowFilter)
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

@Composable
private fun QuestionPostDeleteOption(
    uiColors: UiColors,
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
            Text("질문글도 삭제하기", fontWeight = FontWeight.Medium)
        }
        Switch(enabled, onEnabledChange, colors = dccleanerSwitchColors(uiColors))
    }
    if (enabled) DeleteOptionDescription("삭제 시 차단될 수 있다는 확인이 필요한 질문글도 삭제합니다.")
}

internal fun postDeleteConditionSummary(options: List<DeleteFilterOption>): String? {
    if (options.isEmpty()) return null
    val firstStage = buildList {
        options.firstOrNull { it.type == DeleteFilterType.AGE_DAYS }
            ?.let { add("작성 후 ${it.value}일 이상") }
        options.firstOrNull { it.type == DeleteFilterType.POST_TITLE_REGEX }
            ?.let { add("제목이 ‘${it.value}’ 정규식과 일치") }
    }
    val secondStage = options.mapNotNull {
        when (it.type) {
            DeleteFilterType.RECOMMEND -> "추천수 ${it.value}개 미만"
            DeleteFilterType.COMMENT_COUNT -> "댓글수 ${it.value}개 미만"
            DeleteFilterType.VIEW_COUNT -> "조회수 ${it.value}회 미만"
            DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> null
            else -> null
        }
    }
    return when {
        firstStage.isNotEmpty() && secondStage.isNotEmpty() ->
            "1차 필터 · 삭제 후보\n${firstStage.joinToString(" 그리고 ")}\n\n" +
                "2차 필터 · 최종 대상\n${secondStage.joinToString(" 그리고 ")}\n\n" +
                "두 필터를 모두 통과한 글만 삭제합니다."
        firstStage.isNotEmpty() -> "삭제 필터\n${firstStage.joinToString(" 그리고 ")}"
        secondStage.isNotEmpty() -> "삭제 필터\n${secondStage.joinToString(" 그리고 ")}"
        else -> null
    }
}

@Composable
internal fun NewestFirstOption(
    uiColors: UiColors,
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SwapVert, null, tint = uiColors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Medium)
        }
        Switch(enabled, onEnabledChange, colors = dccleanerSwitchColors(uiColors))
    }
    if (enabled) DeleteOptionDescription("한 페이지씩 불러와 바로 삭제합니다.")
}

@Composable
internal fun DeleteSpeedSummary(uiColors: UiColors, hourlyCount: String, slow: Boolean) {
    Column(
        Modifier.fillMaxWidth()
            .background(uiColors.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("예상 삭제 속도", fontWeight = FontWeight.Medium)
            Text("시간당 약 ${hourlyCount}개", fontWeight = FontWeight.Bold, color = uiColors.primary)
        }
        if (slow) {
            Spacer(Modifier.height(4.dp))
            Text(
                "추가 확인 조건 사용 시 처리 시간이 늘어납니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
