package com.dccleaner.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeletionPreview
import com.dccleaner.app.model.DaewangconProgress
import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.UserInfo
import com.dccleaner.app.ui.card.DaewangconCard
import com.dccleaner.app.ui.card.DaewangconProgressCard
import com.dccleaner.app.ui.card.DeleteProgressCard
import com.dccleaner.app.ui.card.DeleteRunningContent
import com.dccleaner.app.ui.card.DeleteTaskRestoreOverlay
import com.dccleaner.app.ui.card.GuestbookProgressCard
import com.dccleaner.app.ui.card.InterruptedDeleteTasksCard
import com.dccleaner.app.ui.card.LoginCard
import com.dccleaner.app.ui.card.RemoteBannerCard
import com.dccleaner.app.ui.card.TaskProgressDialog
import com.dccleaner.app.ui.card.UserInfoCard
import com.dccleaner.app.ui.card.VersionInfoCard
import com.dccleaner.app.ui.cleaner.DcCleanerTabContent
import com.dccleaner.app.ui.dialog.CaptchaDialog
import com.dccleaner.app.ui.dialog.DaewangconStartDialog
import com.dccleaner.app.ui.dialog.DeleteAccountDialog
import com.dccleaner.app.ui.dialog.DeleteTaskRecordDialog
import com.dccleaner.app.ui.dialog.ErrorDialog
import com.dccleaner.app.ui.dialog.StartDeletionDialog
import com.dccleaner.app.ui.dialog.StopDaewangconDialog
import com.dccleaner.app.ui.dialog.StopDeleteDialog
import com.dccleaner.app.ui.dialog.ProxyCleanerQuoteDialog
import com.dccleaner.app.ui.guestbook.GuestbookTabContent
import com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult
import com.dccleaner.app.platform.fetchRemoteBannerConfig
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REMOTE_BANNER_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1_000L

data class DccleanerScreenState(
    val uiColors: UiColors,
    val isDarkTheme: Boolean,
    val id: String,
    val pw: String,
    val loginInfo: UserInfo?,
    val daewangconServerProgress: DaewangconProgress?,
    val saveLogin: Boolean,
    val credentialStorageSupported: Boolean = true,
    val savedAccounts: List<SavedAccount>,
    val isLoggingIn: Boolean,
    val sessionExpired: Boolean,
    val deleteUiActive: Boolean,
    val runningLoginId: String,
    val displayedGallery: String,
    val displayedProgress: Float,
    val displayedDeletedCount: Int,
    val displayedTotalCount: Int,
    val displayedDeleteLog: List<String>,
    val interruptedTasks: List<DeleteTaskProgress>,
    val resumeEnabled: Boolean,
    val restoringTaskId: String?,
    val focusedTaskId: String?,
    val restoringMessage: String,
    val selectedTab: Int,
    val developerMode: Boolean,
    val commentCleanerRunning: Boolean,
    val postingGallList: Map<String, String>,
    val commentGallList: Map<String, String>,
    val deleteType: String,
    val selectedGallList: List<String>,
    val twocaptchaKey: String,
    val isTwocaptchaValid: Boolean?,
    val isCheckingTwocaptcha: Boolean,
    val minRecommendToKeep: String,
    val minCommentToKeep: String,
    val minViewToKeep: String,
    val recommendFilterEnabled: Boolean,
    val commentFilterEnabled: Boolean,
    val viewFilterEnabled: Boolean,
    val postContentFilterEnabled: Boolean,
    val postContentRegex: String,
    val myPostFilterEnabled: Boolean,
    val dcconOnlyFilterEnabled: Boolean,
    val commentContentFilterEnabled: Boolean,
    val commentContentRegex: String,
    val dateFilterEnabled: Boolean,
    val deleteNewestFirst: Boolean,
    val deleteQuestionPosts: Boolean,
    val minPostAgeDaysToDelete: String,
    val recordGuestbookLog: Boolean,
    val latestVersion: String?,
    val currentVersion: String,
    val isCheckingVersion: Boolean,
    val showErrorDialog: Boolean,
    val errorMessage: String,
    val deleteTaskToRemove: DeleteTaskProgress?,
    val isInspectingDeletion: Boolean,
    val deletionInspectionMessage: String,
    val deletionPreview: DeletionPreview?,
    val showDeleteConfirmDialog: Boolean,
    val activeFilters: List<String>,
    val showDeleteProgressDialog: Boolean,
    val progressDeleteType: String,
    val isCompleted: Boolean,
    val isDeleting: Boolean,
    val estimatedTimeLeft: Long,
    val nextCaptchaEstimatedTimeLeft: Long,
    val isTwoCaptchaConfigured: Boolean,
    val showStopDeleteDialog: Boolean,
    val showCaptchaDialog: Boolean,
    val accountToDelete: SavedAccount?,
    val showDeleteAccountDialog: Boolean,
    val showDaewangconDialog: Boolean,
    val showDaewangconProgressDialog: Boolean,
    val showStopDaewangconDialog: Boolean,
    val isDaewangconRunning: Boolean,
    val isDaewangconCompleted: Boolean,
    val daewangconErrorMessage: String?,
    val daewangconProgress: Float,
    val daewangconLog: List<String>,
    val daewangconPostCount: Int,
    val daewangconCommentCount: Int,
    val guestbookUserListText: String,
    val guestbookMessageText: String,
    val guestbookShowConfirmDialog: Boolean,
    val guestbookShowResultDialog: Boolean,
    val showGuestbookProgressDialog: Boolean,
    val guestbookIsSending: Boolean,
    val guestbookProgressDone: Int,
    val guestbookProgressTotal: Int,
    val guestbookSuccessCount: Int,
    val guestbookFailCount: Int,
    val guestbookCacheEnabled: Boolean,
    val guestbookCachedUserCount: Int
)

