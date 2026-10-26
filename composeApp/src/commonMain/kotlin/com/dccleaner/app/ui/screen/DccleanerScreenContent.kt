package com.dccleaner.app.ui.screen

import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.ui.proxy.ChargeRequestDialog
import com.dccleaner.app.ui.proxy.ChargeStage
import com.dccleaner.app.ui.proxy.applicationText
import com.dccleaner.app.model.DeleteItemCounts
import com.dccleaner.app.model.DeleteTimeEstimator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.UserInfo
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
import com.dccleaner.app.ui.card.UpdateNotice
import com.dccleaner.app.ui.cleaner.DcCleanerTabContent
import com.dccleaner.app.ui.cleaner.deleteTypeLabel
import com.dccleaner.app.ui.cleaner.TwoCaptchaSetupDialog
import com.dccleaner.app.ui.dialog.CaptchaDialog
import com.dccleaner.app.ui.dialog.DeleteDialogPhase
import com.dccleaner.app.ui.dialog.DeleteAccountDialog
import com.dccleaner.app.ui.dialog.DeleteTaskRecordDialog
import com.dccleaner.app.ui.dialog.ErrorDialog
import com.dccleaner.app.ui.dialog.StartDeletionDialog
import com.dccleaner.app.ui.dialog.StopDeleteDialog
import com.dccleaner.app.ui.dialog.ProxyCleanerQuoteDialog
import com.dccleaner.app.ui.guestbook.GuestbookTabContent
import com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult
import com.dccleaner.app.platform.fetchRemoteBannerConfig
import com.dccleaner.app.model.PRIVACY_POLICY_URL
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.proxyGalleryScope
import com.dccleaner.app.model.DeletePhase
import com.dccleaner.app.model.ProxyFollowUp
import com.dccleaner.app.ui.dialog.CommentFirstAdviceMode
import com.dccleaner.app.ui.dialog.buildProxyCleanerApplicationMessage
import com.dccleaner.app.model.withDeleteProgressMarked
import com.dccleaner.app.model.DELETE_PROGRESS_LOG_MARKER
import com.dccleaner.app.ui.dialog.TWO_CAPTCHA_DELETIONS_PER_HOUR
import com.dccleaner.app.ui.dialog.hourlyDeletionCount
import com.dccleaner.app.ui.dialog.formatNumber
import com.dccleaner.app.model.formatWon
import com.dccleaner.app.ui.dialog.speedMultiplier
import com.dccleaner.app.ui.dialog.ProxyStartSummary
import com.dccleaner.app.ui.proxy.ProxyCancelDialog
import com.dccleaner.app.ui.proxy.ProxyCleanerController
import com.dccleaner.app.ui.proxy.ProxyConsentDialog
import com.dccleaner.app.ui.proxy.ProxyCreditTabContent
import com.dccleaner.app.ui.ranking.GalleryRankingContent
import com.dccleaner.app.ui.ranking.galleryRankingBadge
import com.dccleaner.app.ui.userposts.UserPostSearchContent
import com.dccleaner.app.ui.userposts.userPostSearchBadge
import com.dccleaner.app.ui.proxy.ProxyPasswordDialog
import com.dccleaner.app.ui.proxy.ProxyStartingCard
import com.dccleaner.app.ui.proxy.ProxyHandOffDialog
import com.dccleaner.app.ui.proxy.ProxySyncHeader
import com.dccleaner.app.ui.proxy.UltraButton
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REMOTE_BANNER_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1_000L

