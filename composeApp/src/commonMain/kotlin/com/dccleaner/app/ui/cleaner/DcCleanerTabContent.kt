package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import kotlinx.coroutines.CoroutineScope

@Composable
fun DcCleanerTabContent(
    uiColors: UiColors,
    postingGallList: Map<String, String>,
    commentGallList: Map<String, String>,
    deleteType: String,
    onDeleteTypeChange: (String) -> Unit,
    twocaptchaKey: String,
    onTwocaptchaKeyChange: (String) -> Unit,
    isTwocaptchaValid: Boolean?,
    onTwocaptchaValidChange: (Boolean?) -> Unit,
    isCheckingTwocaptcha: Boolean,
    onIsCheckingTwocaptchaChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    coroutine: CoroutineScope,
    snackbarHostState: SnackbarHostState,
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
    onValidateTwocaptchaKey: suspend (String) -> Boolean,
    onOpenAutoCaptchaGuide: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    Column {
        Spacer(Modifier.height(1.dp).then(captchaSectionMarker))

        // 2captcha 설정
        CaptchaKeyCard(
            uiColors = uiColors,
            twocaptchaKey = twocaptchaKey,
            onTwocaptchaKeyChange = onTwocaptchaKeyChange,
            isTwocaptchaValid = isTwocaptchaValid,
            onTwocaptchaValidChange = onTwocaptchaValidChange,
            isCheckingTwocaptcha = isCheckingTwocaptcha,
            onIsCheckingTwocaptchaChange = onIsCheckingTwocaptchaChange,
            coroutine = coroutine,
            snackbarHostState = snackbarHostState,
            onValidateTwocaptchaKey = onValidateTwocaptchaKey,
            onOpenAutoCaptchaGuide = onOpenAutoCaptchaGuide
        )

        Spacer(Modifier.height(20.dp))

        Spacer(Modifier.height(1.dp).then(deleteOptionsSectionMarker))

        // 갤러리 선택
        val currentGallList =
            if (deleteType == "posting") postingGallList else commentGallList
        GallerySelectCard(
            uiColors = uiColors,
            gallList = currentGallList,
            deleteType = deleteType,
            onDeleteTypeChange = onDeleteTypeChange,
            selected = selectedGallList,
            onSelectedChange = onSelectedGallListChange
        )

        Spacer(Modifier.height(1.dp).then(filterOptionsSectionMarker))

        // 삭제 조건 설정 (글 모드에서만 표시)
        if (deleteType == "posting") {
            Spacer(Modifier.height(20.dp))

            PostDeleteConditionCard(
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
                recordGuestbookLog = recordGuestbookLog,
                onRecordGuestbookLogChange = onRecordGuestbookLogChange,
                onShowDeleteDialog = onShowDeleteDialog,
                onOpenProxyCleaner = onOpenProxyCleaner
            )

            Spacer(Modifier.height(20.dp))
        }

        // 내 글 필터 (댓글 모드에서만 표시)
        if (deleteType == "comment") {
            Spacer(Modifier.height(20.dp))

            CommentDeleteConditionCard(
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
                recordGuestbookLog = recordGuestbookLog,
                onRecordGuestbookLogChange = onRecordGuestbookLogChange,
                onShowDeleteDialog = onShowDeleteDialog,
                onOpenProxyCleaner = onOpenProxyCleaner
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}