class DccleanerScreenActions(
    val onDarkThemeChange: (Boolean) -> Unit,
    val onIdChange: (String) -> Unit,
    val onPwChange: (String) -> Unit,
    val onSaveLoginChange: (Boolean) -> Unit,
    val onSavedAccountClick: (SavedAccount) -> Unit,
    val onDeleteSavedAccountClick: (SavedAccount) -> Unit,
    val onLoginClick: () -> Unit,
    val onLogoutClick: () -> Unit,
    val onStopRunningClick: () -> Unit,
    val onResumeTask: (DeleteTaskProgress) -> Unit,
    val onDeleteTask: (DeleteTaskProgress) -> Unit,
    val onTabChange: (Int) -> Unit,
    val onDeveloperModeToggle: () -> Unit,
    val onRequestWriteCookies: () -> List<String>,
    val onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    val onStopCommentCleaner: () -> Unit,
    val onOpenManual: (Int) -> Unit,
    val onDeleteTypeChange: (String) -> Unit,
    val onTwocaptchaKeyChange: (String) -> Unit,
    val onTwocaptchaValidChange: (Boolean?) -> Unit,
    val onIsCheckingTwocaptchaChange: (Boolean) -> Unit,
    val onShowDeleteDialog: () -> Unit,
    val onSelectedGallListChange: (List<String>) -> Unit,
    val onMinRecommendToKeepChange: (String) -> Unit,
    val onMinCommentToKeepChange: (String) -> Unit,
    val onMinViewToKeepChange: (String) -> Unit,
    val onRecommendFilterEnabledChange: (Boolean) -> Unit,
    val onCommentFilterEnabledChange: (Boolean) -> Unit,
    val onViewFilterEnabledChange: (Boolean) -> Unit,
    val onPostContentFilterEnabledChange: (Boolean) -> Unit,
    val onPostContentRegexChange: (String) -> Unit,
    val onMyPostFilterEnabledChange: (Boolean) -> Unit,
    val onDcconOnlyFilterEnabledChange: (Boolean) -> Unit,
    val onCommentContentFilterEnabledChange: (Boolean) -> Unit,
    val onCommentContentRegexChange: (String) -> Unit,
    val onDateFilterEnabledChange: (Boolean) -> Unit,
    val onDeleteNewestFirstChange: (Boolean) -> Unit,
    val onDeleteQuestionPostsChange: (Boolean) -> Unit,
    val onMinPostAgeDaysToDeleteChange: (String) -> Unit,
    val onRecordGuestbookLogChange: (Boolean) -> Unit,
    val onCaptchaSectionPosition: (Int) -> Unit,
    val onDeleteOptionsSectionPosition: (Int) -> Unit,
    val onFilterOptionsSectionPosition: (Int) -> Unit,
    val onValidateTwocaptchaKey: suspend (String) -> Boolean,
    val onOpenUpdate: () -> Unit,
    val onDismissError: () -> Unit,
    val onConfirmDeleteTaskRecord: () -> Unit,
    val onDismissDeleteTaskRecord: () -> Unit,
    val onConfirmStartDeletion: () -> Unit,
    val onDismissStartDeletion: () -> Unit,
    val onCloseDeleteProgress: () -> Unit,
    val onCompleteDeleteProgress: () -> Unit,
    val onStopDeleteRequest: () -> Unit,
    val onConfirmStopDelete: () -> Unit,
    val onDismissStopDelete: () -> Unit,
    val onOpenGallogForCaptcha: () -> Unit,
    val onResolveCaptcha: () -> Unit,
    val onOpenAutoCaptchaGuide: () -> Unit,
    val onConfirmDeleteAccount: () -> Unit,
    val onDismissDeleteAccount: () -> Unit,
    val onStartDaewangconRequest: () -> Unit,
    val onConfirmStartDaewangcon: () -> Unit,
    val onDismissStartDaewangcon: () -> Unit,
    val onCloseDaewangconProgress: () -> Unit,
    val onStopDaewangconRequest: () -> Unit,
    val onConfirmStopDaewangcon: () -> Unit,
    val onDismissStopDaewangcon: () -> Unit,
    val onGuestbookUserListTextChange: (String) -> Unit,
    val onGuestbookMessageTextChange: (String) -> Unit,
    val onGuestbookShowConfirmDialogChange: (Boolean) -> Unit,
    val onGuestbookShowResultDialogChange: (Boolean) -> Unit,
    val onCloseGuestbookProgress: () -> Unit,
    val onGuestbookIsSendingChange: (Boolean) -> Unit,
    val onGuestbookProgressDoneChange: (Int) -> Unit,
    val onGuestbookProgressTotalChange: (Int) -> Unit,
    val onGuestbookSuccessCountChange: (Int) -> Unit,
    val onGuestbookFailCountChange: (Int) -> Unit,
    val onFilterGuestbookCachedUsers: suspend (List<String>) -> GuestbookCacheFilterResult,
    val onClearGuestbookCachedUsers: suspend () -> Boolean,
    val onResolveGuestbookUserList: suspend (String) -> String,
    val onStartGuestbookSend: (List<String>, String) -> Unit
)