data class DccleanerScreenState(
    val uiColors: UiColors,
    val isDarkTheme: Boolean,
    val id: String,
    val pw: String,
    val loginInfo: UserInfo?,
    val profileImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    val itemCounts: DeleteItemCounts = DeleteItemCounts(),
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
    val isCheckingDeletionSession: Boolean,
    val showDeleteConfirmDialog: Boolean,
    val activeFilters: List<String>,
    val showDeleteProgressDialog: Boolean,
    val progressDeleteType: String,
    /** 글+댓글을 순서대로 함께 지울지. 켜져 있으면 [deleteType]은 '지금 설정 중인 종류'다. */
    val deleteBoth: Boolean = false,
    /** 지울 대상을 하나도 고르지 않았다(처음 기본값). 이때 [deleteType]은 '시트에서 고칠 종류'일 뿐이다. */
    val noDeleteTarget: Boolean = false,
    /** 함께 지울 때 댓글을 먼저 지울지(기본은 댓글 먼저). */
    val commentFirst: Boolean = true,
    /** 함께 지울 때, 설정 중이 아닌 다른 종류의 선택 갤러리와 적용 조건. */
    val otherSelectedGallList: List<String> = emptyList(),
    /** 함께 지울 때 설정 중이 아닌 종류의 단계 설정(갤러리·날짜 조건·최근부터). 초고속 이어 삭제에 쓴다. */
    val otherPhase: DeletePhase? = null,
    val otherActiveFilters: List<String> = emptyList(),
    /** 지금 도는 작업이 함께 지우기면 단계 순서(예: ["comment", "posting"]), 아니면 빈 목록. */
    val runningPhases: List<String> = emptyList(),
    val isCompleted: Boolean,
    val isDeleting: Boolean,
    val estimatedTimeLeft: Long,
    val currentDeletionSpeedPerHour: Int,
    val nextCaptchaEstimatedTimeLeft: Long,
    val isTwoCaptchaConfigured: Boolean,
    val showStopDeleteDialog: Boolean,
    val showCaptchaDialog: Boolean,
    val accountToDelete: SavedAccount?,
    val showDeleteAccountDialog: Boolean,
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
    val onDeleteAllTasks: () -> Unit,
    val onTabChange: (Int) -> Unit,
    val onDeveloperModeToggle: () -> Unit,
    /** 초고속 삭제(서버 작업)가 끝났을 때. 직접 삭제처럼 글·댓글 수와 갤러리 목록을 다시 불러온다. */
    val onRemoteDeletionFinished: () -> Unit = {},
    /** 충전 신청서가 확인됐거나 충전됐다. 사용자는 보통 카카오톡에 가 있으니 앱이 화면에 없으면 알림으로 띄운다. */
    val onChargeNotice: (String) -> Unit = {},
    /** 신청 창이 열려 충전을 기다리는 신청(없으면 null). 백그라운드에서 앱이 멈추는 플랫폼은 이걸로 따로 확인을 돌린다. */
    val onChargeWaiting: (CreditPurchase?) -> Unit = {},
    /** 초고속 삭제로 바꾸려고 직접 삭제를 끝낸다(이어하기 기록도 지운다). 이미 끝나는 중이라 끝낼 게 없으면 false. */
    val onStopForProxyHandOff: suspend () -> Boolean = { false },
    val onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    val onStopCommentCleaner: () -> Unit,
    val onDeleteTypeChange: (String) -> Unit,
    /** 지울 대상 체크가 바뀌었다. 빈 집합·한 종류·두 종류 모두 올 수 있다. */
    val onDeleteTargetsChange: (Set<String>) -> Unit = {},
    val onCommentFirstChange: (Boolean) -> Unit = {},
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
    localBannerJson: String? = null,
    snackbarHostState: SnackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() },
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    applySystemBarsPadding: Boolean = false,
    maxContentWidth: Dp = Dp.Unspecified,
    proxyCleaner: ProxyCleanerController? = null
) {
    val tabScrollStates = List(USER_POSTS_TAB + 1) { rememberScrollState() }
    val scrollState = tabScrollStates[state.selectedTab]
    // 갤창랭킹·반고닉 글 찾기는 탭을 떠났다 와도 결과의 보기 설정(순위 수·이미지/텍스트 등)과 입력이 남게 한다. 로그아웃하면 지운다.
    val subpageStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(state.loginInfo == null) {
        if (state.loginInfo == null) {
            subpageStateHolder.removeState(RANKING_TAB)
            subpageStateHolder.removeState(USER_POSTS_TAB)
        }
    }
    val uriHandler = LocalUriHandler.current
    var remoteBanner by remember { mutableStateOf<RemoteBannerConfig?>(null) }
    var dismissedBannerId by remember { mutableStateOf<String?>(null) }
    var showProxyCleanerQuote by remember { mutableStateOf(false) }
    var showTwoCaptchaSetup by remember { mutableStateOf(false) }
    var showAppInfo by remember { mutableStateOf(false) }
    val openProxyCleanerQuote = { showProxyCleanerQuote = true }
    // 견적 화면을 열 때마다 최근 충전 활동을 새로 읽는다(처음 보는 사람이 가장 많이 거치는 화면).
    LaunchedEffect(showProxyCleanerQuote) { if (showProxyCleanerQuote) proxyCleaner?.loadPurchaseActivity() }
    var proxyConsentVisible by remember { mutableStateOf(false) }
    var proxyPasswordVisible by remember { mutableStateOf(false) }
    var proxyCancelVisible by remember { mutableStateOf(false) }
    var proxyMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 충전 신청서와 오픈채팅 주소. 카카오톡으로 넘어가기 전에 남은 단계를 보여 준다.
    var chargeRequest by remember { mutableStateOf<Pair<CreditPurchase, String>?>(null) }
    /** 신청 창이 열려 있는 동안 실제로 충전된 개수. 있으면 창이 마지막 단계(충전됐어요)를 보여 준다. */
    var chargedCount by remember { mutableStateOf<Long?>(null) }
    var creditInitialCounts by remember(state.loginInfo?.userId) { mutableStateOf<Pair<Int, Int>?>(null) }
    var showProxyCredits by remember(state.loginInfo?.userId) { mutableStateOf(false) }
    val creditsVisible = state.selectedTab == 3 || showProxyCredits
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val proxyUserId = state.id.ifBlank { state.loginInfo?.userId.orEmpty() }
    // Keep existing local-only conditions; never silently drop them to enable server deletion.
    // 글+댓글은 서버가 한 작업에서 이어 지운다(초고속 이어 삭제). 서버가 앱보다 먼저 배포된다.
    val runOnServer = proxyCleaner?.runOnServer == true && !state.newestFirstAny && !state.regexFilterEnabled
    // The mode is fixed when the confirmation dialog opens so late balance reads can't change what "시작" does.
    val dialogUseProxy = remember(state.showDeleteConfirmDialog) { mutableStateOf(runOnServer) }
    var pendingLocalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 직접 삭제 중 충전되면 초고속 삭제로 바꿀지 묻는다(충전된 개수).
    var handOffOffer by remember { mutableStateOf<Long?>(null) }
    var handingOff by remember { mutableStateOf(false) }

    /**
     * 직접 삭제를 끝내고(이어하기 기록도 지운다) 클리너의 삭제 시작 창을 초고속으로 연다.
     * 최근부터·정규식·삭제 순서 같은 초고속 조건 안내는 그 창이 하므로 여기서 따로 거르지 않는다.
     */
    fun switchToProxyStart() {
        coroutineScope.launch {
            withContext(NonCancellable) {
                handingOff = true
                val stopped = try { actions.onStopForProxyHandOff() } finally { handingOff = false }
                if (!stopped) {
                    proxyMessage = "초고속 삭제로\n바꾸지 못했습니다" to "직접 삭제가 이미 끝나는 중이에요. 끝난 뒤 클리너에서 초고속 삭제를 시작해 주세요."
                    return@withContext
                }
                actions.onCloseDeleteProgress()
                // 삭제 중 충전은 전체 화면으로 열려 있으니 같이 닫아야 클리너 탭과 시작 창이 보인다.
                showProxyCredits = false
                creditInitialCounts = null
                proxyCleaner?.serverOptOut = false
                actions.onTabChange(0)
                actions.onShowDeleteDialog()
            }
        }
    }

    fun launchProxyStart(password: String) {
        val controller = proxyCleaner ?: return
        if (state.newestFirstAny) {
            proxyMessage = "초고속 삭제를 시작하지 못했습니다" to "최근 글부터 삭제는 초고속 삭제에서 지원하지 않아요. 옵션을 끄고 다시 시도해 주세요."
            return
        }
        if (state.regexFilterEnabled) {
            proxyMessage = "초고속 삭제를 시작하지 못했습니다" to "정규식 조건은 직접 삭제에서만 사용할 수 있어요."
            return
        }
        // 서버는 빈 갤러리 목록을 '전체 갤러리'로 본다. 확인 창이 열린 사이 새로고침으로 선택이 비면 고른 것보다 많이 지우므로 막는다.
        val types = if (state.deleteBoth) listOf("posting", "comment") else listOf(state.deleteType)
        types.firstOrNull { state.valuesOf(it).selection.isEmpty() }?.let { type ->
            proxyMessage = "초고속 삭제를 시작하지 못했습니다" to
                if (state.deleteBoth) "${deleteTypeLabel(type)}을 지울 갤러리를 먼저 골라 주세요." else "삭제할 갤러리를 먼저 골라 주세요."
            return
        }
        coroutineScope.launch {
            controller.start(state.firstDeleteType(), state.toProxyJobConfig(), proxyUserId, password)
                ?.let { proxyMessage = "초고속 삭제를 시작하지 못했습니다" to it }
        }
    }

    fun continueProxyStart() {
        when {
            state.pw.isNotBlank() -> launchProxyStart(state.pw)
            else -> proxyPasswordVisible = true
        }
    }

    fun beginProxyStart() {
        val controller = proxyCleaner ?: return
        // Closes the local confirmation dialog and cancels its session check.
        actions.onDismissStartDeletion()
        if (controller.hasConsent()) continueProxyStart() else proxyConsentVisible = true
    }

    fun openCredits(postCount: Int, commentCount: Int) {
        creditInitialCounts = postCount to commentCount
        showProxyCleanerQuote = false
        // 삭제 확인 창에서 왔다면 창을 닫지 않고 위에 띄워, 충전 뒤 그대로 시작할 수 있게 한다.
        if (state.deleteUiActive || state.showDeleteProgressDialog || state.showCaptchaDialog || state.showDeleteConfirmDialog) {
            showProxyCredits = true
        } else {
            actions.onDismissStartDeletion()
            actions.onTabChange(3)
        }
    }

    fun copyChargeApplication(purchase: CreditPurchase) {
        // 신청 코드만으로 서버가 신청을 찾으므로 두 줄이면 된다. 코드를 주지 않는 이전 서버면 예전 신청서(신청 ID 포함)를 쓴다.
        clipboard.setText(AnnotatedString(purchase.applicationText()
            ?: "${buildProxyCleanerApplicationMessage(purchase.requestedCount, purchase.amountWon)}\n사용자 코드: ${proxyCleaner?.userCode.orEmpty()}\n충전 신청 ID: ${purchase.id}"))
    }

    val creditContent: @Composable () -> Unit = {
        if (proxyCleaner != null) ProxyCreditTabContent(
            uiColors = state.uiColors,
            controller = proxyCleaner,
            initialPostCount = creditInitialCounts?.first ?: state.loginInfo?.article_num?.toCount() ?: 0,
            initialCommentCount = creditInitialCounts?.second ?: state.loginInfo?.comment_num?.toCount() ?: 0,
            onRefresh = { coroutineScope.launch { if (proxyCleaner.pricing == null) proxyCleaner.loadPricing(); proxyCleaner.refresh() } },
            onOpenUrl = openUrl,
            onOpenProgress = if (showProxyCredits) null else { { proxyCleaner.showProgress = true } },
            onCharge = { kakaoUrl, requestedCount ->
                coroutineScope.launch {
                    val (purchase, error) = proxyCleaner.createPurchase(requestedCount)
                    if (purchase == null || proxyCleaner.userCode == null) {
                        proxyMessage = "충전 견적을 확인하지 못했습니다" to error.orEmpty()
                        return@launch
                    }
                    copyChargeApplication(purchase)
                    chargedCount = null
                    chargeRequest = purchase to kakaoUrl
                }
            },
            onShowPendingCharge = { purchase ->
                val kakaoUrl = proxyCleaner.pricing?.kakaoOpenChatUrl
                if (kakaoUrl != null) { chargedCount = null; chargeRequest = purchase to kakaoUrl }
                else proxyMessage = "가격 정보를 불러오지 못했어요" to "새로고침한 뒤 다시 시도해 주세요."
            }
        )
    }

    if (proxyCleaner != null) {
        // Without a charged account this is a local no-op; the account is created on "충전하기".
        LaunchedEffect(state.loginInfo?.userId) {
            if (state.loginInfo == null) return@LaunchedEffect
            // 계정마다 서버 작업이 따로 있으므로 로그인한 계정의 작업을 보여 준다.
            proxyCleaner.accountId = proxyUserId
            proxyCleaner.refresh()
            proxyCleaner.showProgress = proxyCleaner.hasActiveJob
        }
        LaunchedEffect(creditsVisible || state.showDeleteConfirmDialog) {
            if ((creditsVisible || state.showDeleteConfirmDialog) && proxyCleaner.userCode == null) {
                proxyCleaner.refresh()
            }
        }
        // Picks up operator charges without a manual refresh: quickly on the balance tab, slowly elsewhere.
        // 신청 창이 열려 있으면 단계가 바로 바뀌도록 더 자주 본다.
        val chargeWatching = chargeRequest != null && chargedCount == null
        val waitingPurchase = chargeRequest?.first?.takeIf { chargeWatching }
        LaunchedEffect(waitingPurchase?.id) { actions.onChargeWaiting(waitingPurchase) }
        LaunchedEffect(proxyCleaner.userCode != null, creditsVisible, chargeWatching) {
            if (proxyCleaner.userCode == null) return@LaunchedEffect
            while (true) {
                delay(if (chargeWatching) 3_000L else if (creditsVisible) 5_000L else 30_000L)
                val pending = proxyCleaner.purchase.takeIf { !proxyCleaner.requestSeen }
                val charged = proxyCleaner.refreshBalance()
                if (pending != null && proxyCleaner.requestSeen && proxyCleaner.purchase?.id == pending.id) {
                    actions.onChargeNotice("신청서를 확인했어요. 오픈채팅에서 ${formatWon(pending.amountWon.toDouble())}을 송금해 주세요.")
                }
                if (charged > 0) actions.onChargeNotice("충전됐어요 · ${formatNumber(charged)}개 삭제할 수 있어요")
                if (charged > 0) handOffOffer = charged
                if (charged > 0 && chargeRequest != null) chargedCount = charged
                else if (charged > 0) snackbarHostState.showSnackbar("${formatNumber(charged)}개 삭제할 수 있도록 충전되었어요")
            }
        }
        // 직접 삭제가 돌지 않으면 물을 게 없다. 나중에 시작한 직접 삭제에 예전 충전으로 묻지 않게 지운다.
        LaunchedEffect(handOffOffer, state.isDeleting) { if (!state.isDeleting) handOffOffer = null }
        LaunchedEffect(Unit) { proxyCleaner.loadPricing() }
        // 중단 뒤 정산 대기 중일 때도 계속 갱신해야 정산 결과와 잔고가 화면에 반영된다.
        LaunchedEffect(proxyCleaner.job?.id, proxyCleaner.hasActiveJob, proxyCleaner.job?.needsSettlement) {
            if (proxyCleaner.hasActiveJob || proxyCleaner.job?.needsSettlement == true) proxyCleaner.pollActiveJob()
        }
        // 중단 후 정산이 끝날 때까지 서버가 마저 지울 수 있으므로 정산까지 기다렸다가 새로고침한다.
        val proxyJobRunning = proxyCleaner.hasActiveJob || proxyCleaner.job?.needsSettlement == true
        var proxyJobWasRunning by remember { mutableStateOf(false) }
        LaunchedEffect(proxyJobRunning) {
            if (proxyJobWasRunning && !proxyJobRunning) actions.onRemoteDeletionFinished()
            proxyJobWasRunning = proxyJobRunning
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            remoteBanner = fetchRemoteBannerConfig(localBannerJson)
            delay(REMOTE_BANNER_REFRESH_INTERVAL_MILLIS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(state.uiColors.background)
            .then(if (applySystemBarsPadding) Modifier.systemBarsPadding().imePadding() else Modifier),
        containerColor = state.uiColors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HeaderCard(state, actions)
        },
        bottomBar = {
            if (state.loginInfo != null && !state.deleteUiActive) TabsCard(state, actions, showCredits = proxyCleaner != null)
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (maxContentWidth != Dp.Unspecified) Modifier.widthIn(max = maxContentWidth) else Modifier)
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                if (!state.deleteUiActive) {
                    UpdateNotice(state.currentVersion, state.latestVersion, state.isCheckingVersion,
                        modifier = Modifier.padding(vertical = 8.dp), onUpdateClick = actions.onOpenUpdate)
                }
                if (state.deleteUiActive) {
                    DeleteRunningContent(
                        uiColors = state.uiColors,
                        loginId = state.runningLoginId,
                        currentGallery = state.displayedGallery,
                        progress = state.displayedProgress,
                        deletedCount = state.displayedDeletedCount,
                        itemCounts = state.itemCounts,
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
                    // 로그인 전에는 더보기 탭이 없으므로 처리방침은 여기서만 열 수 있다.
                    TextButton(onClick = { openUrl(PRIVACY_POLICY_URL) }, modifier = Modifier.fillMaxWidth()) {
                        Text("개인정보처리방침", color = state.uiColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
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
                        profileImage = state.profileImage
                    )
                    Spacer(Modifier.height(12.dp))
                    // 이어하기 상자는 클리너 탭에서만 보인다.
                    if (state.selectedTab == 0) {
                        InterruptedDeleteTasksCard(
                            uiColors = state.uiColors,
                            tasks = state.interruptedTasks,
                            resumeEnabled = state.resumeEnabled,
                            restoringTaskId = state.restoringTaskId,
                            focusedTaskId = state.focusedTaskId,
                            onResume = actions.onResumeTask,
                            onDelete = actions.onDeleteTask,
                            onDeleteAll = actions.onDeleteAllTasks
                        )
                        if (state.interruptedTasks.isNotEmpty()) Spacer(Modifier.height(20.dp))
                    }
                    if (!state.showDeleteProgressDialog) {
                        when (state.selectedTab) {
                            0 -> Column {
                                if (proxyCleaner?.hasActiveJob == true) {
                                    UltraButton(
                                        "초고속 삭제 진행 중 · 진행 상황 보기",
                                        onClick = { proxyCleaner.showProgress = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(20.dp))
                                }
                                DcCleanerTabContent(
                                uiColors = state.uiColors,
                                postingGallList = state.postingGallList,
                                commentGallList = state.commentGallList,
                                deleteType = state.deleteType,
                                onDeleteTypeChange = actions.onDeleteTypeChange,
                                isTwocaptchaValid = state.isTwocaptchaValid,
                                onShowDeleteDialog = actions.onShowDeleteDialog,
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
                                onOpenProxyCleaner = openProxyCleanerQuote,
                                runOnServer = runOnServer,
                                onRequireLocal = { apply -> pendingLocalAction = apply },
                                proxySpeedPerHour = proxyCleaner?.pricing?.let { hourlyDeletionCount(it.deletionsPerSecond) },
                                deleteBoth = state.deleteBoth,
                                noDeleteTarget = state.noDeleteTarget,
                                onDeleteTargetsChange = actions.onDeleteTargetsChange,
                                commentFirst = state.commentFirst,
                                onCommentFirstChange = actions.onCommentFirstChange,
                                otherSelectedCount = state.otherSelectedGallList.size,
                                activeFilters = state.activeFilters,
                                otherActiveFilters = state.otherActiveFilters
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
                                onStartGuestbookSend = actions.onStartGuestbookSend,
                                onOpenRanking = { actions.onTabChange(RANKING_TAB) }
                            )
                            COMMENT_CLEANER_TAB -> CommentCleanerTabContent(
                                running = state.commentCleanerRunning,
                                onStartCommentCleaner = actions.onStartCommentCleaner,
                                onStopCommentCleaner = actions.onStopCommentCleaner,
                                uiColors = state.uiColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                            3 -> creditContent()
                            MORE_TAB -> MoreTabContent(
                                uiColors = state.uiColors,
                                commentCleanerStatus = if (state.commentCleanerRunning) "사용 중" else null,
                                guestbookStatus = if (state.guestbookIsSending)
                                    "보내는 중 ${state.guestbookProgressDone}/${state.guestbookProgressTotal}" else null,
                                isDarkTheme = state.isDarkTheme,
                                currentVersion = state.currentVersion,
                                onOpenCommentCleaner = { actions.onTabChange(COMMENT_CLEANER_TAB) },
                                onOpenGuestbook = { actions.onTabChange(GUESTBOOK_TAB) },
                                rankingStatus = galleryRankingBadge(),
                                onOpenRanking = { actions.onTabChange(RANKING_TAB) },
                                userPostsStatus = userPostSearchBadge(),
                                onOpenUserPosts = { actions.onTabChange(USER_POSTS_TAB) },
                                onDarkThemeChange = actions.onDarkThemeChange,
                                onOpenManual = { openUrl(HELP_URL) },
                                onAppInfo = { showAppInfo = true },
                                onOpenPrivacy = { openUrl(PRIVACY_POLICY_URL) },
                                onLogout = actions.onLogoutClick
                            )
                            RANKING_TAB -> subpageStateHolder.SaveableStateProvider(RANKING_TAB) {
                                GalleryRankingContent(
                                    uiColors = state.uiColors,
                                    // 받는 사람만 채우고, 메시지와 보내기는 방명록 화면에서 직접 한다.
                                    onSendGuestbook = { ids ->
                                        actions.onGuestbookUserListTextChange(ids.joinToString("\n"))
                                        actions.onTabChange(GUESTBOOK_TAB)
                                    },
                                    guestbookSending = state.guestbookIsSending
                                )
                            }
                            USER_POSTS_TAB -> subpageStateHolder.SaveableStateProvider(USER_POSTS_TAB) {
                                UserPostSearchContent(uiColors = state.uiColors)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            PlatformVerticalScrollbar(
                scrollState = scrollState,
                uiColors = state.uiColors,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 8.dp, bottom = 8.dp, end = 4.dp)
            )

            if (!showProxyCredits) SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            if (state.restoringTaskId != null) {
                DeleteTaskRestoreOverlay(state.uiColors, state.restoringMessage)
            }
            ScreenDialogs(
                state = state,
                actions = actions,
                showProxyCleanerQuote = showProxyCleanerQuote,
                onShowProxyCleanerQuoteChange = { if (it) openProxyCleanerQuote() else showProxyCleanerQuote = false },
                showTwoCaptchaSetup = showTwoCaptchaSetup,
                onShowTwoCaptchaSetupChange = { showTwoCaptchaSetup = it },
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState,
                proxySummary = proxyCleaner?.let { controller -> state.proxyStartSummary(controller) },
                purchaseActivity = proxyCleaner?.purchaseActivity,
                useProxy = dialogUseProxy.value,
                onDisableNewestFirst = {
                    proxyCleaner?.let {
                        // 잔고가 없어도 켠다. 확인 창이 가격과 충전 버튼을 바로 보여 준다.
                        actions.onDeleteNewestFirstChange(false)
                        // 함께 지울 때 다른 종류에 켜져 있으면 그쪽도 끈다(잠깐 그 종류로 바꿔 끄고 되돌린다).
                        if (state.deleteBoth && state.otherPhase?.deleteNewestFirst == true) {
                            val editing = state.deleteType
                            actions.onDeleteTypeChange(if (editing == "posting") "comment" else "posting")
                            actions.onDeleteNewestFirstChange(false)
                            actions.onDeleteTypeChange(editing)
                        }
                        it.serverOptOut = false
                        dialogUseProxy.value = true
                    }
                },
                onUseProxyChange = { enable ->
                    val controller = proxyCleaner
                    when {
                        controller == null -> Unit
                        !enable -> { controller.serverOptOut = true; dialogUseProxy.value = false }
                        else -> { controller.serverOptOut = false; dialogUseProxy.value = true }
                    }
                },
                onConfirmProxyStart = ::beginProxyStart,
                onOpenCredits = proxyCleaner?.let { { posts, comments -> openCredits(posts, comments) } },
                // 창 안에서 바꾸면 눈에 띄지 않고, 갤러리·조건이 함께 바뀌므로 창을 닫고 맨 위에서 확인하게 한다.
                // 함께 지우는 중이면 창을 닫지 않고 순서만 댓글 먼저로 바꾼다(단계 목록이 바로 바뀐다).
                onReorderCommentFirst = { actions.onCommentFirstChange(true) },
                onSwitchToComments = {
                    actions.onDismissStartDeletion()
                    // 지울 대상을 댓글로 바꾸면 설정 중인 종류도 같이 바뀌고, 고른 대상이 저장된다.
                    actions.onDeleteTargetsChange(setOf("comment"))
                    coroutineScope.launch {
                        scrollState.animateScrollTo(0)
                        snackbarHostState.showSnackbar("댓글 삭제로 바꿨어요. 갤러리와 조건을 확인하고 시작해 주세요")
                    }
                }
            )
            val proxyJob = proxyCleaner?.job
            if (proxyCleaner != null && proxyCleaner.showProgress && proxyJob != null && !state.showDeleteProgressDialog) {
                TaskProgressDialog {
                    ProxyJobProgressCard(state.uiColors, proxyJob, proxyCleaner,
                        onClose = { proxyCleaner.showProgress = false },
                        onStop = { proxyCancelVisible = true })
                }
            } else if (proxyCleaner?.isStarting == true || handingOff) {
                TaskProgressDialog { ProxyStartingCard(state.uiColors, stoppingLocal = handingOff) }
            }
            if (proxyCleaner != null) {
                if (showProxyCredits) Dialog(
                    onDismissRequest = { showProxyCredits = false; creditInitialCounts = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(Modifier.fillMaxSize(), color = state.uiColors.background) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("삭제 개수 충전", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = { showProxyCredits = false; creditInitialCounts = null }) { Text("닫기") }
                            }
                            HorizontalDivider(color = state.uiColors.outline)
                            Box(Modifier.weight(1f)) {
                                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                                    creditContent()
                                }
                                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
                if (proxyConsentVisible) ProxyConsentDialog(
                    uiColors = state.uiColors,
                    onAgree = {
                        proxyConsentVisible = false
                        if (proxyCleaner.saveConsent()) continueProxyStart()
                        else proxyMessage = "동의를 저장하지 못했습니다" to "잠시 후 다시 시도해 주세요."
                    },
                    onOpenUrl = openUrl,
                    onDismiss = { proxyConsentVisible = false }
                )
                if (proxyPasswordVisible) ProxyPasswordDialog(
                    uiColors = state.uiColors,
                    userId = proxyUserId,
                    onConfirm = { password ->
                        proxyPasswordVisible = false
                        launchProxyStart(password)
                    },
                    onDismiss = { proxyPasswordVisible = false }
                )
                if (proxyCancelVisible) ProxyCancelDialog(
                    uiColors = state.uiColors,
                    onConfirm = {
                        proxyCancelVisible = false
                        coroutineScope.launch {
                            proxyCleaner.cancel()?.let { proxyMessage = "작업을 중단하지 못했습니다" to it }
                        }
                    },
                    onDismiss = { proxyCancelVisible = false }
                )
                pendingLocalAction?.let { apply ->
                    AlertDialog(
                        onDismissRequest = { pendingLocalAction = null },
                        title = { Text("초고속 삭제를 꺼야 사용할 수 있어요") },
                        text = { Text("정규식 조건과 최신순 삭제는 직접 삭제에서만 쓸 수 있어요. 초고속 삭제를 끄고 사용할까요?") },
                        confirmButton = {
                            TextButton(onClick = {
                                proxyCleaner.serverOptOut = true
                                pendingLocalAction = null
                                apply()
                            }) { Text("끄기") }
                        },
                        dismissButton = { TextButton(onClick = { pendingLocalAction = null }) { Text("취소") } }
                    )
                }
                chargeRequest?.let { (purchase, kakaoUrl) ->
                    ChargeRequestDialog(
                        uiColors = state.uiColors,
                        amountWon = purchase.amountWon,
                        creditedCount = chargedCount ?: purchase.creditedCount,
                        code = purchase.code,
                        stage = when {
                            chargedCount != null -> ChargeStage.CHARGED
                            proxyCleaner?.requestSeen == true && proxyCleaner.purchase?.id == purchase.id -> ChargeStage.TRANSFER
                            else -> ChargeStage.SEND_REQUEST
                        },
                        // 창은 충전될 때까지 열어 두고 단계가 바뀌는 걸 보여 준다. PC에서는 여기서 신청 코드를 보고 휴대폰에 친다.
                        onOpenKakao = {
                            copyChargeApplication(purchase)
                            openUrl(kakaoUrl)
                        },
                        onDismiss = { chargeRequest = null; chargedCount = null }
                    )
                }
                // 서버 작업이 이미 있으면 묻지 않고 직접 삭제를 이어 간다. 바꾸기를 누르면 직접 삭제를 끝내고 삭제 시작 창을 연다.
                handOffOffer?.takeIf {
                    state.isDeleting && chargeRequest == null && !state.showStopDeleteDialog && !proxyCleaner.hasActiveJob && !proxyCleaner.isStarting &&
                        !handingOff &&
                        (state.runningLoginId.isBlank() || state.runningLoginId.equals(proxyUserId, ignoreCase = true))
                }?.let {
                    // 이어 삭제 중이면 지금 단계부터만 센다(지운 개수는 단계마다 0부터 다시 센다).
                    val types = state.runningPhases.dropWhile { it != state.progressDeleteType }.ifEmpty { listOf(state.progressDeleteType) }
                    val remaining = (types.sumOf { type ->
                        (if (type == "posting") state.loginInfo?.article_num else state.loginInfo?.comment_num)?.toCount() ?: 0
                    } - state.itemCounts.deleted).coerceAtLeast(0)
                    ProxyHandOffDialog(
                        uiColors = state.uiColors,
                        remainingCount = remaining.toLong(),
                        balance = proxyCleaner.balance ?: 0L,
                        onConfirm = {
                            handOffOffer = null
                            switchToProxyStart()
                        },
                        onDismiss = { handOffOffer = null }
                    )
                }
                // 직접 삭제를 다시 이어 갈 때 진행 창이 잠시 뒤 열리므로, 그 뒤에 띄워 진행 창에 가려지지 않게 한다.
                proxyMessage?.takeIf { state.restoringTaskId == null }?.let { (title, message) ->
                    ErrorDialog(state.uiColors, title, message, onDismiss = { proxyMessage = null })
                }
                LaunchedEffect(state.selectedTab) {
                    if (state.selectedTab != 3) creditInitialCounts = null
                    if (state.selectedTab == 3) proxyCleaner.logEvent("proxy_tab_view")
                }
            }
        }
    }
    if (showAppInfo) {
        AlertDialog(
            onDismissRequest = { showAppInfo = false },
            title = { Text("디시클리너 모바일 & PC") },
            text = {
                VersionInfoCard(
                    uiColors = state.uiColors,
                    currentVersion = state.currentVersion,
                    latestVersion = state.latestVersion,
                    isCheckingVersion = state.isCheckingVersion,
                    onUpdateClick = actions.onOpenUpdate,
                    onDeveloperModeToggle = {
                        actions.onDeveloperModeToggle()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(if (state.developerMode) "개발자 모드 해제" else "개발자 모드 활성화")
                        }
                    }
                )
            },
            confirmButton = { TextButton(onClick = { showAppInfo = false }) { Text("닫기") } }
        )
    }
}

@Composable
private fun HeaderCard(
    state: DccleanerScreenState,
    actions: DccleanerScreenActions
) {
    // 댓글 자동 정리·방명록 등은 더보기 안의 화면이라 뒤로 가면 더보기로 돌아간다.
    val inMoreSubpage = state.loginInfo != null && !state.deleteUiActive &&
        state.selectedTab in MORE_SUBPAGES
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (inMoreSubpage) 4.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (inMoreSubpage) IconButton(onClick = { actions.onTabChange(MORE_TAB) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "더보기로 돌아가기")
        }
        Text(
            when {
                state.deleteUiActive -> "삭제 작업"
                state.loginInfo == null -> "디시클리너 모바일 & PC"
                else -> listOf("클리너", "방명록", "댓글 자동 정리", "충전", "더보기", "갤창랭킹", "반고닉 글 찾기")[state.selectedTab]
            },
            modifier = Modifier.weight(1f),
            style = if (state.loginInfo == null) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineMedium
        )
    }
}

/** 사용 방법 모음. 기능별 안내 링크를 이 페이지에서 고른다. */
const val HELP_URL = "https://dccleaner3.github.io/dccleaner/help"

const val GUESTBOOK_TAB = 1
const val COMMENT_CLEANER_TAB = 2
const val MORE_TAB = 4
const val RANKING_TAB = 5
const val USER_POSTS_TAB = 6
/** 더보기 안의 화면. 뒤로 가면 더보기로 돌아간다. */
val MORE_SUBPAGES = setOf(GUESTBOOK_TAB, COMMENT_CLEANER_TAB, RANKING_TAB, USER_POSTS_TAB)
private val MORE_TABS = MORE_SUBPAGES + MORE_TAB

@Composable
private fun TabsCard(state: DccleanerScreenState, actions: DccleanerScreenActions, showCredits: Boolean) {
    NavigationBar(containerColor = state.uiColors.card, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        // 자주 쓰는 클리너·충전만 아래에 두고 나머지는 더보기에 모은다.
        val items = listOfNotNull(
            Triple("클리너", Icons.Outlined.DeleteSweep, 0),
            if (showCredits) Triple("충전", Icons.Outlined.AccountBalanceWallet, 3) else null,
            Triple("더보기", Icons.Outlined.Menu, MORE_TAB)
        )
        items.forEach { (label, icon, tab) ->
            val selected = if (tab == MORE_TAB) state.selectedTab in MORE_TABS else state.selectedTab == tab
            NavigationBarItem(
                selected = selected,
                enabled = state.restoringTaskId == null,
                onClick = { actions.onTabChange(tab) },
                // 진행 중인 작업은 더보기 안 각 줄의 상태와 완료 알림이 알려 준다. 탭 점은 '새 소식'으로 읽혀 두지 않는다.
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = state.uiColors.primary,
                    selectedTextColor = state.uiColors.primary,
                    indicatorColor = state.uiColors.card
                )
            )
        }
    }
}

@Composable
private fun ScreenDialogs(
    state: DccleanerScreenState,
    actions: DccleanerScreenActions,
    showProxyCleanerQuote: Boolean,
    onShowProxyCleanerQuoteChange: (Boolean) -> Unit,
    showTwoCaptchaSetup: Boolean,
    onShowTwoCaptchaSetupChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    proxySummary: ProxyStartSummary?,
    purchaseActivity: PurchaseActivity?,
    useProxy: Boolean,
    onUseProxyChange: (Boolean) -> Unit,
    onDisableNewestFirst: () -> Unit,
    onConfirmProxyStart: () -> Unit,
    onOpenCredits: ((Int, Int) -> Unit)?,
    onSwitchToComments: () -> Unit,
    onReorderCommentFirst: () -> Unit
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
            checkingSession = state.isCheckingDeletionSession,
            // 함께 지우면 설정 중인 종류가 댓글이어도 게시글 단계가 있다.
            questionPostsMayBeExcluded = (state.deleteType == "posting" || state.deleteBoth) && !state.deleteQuestionPosts,
            activeFilters = state.activeFilters,
            onConfirm = if (proxySummary != null && useProxy) onConfirmProxyStart else actions.onConfirmStartDeletion,
            onDismiss = actions.onDismissStartDeletion,
            proxy = proxySummary,
            // 충전 탭처럼 갤로그의 글·댓글 수를 둘 다 채워 둔다. 필요하면 사용자가 줄인다.
            onCharge = { proxySummary?.let { onOpenCredits?.invoke(it.chargePostCount, it.chargeCommentCount) } },
            useProxy = useProxy,
            onUseProxyChange = onUseProxyChange,
            onDisableNewestFirst = onDisableNewestFirst,
            remainingCommentCount = state.loginInfo?.comment_num?.toCount() ?: 0,
            onSwitchToComments = onSwitchToComments,
            onReorderCommentFirst = onReorderCommentFirst,
            commentFirstAdvice = when {
                state.deleteBoth -> if (state.commentFirst) null else CommentFirstAdviceMode.REORDER
                state.deleteType == "posting" -> CommentFirstAdviceMode.SWITCH
                else -> null
            },
            phases = state.deletePhasesForDialog()
        )
    }
    if (state.showDeleteProgressDialog) {
        TaskProgressDialog {
            DeleteProgressCard(
                uiColors = state.uiColors,
                deleteType = state.progressDeleteType,
                currentGallery = state.displayedGallery,
                isCompleted = state.isCompleted,
                isDeleting = state.isDeleting,
                totalPosts = state.displayedTotalCount,
                deletedPosts = state.displayedDeletedCount,
                currentProgress = state.displayedProgress,
                estimatedTimeLeft = state.estimatedTimeLeft,
                currentDeletionSpeedPerHour = state.currentDeletionSpeedPerHour,
                nextCaptchaEstimatedTimeLeft = state.nextCaptchaEstimatedTimeLeft,
                isTwoCaptchaConfigured = state.isTwoCaptchaConfigured,
                deleteLog = state.displayedDeleteLog,
                onClose = actions.onCloseDeleteProgress,
                onComplete = actions.onCompleteDeleteProgress,
                onStop = actions.onStopDeleteRequest,
                onOpenProxyCleaner = { onShowProxyCleanerQuoteChange(true) },
                overallTimeLeft = state.overallTimeLeft(),
                subtitle = state.runningPhaseSubtitle()
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
    if (state.showCaptchaDialog && !state.showStopDeleteDialog) {
        CaptchaDialog(
            uiColors = state.uiColors,
            onOpenGallog = actions.onOpenGallogForCaptcha,
            onResolveCaptcha = actions.onResolveCaptcha,
            // 로그인 때 받은 글·댓글 수에서 이번 작업에서 이미 지운 개수를 뺀 나머지로 계산한다.
            totalItemCount = ((state.loginInfo?.article_num?.toCount() ?: 0).toLong() +
                (state.loginInfo?.comment_num?.toCount() ?: 0) - state.itemCounts.deleted).coerceAtLeast(0),
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
            onOpenTwoCaptchaSetup = { onShowTwoCaptchaSetupChange(true) },
            onDismiss = { onShowProxyCleanerQuoteChange(false) },
            onOpenCredits = onOpenCredits,
            purchaseActivity = purchaseActivity
        )
    }
    if (showTwoCaptchaSetup) {
        TwoCaptchaSetupDialog(
            uiColors = state.uiColors,
            twocaptchaKey = state.twocaptchaKey,
            onTwocaptchaKeyChange = actions.onTwocaptchaKeyChange,
            isTwocaptchaValid = state.isTwocaptchaValid,
            onTwocaptchaValidChange = actions.onTwocaptchaValidChange,
            isCheckingTwocaptcha = state.isCheckingTwocaptcha,
            onIsCheckingTwocaptchaChange = actions.onIsCheckingTwocaptchaChange,
            coroutine = coroutineScope,
            snackbarHostState = snackbarHostState,
            onValidateTwocaptchaKey = actions.onValidateTwocaptchaKey,
            onOpenAutoCaptchaGuide = actions.onOpenAutoCaptchaGuide,
            onDismiss = { onShowTwoCaptchaSetupChange(false) }
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
}

private fun String.toCount(): Int = filter(Char::isDigit).toIntOrNull() ?: 0

private fun errorDialogTitle(errorMessage: String): String =
    when {
        errorMessage.startsWith("로그인에 실패") -> "로그인 실패"
        else -> "오류"
    }

/** 지울 첫 종류. 함께 지우면 정한 순서의 앞쪽, 아니면 고른 한 종류. */
private fun DccleanerScreenState.firstDeleteType(): String =
    if (!deleteBoth) deleteType else if (commentFirst) "comment" else "posting"

private fun DccleanerScreenState.gallListOf(type: String) = if (type == "posting") postingGallList else commentGallList

/** 종류별로 따로인 값: 고른 갤러리·날짜 조건·작성 후 일수·최근부터. 설정 중인 종류는 화면 값, 다른 종류는 [otherPhase]. */
private class TypeValues(val selection: List<String>, val dateFilterEnabled: Boolean, val minPostAgeDays: Int, val newestFirst: Boolean)

private fun DccleanerScreenState.valuesOf(type: String): TypeValues =
    if (type == deleteType || otherPhase == null) {
        TypeValues(selectedGallList, dateFilterEnabled, minPostAgeDaysToDelete.toIntOrNull() ?: 5, deleteNewestFirst)
    } else otherPhase.let {
        TypeValues(it.selectedGalleries, it.dateFilterEnabled, it.minPostAgeDaysToDelete.takeIf { days -> days >= 0 } ?: 5,
            it.deleteNewestFirst)
    }

/** 최근부터 삭제는 초고속에서 못 쓴다. 함께 지우면 두 종류 중 하나라도 켜져 있으면 막는다. */
private val DccleanerScreenState.newestFirstAny: Boolean
    get() = deleteNewestFirst || (deleteBoth && otherPhase?.deleteNewestFirst == true)

private fun DccleanerScreenState.toProxyJobConfig(): ProxyJobConfig {
    val first = firstDeleteType()
    val firstValues = valuesOf(first)
    // Only options visible for the current mode are sent, so the server applies exactly what the
    // confirmation dialog listed (hidden toggles from the other mode stay behind).
    // 함께 지우면 두 종류 조건을 모두 보낸다. 서버가 단계마다 그 종류 조건만 쓴다.
    val posting = first == "posting" || deleteBoth
    val comment = first == "comment" || deleteBoth
    val second = if (deleteBoth) (if (first == "posting") "comment" else "posting") else null
    return ProxyJobConfig(
        galleries = proxyGalleryScope(firstValues.selection, gallListOf(first).keys),
        // Never sent: newest-first slows the server, so 초고속 삭제 refuses to start with it on.
        deleteNewestFirst = false,
        deleteQuestionPosts = posting && deleteQuestionPosts,
        recommendFilterEnabled = posting && recommendFilterEnabled,
        minRecommendToKeep = minRecommendToKeep.toIntOrNull() ?: 1,
        commentFilterEnabled = posting && commentFilterEnabled,
        minCommentToKeep = minCommentToKeep.toIntOrNull() ?: 1,
        viewFilterEnabled = posting && viewFilterEnabled,
        minViewToKeep = minViewToKeep.toIntOrNull() ?: 1,
        postContentFilterEnabled = posting && postContentFilterEnabled,
        postContentRegex = if (posting && postContentFilterEnabled) postContentRegex else "",
        commentContentFilterEnabled = comment && commentContentFilterEnabled,
        commentContentRegex = if (comment && commentContentFilterEnabled) commentContentRegex else "",
        myPostFilterEnabled = comment && myPostFilterEnabled,
        dcconOnlyFilterEnabled = comment && dcconOnlyFilterEnabled,
        dateFilterEnabled = firstValues.dateFilterEnabled,
        minPostAgeDays = firstValues.minPostAgeDays,
        excludedPostNumbers = emptyList(),
        recordGuestbookLog = recordGuestbookLog,
        followUp = second?.let { type ->
            val values = valuesOf(type)
            ProxyFollowUp(
                deleteType = type,
                galleries = proxyGalleryScope(values.selection, gallListOf(type).keys),
                dateFilterEnabled = values.dateFilterEnabled,
                minPostAgeDays = values.minPostAgeDays
            )
        }
    )
}

/**
 * 갤러리를 모두 골랐을 때만 메인 화면의 글·댓글 수로 전체 남은 시간을 낸다(다시 불러오지 않는다).
 * 로그인 시점 개수라 오차가 있어, 현재 갤러리 남은 시간보다 길 때만 보여 준다.
 */
private fun DccleanerScreenState.overallTimeLeft(): Long? {
    val gallList = gallListOf(progressDeleteType)
    // 함께 지우면 도는 단계가 설정 중인 종류와 다를 수 있어 그 종류의 선택으로 본다.
    if (gallList.isEmpty() || valuesOf(progressDeleteType).selection.toSet() != gallList.keys) return null
    val total = (if (progressDeleteType == "posting") loginInfo?.article_num else loginInfo?.comment_num)?.toCount() ?: return null
    return DeleteTimeEstimator.estimateRemainingSeconds(currentDeletionSpeedPerHour, itemCounts.deleted, itemCounts.skipped, total)
        .takeIf { it > estimatedTimeLeft }
}

private val DccleanerScreenState.regexFilterEnabled: Boolean
    get() = when {
        // 함께 지우면 두 종류 정규식이 모두 실행된다.
        deleteBoth -> postContentFilterEnabled || commentContentFilterEnabled
        deleteType == "posting" -> postContentFilterEnabled
        else -> commentContentFilterEnabled
    }

private fun DccleanerScreenState.proxyStartSummary(controller: ProxyCleanerController): ProxyStartSummary {
    val types = if (deleteBoth) listOf("posting", "comment") else listOf(deleteType)
    val total = types.sumOf { type ->
        (if (type == "posting") loginInfo?.article_num else loginInfo?.comment_num)?.toCount() ?: 0
    }
    return ProxyStartSummary(
        estimatedCount = total,
        // A gallery subset or any filter can only lower the account total.
        estimateIsUpperBound = types.any { proxyGalleryScope(valuesOf(it).selection, gallListOf(it).keys).isNotEmpty() } ||
            activeFilters.isNotEmpty() || (deleteBoth && otherActiveFilters.isNotEmpty()),
        balance = controller.balance,
        activeJob = controller.hasActiveJob,
        starting = controller.isStarting,
        speedMultiplier = controller.pricing?.let {
            speedMultiplier(hourlyDeletionCount(it.deletionsPerSecond), TWO_CAPTCHA_DELETIONS_PER_HOUR)
        },
        newestFirstConflict = newestFirstAny,
        regexConflict = regexFilterEnabled,
        pricing = controller.pricing,
        chargePostCount = loginInfo?.article_num?.toCount() ?: 0,
        chargeCommentCount = loginInfo?.comment_num?.toCount() ?: 0
    )
}

@Composable
internal fun ProxyJobProgressCard(
    uiColors: UiColors,
    job: ProxyJob,
    /** null이면 읽기 전용: 중단 버튼·동기화 바를 숨기고 [stalledOverride]로 멈춤을 판단한다. */
    controller: ProxyCleanerController?,
    onClose: () -> Unit,
    onStop: () -> Unit,
    stalledOverride: Boolean = false
) {
    val stalled = controller?.jobStalled ?: stalledOverride
    val cancelling = controller?.isCancelling == true
    val stopRequested = cancelling || job.cancelRequested
    // 서버 작업이 멈춰 상태가 갱신되지 않으면 직접 삭제 중 정지했을 때처럼 멈춘 화면으로 보여 준다.
    // 폴링은 계속되므로 서버가 이어서 처리하면 다시 '삭제 중'으로 돌아간다.
    val halted = job.isActive && stalled
    val logs = (
        // 대기 중에는 제목·안내 줄이 상태를 말하므로 서버 문구를 겹쳐 보이지 않는다.
        if (job.status == "queued") emptyList()
        else if (job.statusMessage.isNotBlank() && job.logTail.lastOrNull() != job.statusMessage) {
            job.logTail + job.statusMessage
        } else {
            job.logTail
        }
    ).withDeleteProgressMarked().let { base ->
        // 서버는 로그 원문 대신 숫자만 보내므로 직접 삭제처럼 현재 갤러리 진행 줄을 여기서 만든다.
        val line = job.galleryProcessed?.takeIf { job.status == "running" && !halted }?.let { processed ->
            val total = job.galleryTotal?.takeIf { it > 0 }
            DELETE_PROGRESS_LOG_MARKER + "🗑️ " + if (total == null) "${formatNumber(processed.toLong())}개 처리"
            else "${formatNumber(processed.toLong())} / ${formatNumber(total.toLong())}개 처리 · " +
                "${(processed * 100L / total).coerceIn(0, 100)}%"
        }
        when {
            line == null -> base
            // 캡챠 해결 중 안내는 진행 줄보다 뒤(현재 상태)에 둔다.
            job.statusMessage == "캡챠 해결 중" -> listOf(line) + base
            else -> base + line
        }
    }
    DeleteProgressCard(
        uiColors = uiColors,
        deleteType = job.deleteType,
        currentGallery = if (job.status == "queued") "곧 시작해요" else job.currentGallery,
        isCompleted = !job.isActive || halted,
        isDeleting = job.isActive && !halted,
        totalPosts = job.galleriesTotal,
        // 정상 완료면 갤러리도 모두 끝난 것이므로 100%와 어긋나지 않게 맞춘다.
        deletedPosts = if (job.status == "completed" && !job.stoppedByBalance) job.galleriesTotal else job.galleriesDone,
        // 정상 완료인데 서버 진행률이 덜 찬 채 끝나면 100%로 보여 준다(잔고 소진·실패·취소는 그대로).
        currentProgress = if (job.status == "completed" && !job.stoppedByBalance) 1f else job.progress,
        // 재개를 기다리는 동안 이전 실행의 남은 시간·속도는 맞지 않으므로 '계산 중'으로 둔다.
        estimatedTimeLeft = if (job.status == "queued") 0L else job.etaSeconds,
        currentDeletionSpeedPerHour = if (job.status == "queued") 0 else job.recentSpeedPerHour.takeIf { it > 0 } ?: job.speedPerHour,
        nextCaptchaEstimatedTimeLeft = 0L,
        isTwoCaptchaConfigured = true,
        deleteLog = logs,
        onClose = onClose,
        onComplete = onClose,
        onStop = onStop,
        onOpenProxyCleaner = {},
        proxyMode = true,
        subtitle = job.phaseSubtitle(),
        proxyTitle = when {
            cancelling && job.isActive -> "중단 요청 중"
            halted -> "작업 멈춤"
            job.status == "queued" -> "대기 중"
            job.status == "running" && job.cancelRequested -> "중단 요청 중"
            job.status == "running" -> "삭제 중"
            job.status == "completed" -> "삭제 완료"
            job.status == "failed" -> "작업 실패"
            job.status == "cancelled" -> "작업 취소됨"
            else -> null
        },
        cumulativeSpeedPerHour = job.speedPerHour,
        counts = job.deletedCount to job.skippedCount,
        header = if (controller != null && (job.isActive || job.needsSettlement || cancelling)) { {
            ProxySyncHeader(
                uiColors = uiColors,
                cancelling = controller.isCancelling,
                syncing = controller.isSyncing,
                syncFailed = controller.syncFailed,
                waitStartedAt = controller.syncWaitStartedAt,
                intervalMillis = controller.syncIntervalMillis
            )
        } } else null,
        stopLabel = if (stopRequested) "중단 요청 중…" else "작업 중단",
        stopEnabled = !stopRequested,
        showStop = controller != null,
        completeLabel = if (job.status == "completed") "완료" else "닫기",
        resultSummary = if (halted) listOf(
            "서버 작업이 멈췄어요. 삭제된 개수만 차감되고 나머지는 돌려드려요. 계속 멈춰 있으면 사용자 코드와 함께 문의해 주세요."
        ) else if (job.isActive) emptyList() else listOfNotNull(
            job.error,
            if (job.needsSettlement) "삭제 결과를 확인하고 있어요. 끝나면 쓰지 않은 개수를 돌려드려요." else null,
            if (job.stoppedByBalance) "충전한 개수를 모두 썼어요. 충전하면 남은 항목을 이어서 지울 수 있어요." else null,
            job.returnedCount?.takeIf { it > 0 }?.let { "쓰지 않은 ${formatNumber(it)}개는 돌려드렸어요." }
        )
    )
}

/** 함께 지울 때 시작 확인 창에 순서대로 보여 줄 단계. 한 종류만 지우면 빈 목록. */
private fun DccleanerScreenState.deletePhasesForDialog(): List<DeleteDialogPhase> {
    if (!deleteBoth) return emptyList()
    val order = if (commentFirst) listOf("comment", "posting") else listOf("posting", "comment")
    return order.map { type ->
        val galleries = if (type == "posting") postingGallList else commentGallList
        val editing = type == deleteType
        DeleteDialogPhase(
            label = if (type == "posting") "게시글" else "댓글",
            galleryNames = (if (editing) selectedGallList else otherSelectedGallList).mapNotNull(galleries::get),
            filters = if (editing) activeFilters else otherActiveFilters
        )
    }
}

/** 함께 지우는 중이면 "댓글 정리 · 1/2단계 · 다음은 게시글"처럼 단계를 붙인다. */
private fun DccleanerScreenState.runningPhaseSubtitle(): String? {
    if (runningPhases.size < 2) return null
    val labels = runningPhases.map { if (it == "posting") "게시글" else "댓글" }
    if (isCompleted) return "${labels.joinToString(" → ")} 정리"
    val step = runningPhases.indexOf(progressDeleteType).takeIf { it >= 0 } ?: return null
    val current = "${labels[step]} 정리 · ${step + 1}/${runningPhases.size}단계"
    return if (step + 1 < labels.size) "$current · 다음은 ${labels[step + 1]}" else current
}

/** 초고속 이어 삭제 중이면 "댓글 정리 · 1/2단계 · 다음은 게시글". 한 종류 작업이면 null. */
private fun ProxyJob.phaseSubtitle(): String? {
    if (phaseCount < 2) return null
    fun label(type: String) = if (type == "posting") "게시글" else "댓글"
    val current = "${label(deleteType)} 정리 · $phaseIndex/${phaseCount}단계"
    // 끝난 작업(잔고 소진·실패·취소)은 다음 단계로 넘어가지 않으니 '다음은'을 붙이지 않는다.
    return nextDeleteType?.takeIf { isActive && phaseIndex < phaseCount }?.let { "$current · 다음은 ${label(it)}" } ?: current
}
