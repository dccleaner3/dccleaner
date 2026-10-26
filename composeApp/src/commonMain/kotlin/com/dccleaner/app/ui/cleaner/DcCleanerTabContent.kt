package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors

@Composable
fun DcCleanerTabContent(
    uiColors: UiColors,
    postingGallList: Map<String, String>,
    commentGallList: Map<String, String>,
    deleteType: String,
    onDeleteTypeChange: (String) -> Unit,
    isTwocaptchaValid: Boolean?,
    onShowDeleteDialog: () -> Unit,
    selectedGallList: List<String>,
    onSelectedGallListChange: (List<String>) -> Unit,
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
    deleteQuestionPosts: Boolean,
    onDeleteQuestionPostsChange: (Boolean) -> Unit,
    minPostAgeDaysToDelete: String,
    onMinPostAgeDaysToDeleteChange: (String) -> Unit,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    captchaSectionMarker: Modifier = Modifier,
    deleteOptionsSectionMarker: Modifier = Modifier,
    filterOptionsSectionMarker: Modifier = Modifier,
    onOpenProxyCleaner: () -> Unit,
    runOnServer: Boolean = false,
    proxySpeedPerHour: Int? = null,
    onRequireLocal: (apply: () -> Unit) -> Unit = { it() },
    deleteBoth: Boolean = false,
    /** 지울 대상을 하나도 안 골랐다. 시작 버튼이 꺼진다. */
    noDeleteTarget: Boolean = false,
    onDeleteTargetsChange: (Set<String>) -> Unit = {},
    commentFirst: Boolean = true,
    onCommentFirstChange: (Boolean) -> Unit = {},
    otherSelectedCount: Int = 0,
    /** 시트 밖 요약 줄에 쓰는 종류별 적용 조건. */
    activeFilters: List<String> = emptyList(),
    otherActiveFilters: List<String> = emptyList()
) {
    // 지금 시트로 고치는 종류. null이면 시트가 닫혀 있다.
    var editingType by remember { mutableStateOf<String?>(null) }
    fun isChecked(type: String) = !noDeleteTarget && (deleteBoth || deleteType == type)
    val checkedTypes = listOf("posting", "comment").filter(::isChecked).toSet()
    fun gallListOf(type: String) = if (type == "posting") postingGallList else commentGallList

    Column {
        Spacer(Modifier.height(1.dp).then(deleteOptionsSectionMarker))

        CleanerSectionLabel("무엇을 지울까요", uiColors)
        Spacer(Modifier.height(8.dp))
        DeleteTargetCard(
            uiColors = uiColors,
            checked = ::isChecked,
            summaryOf = { type ->
                val editing = type == deleteType
                DeleteTargetSummary(
                    selectedCount = if (editing) selectedGallList.size else otherSelectedCount,
                    totalCount = gallListOf(type).size,
                    // 캡챠 자동 해결은 두 종류 공통이라 줄 요약에서는 뺀다.
                    conditions = (if (editing) activeFilters else otherActiveFilters).filterNot { it.contains("2Captcha") }
                )
            },
            onCheckedChange = { type, on -> onDeleteTargetsChange(if (on) checkedTypes + type else checkedTypes - type) },
            onOpen = { type ->
                if (deleteType != type) onDeleteTypeChange(type)
                editingType = type
            },
            commentFirst = commentFirst,
            onSwapOrder = { onCommentFirstChange(!commentFirst) },
            adviseCommentFirst = runOnServer && deleteBoth && !commentFirst,
            modifier = filterOptionsSectionMarker
        )

        // 시트가 열린 사이 설정 중인 종류나 지울 대상이 밖에서 바뀌면(삭제 단계 전환 등) 닫는다.
        // 숨기기만 하면 나중에 그 종류로 돌아올 때 시트가 저절로 다시 열린다.
        val sheetType = editingType?.takeIf { it == deleteType && isChecked(it) }
        LaunchedEffect(sheetType == null) { if (sheetType == null) editingType = null }
        sheetType?.let { type ->
            DeleteTargetSheet(
                uiColors = uiColors,
                type = type,
                onDismiss = { editingType = null },
                galleries = {
                    GallListSelectUI(
                        gallList = gallListOf(type),
                        primaryColor = uiColors.primary,
                        uiColors = uiColors,
                        deleteType = type,
                        selected = selectedGallList,
                        onSelectedChange = onSelectedGallListChange
                    )
                },
                conditions = {
                    if (type == "posting") PostDeleteConditionCard(
                        uiColors = uiColors,
                        minRecommendToKeep = minRecommendToKeep,
                        onMinRecommendToKeepChange = onMinRecommendToKeepChange,
                        minCommentToKeep = minCommentToKeep,
                        onMinCommentToKeepChange = onMinCommentToKeepChange,
                        minViewToKeep = minViewToKeep,
                        onMinViewToKeepChange = onMinViewToKeepChange,
                        recommendFilterEnabled = recommendFilterEnabled,
                        onRecommendFilterEnabledChange = onRecommendFilterEnabledChange,
                        commentFilterEnabled = commentFilterEnabled,
                        onCommentFilterEnabledChange = onCommentFilterEnabledChange,
                        viewFilterEnabled = viewFilterEnabled,
                        onViewFilterEnabledChange = onViewFilterEnabledChange,
                        postContentFilterEnabled = postContentFilterEnabled,
                        onPostContentFilterEnabledChange = onPostContentFilterEnabledChange,
                        postContentRegex = postContentRegex,
                        onPostContentRegexChange = onPostContentRegexChange,
                        dateFilterEnabled = dateFilterEnabled,
                        onDateFilterEnabledChange = onDateFilterEnabledChange,
                        deleteNewestFirst = deleteNewestFirst,
                        onDeleteNewestFirstChange = onDeleteNewestFirstChange,
                        deleteQuestionPosts = deleteQuestionPosts,
                        onDeleteQuestionPostsChange = onDeleteQuestionPostsChange,
                        minPostAgeDaysToDelete = minPostAgeDaysToDelete,
                        onMinPostAgeDaysToDeleteChange = onMinPostAgeDaysToDeleteChange,
                        proxySpeedPerHour = proxySpeedPerHour.takeIf { runOnServer },
                        runOnServer = runOnServer,
                        onRequireLocal = onRequireLocal
                    ) else CommentDeleteConditionCard(
                        uiColors = uiColors,
                        myPostFilterEnabled = myPostFilterEnabled,
                        onMyPostFilterEnabledChange = onMyPostFilterEnabledChange,
                        dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                        onDcconOnlyFilterEnabledChange = onDcconOnlyFilterEnabledChange,
                        commentContentFilterEnabled = commentContentFilterEnabled,
                        onCommentContentFilterEnabledChange = onCommentContentFilterEnabledChange,
                        commentContentRegex = commentContentRegex,
                        onCommentContentRegexChange = onCommentContentRegexChange,
                        dateFilterEnabled = dateFilterEnabled,
                        onDateFilterEnabledChange = onDateFilterEnabledChange,
                        deleteNewestFirst = deleteNewestFirst,
                        onDeleteNewestFirstChange = onDeleteNewestFirstChange,
                        minPostAgeDaysToDelete = minPostAgeDaysToDelete,
                        onMinPostAgeDaysToDeleteChange = onMinPostAgeDaysToDeleteChange,
                        proxySpeedPerHour = proxySpeedPerHour.takeIf { runOnServer },
                        runOnServer = runOnServer,
                        onRequireLocal = onRequireLocal
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        CleanerSectionLabel("실행", uiColors)
        Spacer(Modifier.height(8.dp))
        DeleteStartControls(uiColors, recordGuestbookLog, onRecordGuestbookLogChange, onShowDeleteDialog, runOnServer,
            startLabel = when {
                noDeleteTarget -> "지울 것을 먼저 골라 주세요"
                deleteBoth -> (if (commentFirst) "댓글 → 게시글" else "게시글 → 댓글") + " 삭제 시작"
                else -> "삭제 시작"
            },
            startEnabled = !noDeleteTarget,
            ultraLabel = if (deleteBoth) (if (commentFirst) "댓글 → 게시글" else "게시글 → 댓글") + " 초고속 삭제" else "초고속 삭제 시작")

        Spacer(Modifier.height(20.dp))
        Spacer(Modifier.height(1.dp).then(captchaSectionMarker))
        CaptchaAutomationCard(uiColors, isTwocaptchaValid, onOpenProxyCleaner)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CleanerSectionLabel(text: String, uiColors: UiColors) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = uiColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}