@Composable
fun DccleanerScreenContent(
    state: DccleanerScreenState,
    actions: DccleanerScreenActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() },
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    applySystemBarsPadding: Boolean = false,
    maxContentWidth: Dp = Dp.Unspecified
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    var remoteBanner by remember { mutableStateOf<RemoteBannerConfig?>(null) }
    var dismissedBannerId by remember { mutableStateOf<String?>(null) }
    var showProxyCleanerQuote by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            remoteBanner = fetchRemoteBannerConfig()
            delay(REMOTE_BANNER_REFRESH_INTERVAL_MILLIS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(state.uiColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (maxContentWidth != Dp.Unspecified) Modifier.widthIn(max = maxContentWidth) else Modifier)
                .then(if (applySystemBarsPadding) Modifier.systemBarsPadding() else Modifier)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            HeaderCard(
                uiColors = state.uiColors,
                isDarkTheme = state.isDarkTheme,
                onDarkThemeChange = actions.onDarkThemeChange
            )
            Spacer(Modifier.height(24.dp))

            if (state.deleteUiActive) {
                DeleteRunningContent(
                    uiColors = state.uiColors,
                    loginId = state.runningLoginId,
                    currentGallery = state.displayedGallery,
                    progress = state.displayedProgress,
                    deletedCount = state.displayedDeletedCount,
                    totalCount = state.displayedTotalCount,
                    latestLog = state.displayedDeleteLog.lastOrNull(),
                    onStop = actions.onStopRunningClick
                )
            } else if (state.loginInfo == null) {
                LoginCard(
                    uiColors = state.uiColors,
                    savedAccounts = state.savedAccounts,
                    id = state.id,
                    onIdChange = actions.onIdChange,
                    pw = state.pw,
                    onPwChange = actions.onPwChange,
                    saveLogin = state.saveLogin,
                    credentialStorageSupported = state.credentialStorageSupported,
                    onSaveLoginChange = actions.onSaveLoginChange,
                    isLoggingIn = state.isLoggingIn,
                    sessionExpired = state.sessionExpired,
                    onSavedAccountClick = actions.onSavedAccountClick,
                    onDeleteSavedAccountClick = actions.onDeleteSavedAccountClick,
                    onLoginClick = actions.onLoginClick
                )
            } else {
                remoteBanner
                    ?.takeUnless { it.id == dismissedBannerId }
                    ?.let { banner ->
                        RemoteBannerCard(
                            banner = banner,
                            uiColors = state.uiColors,
                            onAction = { url -> runCatching { uriHandler.openUri(url) } },
                            onDismiss = { dismissedBannerId = banner.id }
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                UserInfoCard(
                    uiColors = state.uiColors,
                    loginInfo = state.loginInfo,
                    onLogoutClick = actions.onLogoutClick
                )
                Spacer(Modifier.height(20.dp))
                InterruptedDeleteTasksCard(
                    uiColors = state.uiColors,
                    tasks = state.interruptedTasks,
                    resumeEnabled = state.resumeEnabled,
                    restoringTaskId = state.restoringTaskId,
                    focusedTaskId = state.focusedTaskId,
                    onResume = actions.onResumeTask,
                    onDelete = actions.onDeleteTask
                )
                if (state.interruptedTasks.isNotEmpty()) Spacer(Modifier.height(20.dp))
                if (!state.showDeleteProgressDialog) {
                    TabsCard(state, actions)
                    Spacer(Modifier.height(20.dp))
                    when (state.selectedTab) {
                        0 -> Column {
                            state.daewangconServerProgress
                                ?.takeUnless(DaewangconProgress::isEnabled)
                                ?.let { progress ->
                                    DaewangconCard(
                                        uiColors = state.uiColors,
                                        progress = progress,
                                        onStartDaewangcon = actions.onStartDaewangconRequest,
                                        isDaewangconRunning = state.isDaewangconRunning
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                            DcCleanerTabContent(
                            uiColors = state.uiColors,
                            postingGallList = state.postingGallList,
                            commentGallList = state.commentGallList,
                            deleteType = state.deleteType,
                            onDeleteTypeChange = actions.onDeleteTypeChange,
                            twocaptchaKey = state.twocaptchaKey,
                            onTwocaptchaKeyChange = actions.onTwocaptchaKeyChange,
                            isTwocaptchaValid = state.isTwocaptchaValid,
                            onTwocaptchaValidChange = actions.onTwocaptchaValidChange,
                            isCheckingTwocaptcha = state.isCheckingTwocaptcha,
                            onIsCheckingTwocaptchaChange = actions.onIsCheckingTwocaptchaChange,
                            onShowDeleteDialog = actions.onShowDeleteDialog,
                            coroutine = coroutineScope,
                            snackbarHostState = snackbarHostState,
                            selectedGallList = state.selectedGallList,
                            onSelectedGallListChange = actions.onSelectedGallListChange,
                            minRecommendToKeep = state.minRecommendToKeep,
                            onMinRecommendToKeepChange = actions.onMinRecommendToKeepChange,
                            minCommentToKeep = state.minCommentToKeep,
                            onMinCommentToKeepChange = actions.onMinCommentToKeepChange,
                            minViewToKeep = state.minViewToKeep,
                            onMinViewToKeepChange = actions.onMinViewToKeepChange,
                            recommendFilterEnabled = state.recommendFilterEnabled,
                            onRecommendFilterEnabledChange = actions.onRecommendFilterEnabledChange,
                            commentFilterEnabled = state.commentFilterEnabled,
                            onCommentFilterEnabledChange = actions.onCommentFilterEnabledChange,
                            viewFilterEnabled = state.viewFilterEnabled,
                            onViewFilterEnabledChange = actions.onViewFilterEnabledChange,
                            postContentFilterEnabled = state.postContentFilterEnabled,
                            onPostContentFilterEnabledChange = actions.onPostContentFilterEnabledChange,
                            postContentRegex = state.postContentRegex,
                            onPostContentRegexChange = actions.onPostContentRegexChange,
                            myPostFilterEnabled = state.myPostFilterEnabled,
                            onMyPostFilterEnabledChange = actions.onMyPostFilterEnabledChange,
                            dcconOnlyFilterEnabled = state.dcconOnlyFilterEnabled,
                            onDcconOnlyFilterEnabledChange = actions.onDcconOnlyFilterEnabledChange,
                            commentContentFilterEnabled = state.commentContentFilterEnabled,
                            onCommentContentFilterEnabledChange = actions.onCommentContentFilterEnabledChange,
                            commentContentRegex = state.commentContentRegex,
                            onCommentContentRegexChange = actions.onCommentContentRegexChange,
                            dateFilterEnabled = state.dateFilterEnabled,
                            onDateFilterEnabledChange = actions.onDateFilterEnabledChange,
                            deleteNewestFirst = state.deleteNewestFirst,
                            onDeleteNewestFirstChange = actions.onDeleteNewestFirstChange,
                            deleteQuestionPosts = state.deleteQuestionPosts,
                            onDeleteQuestionPostsChange = actions.onDeleteQuestionPostsChange,
                            minPostAgeDaysToDelete = state.minPostAgeDaysToDelete,
                            onMinPostAgeDaysToDeleteChange = actions.onMinPostAgeDaysToDeleteChange,
                            recordGuestbookLog = state.recordGuestbookLog,
                            onRecordGuestbookLogChange = actions.onRecordGuestbookLogChange,
                            captchaSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                actions.onCaptchaSectionPosition(
                                    (scrollState.value + coordinates.positionInRoot().y).roundToInt()
                                )
                            },
                            deleteOptionsSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                actions.onDeleteOptionsSectionPosition(
                                    (scrollState.value + coordinates.positionInRoot().y).roundToInt()
                                )
                            },
                            filterOptionsSectionMarker = Modifier.onGloballyPositioned { coordinates ->
                                actions.onFilterOptionsSectionPosition(
                                    (scrollState.value + coordinates.positionInRoot().y).roundToInt()
                                )
                            },
                            onValidateTwocaptchaKey = actions.onValidateTwocaptchaKey,
                            onOpenAutoCaptchaGuide = actions.onOpenAutoCaptchaGuide,
                                onOpenProxyCleaner = { showProxyCleanerQuote = true }
                            )
                        }
                        1 -> GuestbookTabContent(
                            uiColors = state.uiColors,
                            coroutine = coroutineScope,
                            userListText = state.guestbookUserListText,
                            onUserListTextChange = actions.onGuestbookUserListTextChange,
                            messageText = state.guestbookMessageText,
                            onMessageTextChange = actions.onGuestbookMessageTextChange,
                            showConfirmDialog = state.guestbookShowConfirmDialog,
                            onShowConfirmDialogChange = actions.onGuestbookShowConfirmDialogChange,
                            showResultDialog = state.guestbookShowResultDialog,
                            onShowResultDialogChange = actions.onGuestbookShowResultDialogChange,
                            isSending = state.guestbookIsSending,
                            onIsSendingChange = actions.onGuestbookIsSendingChange,
                            progressDone = state.guestbookProgressDone,
                            onProgressDoneChange = actions.onGuestbookProgressDoneChange,
                            progressTotal = state.guestbookProgressTotal,
                            onProgressTotalChange = actions.onGuestbookProgressTotalChange,
                            successCount = state.guestbookSuccessCount,
                            onSuccessCountChange = actions.onGuestbookSuccessCountChange,
                            failCount = state.guestbookFailCount,
                            onFailCountChange = actions.onGuestbookFailCountChange,
                            cacheEnabled = state.guestbookCacheEnabled,
                            cachedUserCount = state.guestbookCachedUserCount,
                            onFilterCachedUsers = actions.onFilterGuestbookCachedUsers,
                            onClearCachedUsers = actions.onClearGuestbookCachedUsers,
                            onResolveUserList = actions.onResolveGuestbookUserList,
                            onStartGuestbookSend = actions.onStartGuestbookSend
                        )
                        2 -> WriteTabContent(
                            onRequestCookies = actions.onRequestWriteCookies,
                            commentCleanerRunning = state.commentCleanerRunning,
                            onStartCommentCleaner = actions.onStartCommentCleaner,
                            onStopCommentCleaner = actions.onStopCommentCleaner,
                            uiColors = state.uiColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(720.dp)
                        )
                    }
                }
            }

            if (!state.deleteUiActive) {
                when {
                    state.loginInfo == null -> Spacer(Modifier.height(24.dp))
                    state.selectedTab != 0 -> Spacer(Modifier.height(20.dp))
                }
                VersionInfoCard(
                    uiColors = state.uiColors,
                    currentVersion = state.currentVersion,
                    latestVersion = state.latestVersion,
                    isCheckingVersion = state.isCheckingVersion,
                    onUpdateClick = actions.onOpenUpdate,
                    onDeveloperModeToggle = {
                        actions.onDeveloperModeToggle()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (state.developerMode) "개발자 모드가 해제되었습니다."
                                else "개발자 모드가 활성화되었습니다."
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(100.dp))
        }

        PlatformVerticalScrollbar(
            scrollState = scrollState,
            uiColors = state.uiColors,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 8.dp, end = 4.dp)
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.Center))
        if (state.restoringTaskId != null) {
            DeleteTaskRestoreOverlay(state.uiColors, state.restoringMessage)
        }
        ScreenDialogs(
            state = state,
            actions = actions,
            showProxyCleanerQuote = showProxyCleanerQuote,
            onShowProxyCleanerQuoteChange = { showProxyCleanerQuote = it }
        )
    }
}

