package com.dccleaner.app.ui.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.dialog.formatNumber
import com.dccleaner.app.ui.proxy.ULTRA_COLORS
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
    proxySpeedPerHour: Int? = null,
    runOnServer: Boolean = false,
    onRequireLocal: (apply: () -> Unit) -> Unit = { it() }
) {
    val activeOptions = buildList {
        if (recommendFilterEnabled) add(DeleteFilterOption(DeleteFilterType.RECOMMEND, minRecommendToKeep.ifBlank { "1" }))
        if (commentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.COMMENT_COUNT, minCommentToKeep.ifBlank { "1" }))
        if (viewFilterEnabled) add(DeleteFilterOption(DeleteFilterType.VIEW_COUNT, minViewToKeep.ifBlank { "1" }))
        if (postContentFilterEnabled) add(DeleteFilterOption(DeleteFilterType.POST_TITLE_REGEX, postContentRegex))
        if (dateFilterEnabled) add(DeleteFilterOption(DeleteFilterType.AGE_DAYS, minPostAgeDaysToDelete.ifBlank { "5" }))
    }
    val usesSlowFilter = recommendFilterEnabled || commentFilterEnabled || viewFilterEnabled

    // 삭제 설정 시트 안에 놓이므로 카드 바탕·옆 여백 없이 시트의 갤러리 버튼과 줄을 맞춘다.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
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
                            val apply = { onPostContentRegexChange(value); onPostContentFilterEnabledChange(true) }
                            if (runOnServer) onRequireLocal(apply) else apply()
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
            DeleteSectionDivider(uiColors)
            AdvancedOptions(listOf(deleteNewestFirst, deleteQuestionPosts).count { it }) {
                DeleteSwitchRow(
                    uiColors, "최근 글부터 삭제", "최신 글부터 한 페이지씩 지워요", deleteNewestFirst
                ) { enabled ->
                    if (runOnServer && enabled) onRequireLocal { onDeleteNewestFirstChange(true) } else onDeleteNewestFirstChange(enabled)
                }
                DeleteSwitchRow(
                    uiColors, "질문글도 삭제하기", "차단 경고가 뜨는 질문글도 지워요", deleteQuestionPosts, onDeleteQuestionPostsChange
                )
            }
            DeleteSectionDivider(uiColors)
            // The server is halved by the same filters that halve a local run.
            DeleteSpeedSummary(uiColors, usesSlowFilter, proxySpeedPerHour)
        }
    }
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
            "먼저 고르는 조건\n${firstStage.joinToString(" 그리고 ")}\n\n" +
                "그중에서 지울 것\n${secondStage.joinToString(" 그리고 ")}\n\n" +
                "두 조건을 모두 만족하는 글만 지워요"
        firstStage.isNotEmpty() -> "지울 조건\n${firstStage.joinToString(" 그리고 ")}"
        secondStage.isNotEmpty() -> "지울 조건\n${secondStage.joinToString(" 그리고 ")}"
        else -> null
    }
}

@Composable
internal fun DeleteSectionDivider(uiColors: UiColors) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = uiColors.outline)
}

/** 자주 안 쓰는 스위치를 접어 두는 줄. 켜진 게 있으면 처음부터 펼쳐 둔다. */
@Composable
internal fun AdvancedOptions(enabledCount: Int, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(enabledCount > 0) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }
            .heightIn(min = 48.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (enabledCount > 0) "고급 옵션 · ${enabledCount}개 켜짐" else "고급 옵션",
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "고급 옵션 접기" else "고급 옵션 펼치기"
        )
    }
    AnimatedVisibility(expanded) { Column(content = content) }
}

/** 줄 전체를 눌러도 켜고 끌 수 있는 스위치 줄. */
@Composable
internal fun DeleteSwitchRow(
    uiColors: UiColors,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            DeleteOptionDescription(description)
        }
        Switch(
            checked, null, colors = dccleanerSwitchColors(uiColors),
            modifier = Modifier.semantics { contentDescription = title }
        )
    }
}

@Composable
internal fun DeleteSpeedSummary(uiColors: UiColors, halved: Boolean, proxySpeedPerHour: Int? = null) {
    // 초고속 삭제 runs on the server, so its own speed replaces the local 900/1,800 estimate.
    val proxy = proxySpeedPerHour != null
    val speed = proxySpeedPerHour?.let { formatNumber((if (halved) it / 2 else it).toLong()) }
        ?: if (halved) "900" else "1,800"
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (proxy) "초고속 삭제 예상 속도 · " else "예상 속도 · ",
            style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary
        )
        Text(
            "시간당 약 ${speed}개" + if (halved) " (조건 때문에 절반)" else "",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (proxy) ULTRA_COLORS[0] else uiColors.primary
        )
    }
}