@Composable
private fun HeaderCard(
    uiColors: UiColors,
    isDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(uiColors.headerStart, uiColors.headerEnd)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "디시클리너 모바일 & PC",
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = { onDarkThemeChange(!isDarkTheme) },
                    modifier = Modifier.background(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    )
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkTheme) "화이트 모드로 전환" else "다크 모드로 전환",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TabsCard(state: DccleanerScreenState, actions: DccleanerScreenActions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = state.uiColors.card),
        border = BorderStroke(1.dp, state.uiColors.outline)
    ) {
        SecondaryTabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = state.uiColors.card,
            contentColor = state.uiColors.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = state.selectedTab == 0, onClick = { actions.onTabChange(0) }, text = { Text("클리너") })
            Tab(selected = state.selectedTab == 1, onClick = { actions.onTabChange(1) }, text = { Text("방명록") })
            Tab(selected = state.selectedTab == 2, onClick = { actions.onTabChange(2) }, text = { Text("글 쓰기") })
        }
        if (state.selectedTab == 0 || state.selectedTab == 1) {
            TextButton(
                onClick = { actions.onOpenManual(state.selectedTab) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = state.uiColors.primary)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("설명서", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ScreenDialogs(
    state: DccleanerScreenState,
    actions: DccleanerScreenActions,
    showProxyCleanerQuote: Boolean,
    onShowProxyCleanerQuoteChange: (Boolean) -> Unit
) {
    if (state.showErrorDialog) {
        ErrorDialog(
            uiColors = state.uiColors,
            title = errorDialogTitle(state.errorMessage),
            errorMessage = state.errorMessage,
            onDismiss = actions.onDismissError
        )
    }
    if (state.deleteTaskToRemove != null) {
        DeleteTaskRecordDialog(
            uiColors = state.uiColors,
            onConfirm = actions.onConfirmDeleteTaskRecord,
            onDismiss = actions.onDismissDeleteTaskRecord
        )
    }
    if (state.showDeleteConfirmDialog) {
        val galleryMap = if (state.deleteType == "posting") state.postingGallList else state.commentGallList
        StartDeletionDialog(
            uiColors = state.uiColors,
            deleteType = state.deleteType,
            selectedGalleryCount = state.selectedGallList.size,
            selectedGalleryNames = state.selectedGallList.mapNotNull(galleryMap::get),
            preview = state.deletionPreview,
            previewLoading = state.isInspectingDeletion,
            previewMessage = state.deletionInspectionMessage,
            questionPostsMayBeExcluded = state.deleteType == "posting" && !state.deleteQuestionPosts,
            activeFilters = state.activeFilters,
            onConfirm = actions.onConfirmStartDeletion,
            onDismiss = actions.onDismissStartDeletion
        )
    }
    if (state.showDeleteProgressDialog) {
        TaskProgressDialog {
            DeleteProgressCard(
                uiColors = state.uiColors,
                deleteType = state.progressDeleteType,
                isCompleted = state.isCompleted,
                isDeleting = state.isDeleting,
                totalPosts = state.displayedTotalCount,
                deletedPosts = state.displayedDeletedCount,
                currentProgress = state.displayedProgress,
                estimatedTimeLeft = state.estimatedTimeLeft,
                nextCaptchaEstimatedTimeLeft = state.nextCaptchaEstimatedTimeLeft,
                isTwoCaptchaConfigured = state.isTwoCaptchaConfigured,
                currentGallery = state.displayedGallery,
                deleteLog = state.displayedDeleteLog,
                onClose = actions.onCloseDeleteProgress,
                onComplete = actions.onCompleteDeleteProgress,
                onStop = actions.onStopDeleteRequest,
                onOpenProxyCleaner = { onShowProxyCleanerQuoteChange(true) }
            )
        }
    }
    if (state.showStopDeleteDialog) {
        StopDeleteDialog(
            uiColors = state.uiColors,
            onConfirm = actions.onConfirmStopDelete,
            onDismiss = actions.onDismissStopDelete
        )
    }
    if (state.showCaptchaDialog) {
        CaptchaDialog(
            uiColors = state.uiColors,
            onOpenGallog = actions.onOpenGallogForCaptcha,
            onResolveCaptcha = actions.onResolveCaptcha,
            onOpenProxyCleaner = { onShowProxyCleanerQuoteChange(true) }
        )
    }
    if (showProxyCleanerQuote) {
        ProxyCleanerQuoteDialog(
            uiColors = state.uiColors,
            initialPostCount = state.loginInfo?.article_num?.toCount() ?: 0,
            initialCommentCount = state.loginInfo?.comment_num?.toCount() ?: 0,
            gallList = if (state.deleteType == "posting") state.postingGallList else state.commentGallList,
            selectedGalleries = state.selectedGallList,
            onDismiss = { onShowProxyCleanerQuoteChange(false) }
        )
    }
    if (state.showDeleteAccountDialog && state.accountToDelete != null) {
        DeleteAccountDialog(
            uiColors = state.uiColors,
            accountToDelete = state.accountToDelete,
            onConfirm = actions.onConfirmDeleteAccount,
            onDismiss = actions.onDismissDeleteAccount
        )
    }
    if (state.showDaewangconDialog) {
        DaewangconStartDialog(
            uiColors = state.uiColors,
            onStart = actions.onConfirmStartDaewangcon,
            onDismiss = actions.onDismissStartDaewangcon
        )
    }
    if (state.showDaewangconProgressDialog) {
        TaskProgressDialog {
            DaewangconProgressCard(
                uiColors = state.uiColors,
                isRunning = state.isDaewangconRunning,
                isCompleted = state.isDaewangconCompleted,
                errorMessage = state.daewangconErrorMessage,
                progress = state.daewangconProgress,
                logs = state.daewangconLog,
                postCount = state.daewangconPostCount,
                commentCount = state.daewangconCommentCount,
                onClose = actions.onCloseDaewangconProgress,
                onStop = actions.onStopDaewangconRequest
            )
        }
    }
    if (state.showGuestbookProgressDialog) {
        TaskProgressDialog {
            GuestbookProgressCard(
                uiColors = state.uiColors,
                isSending = state.guestbookIsSending,
                progressDone = state.guestbookProgressDone,
                progressTotal = state.guestbookProgressTotal,
                successCount = state.guestbookSuccessCount,
                failCount = state.guestbookFailCount,
                onClose = actions.onCloseGuestbookProgress
            )
        }
    }
    if (state.showStopDaewangconDialog) {
        StopDaewangconDialog(
            uiColors = state.uiColors,
            onConfirm = actions.onConfirmStopDaewangcon,
            onDismiss = actions.onDismissStopDaewangcon
        )
    }
}

private fun String.toCount(): Int = filter(Char::isDigit).toIntOrNull() ?: 0

private fun errorDialogTitle(errorMessage: String): String =
    when {
        errorMessage.startsWith("로그인에 실패") -> "로그인 실패"
        else -> "오류"
    }
