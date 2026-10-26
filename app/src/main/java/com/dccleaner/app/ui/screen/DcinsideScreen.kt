package com.dccleaner.app.ui.screen

import com.dccleaner.app.util.openLink
import androidx.activity.compose.BackHandler
import com.dccleaner.app.MainActivity
import com.dccleaner.app.BuildConfig
import com.dccleaner.app.model.*
import com.dccleaner.app.model.dccleanerUiColors
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.CleanerLogSink
import com.dccleaner.app.network.GuestbookUserListFetcher
import com.dccleaner.app.service.ChargeWatchService
import com.dccleaner.app.service.ProxyJobWatcher
import com.dccleaner.app.service.ServiceManager
import com.dccleaner.app.storage.addSavedAccount
import com.dccleaner.app.storage.getSavedAccounts
import com.dccleaner.app.storage.getSavedTwoCaptchaKey
import com.dccleaner.app.storage.removeSavedAccount
import com.dccleaner.app.storage.removeSavedTwoCaptchaKey
import com.dccleaner.app.storage.saveTwoCaptchaKey
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.storage.GuestbookSentUserCache
import com.dccleaner.app.storage.AndroidProxyCleanerStorage
import com.dccleaner.app.proxy.SupabaseProxyCleanerBackend
import com.dccleaner.app.ui.proxy.ProxyCleanerController
import com.dccleaner.app.storage.getSavedRecordGuestbookLog
import com.dccleaner.app.storage.getSavedDeleteCommentFirst
import com.dccleaner.app.storage.getSavedDeleteTargets
import com.dccleaner.app.storage.saveDeleteCommentFirst
import com.dccleaner.app.storage.saveDeleteTargets
import com.dccleaner.app.storage.getSavedDeveloperMode
import com.dccleaner.app.storage.saveDeveloperMode
import com.dccleaner.app.storage.saveRecordGuestbookLog
import com.dccleaner.app.util.LogManager
import com.dccleaner.app.update.ReleaseVersionChecker
import com.dccleaner.app.ui.card.*
import com.dccleaner.app.ui.cleaner.DcCleanerTabContent
import com.dccleaner.app.ui.dialog.*
import com.dccleaner.app.ui.guestbook.GuestbookTabContent
import com.dccleaner.app.ui.ranking.clearGalleryRanking
import com.dccleaner.app.ui.userposts.clearUserPostSearch
import com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import android.app.Application
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import com.dccleaner.app.runtime.toDeletionRegex
import android.app.Activity
import android.content.Intent
import android.webkit.CookieManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DcinsideScreen(
    resumeTaskId: String? = null,
    onResumeTaskConsumed: () -> Unit = {},
    openGalleryRanking: Boolean = false,
    onOpenGalleryRankingConsumed: () -> Unit = {},
    isDarkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current
    val appContext = context.applicationContext
    val session = remember(context) {
        ViewModelProvider(context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(appContext as Application))
            .get(DccleanerSession::class.java)
    }
    val cleaner = session.cleaner
    val serviceManager = remember(appContext) { ServiceManager(appContext) }
    val deleteTaskStore = session.deleteTaskStore
    val guestbookSentUserCache = remember(appContext) { GuestbookSentUserCache(appContext) }
    val proxyStorage = remember(appContext) { AndroidProxyCleanerStorage(appContext) }
    val proxyBackend = remember(proxyStorage) { SupabaseProxyCleanerBackend(proxyStorage) }
    val proxyCleaner = remember(proxyBackend) { ProxyCleanerController(proxyBackend, proxyStorage) }
    DisposableEffect(proxyBackend) { onDispose { proxyBackend.close() } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val restoreScrollTopPadding = with(LocalDensity.current) { 72.dp.roundToPx() }
    var captchaSectionScrollY by remember { mutableIntStateOf(0) }
    var deleteOptionsSectionScrollY by remember { mutableIntStateOf(0) }
    var filterOptionsSectionScrollY by remember { mutableIntStateOf(0) }

    suspend fun animateRestoreScrollTo(sectionY: Int) {
        if (sectionY <= 0) return
        scrollState.animateScrollTo(
            value = (sectionY - restoreScrollTopPadding).coerceIn(0, scrollState.maxValue),
            animationSpec = tween(
                durationMillis = 1_200,
                easing = FastOutSlowInEasing
            )
        )
        kotlinx.coroutines.delay(500)
    }


    val uiColors = remember(isDarkTheme) { dccleanerUiColors(isDarkTheme) }


    var id by session.id
    var pw by session.password
    var loginInfo by session.loginInfo
    var saveLogin by rememberSaveable { mutableStateOf(true) } // 로그인 정보 저장 여부 (기본값: true)
    var savedAccounts by remember { mutableStateOf<List<SavedAccount>>(emptyList()) }
    var interruptedTasks by remember { mutableStateOf<List<DeleteTaskProgress>>(emptyList()) }
    var isLoggingOut by remember { mutableStateOf(false) }
    // 로그아웃을 시작하면 성공해 화면을 떠날 때까지 댓글 자동 정리를 다시 켜지 않는다(실패하면 다시 푼다).
    var logoutStarted by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) } // 로그인 중 상태
    var sessionExpired by remember { mutableStateOf(false) }


    var postingGallList by session.postingGalleries
    var commentGallList by session.commentGalleries
    var selectedGallList by session.selectedGalleries
    var userDataRefreshRequest by remember { mutableIntStateOf(0) }
    var sessionCheckRequest by remember { mutableIntStateOf(0) }


    var twocaptchaKey by remember { mutableStateOf("") }
    var isTwocaptchaValid by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingTwocaptcha by remember { mutableStateOf(false) }

    // 마지막에 고른 지울 대상. 처음이면 아무것도 안 고른 상태로 시작한다.
    val savedDeleteTargets = remember(appContext) { getSavedDeleteTargets(appContext) }
    var deleteType by rememberSaveable { mutableStateOf(if (savedDeleteTargets == setOf("comment")) "comment" else "posting") }
    var deleteLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // 삭제 조건 설정
    var minRecommendToKeep by rememberSaveable { mutableStateOf("1") }
    var minCommentToKeep by rememberSaveable { mutableStateOf("1") }
    var minViewToKeep by rememberSaveable { mutableStateOf("1") }
    var recommendFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var commentFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var viewFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var postContentFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var postContentRegex by rememberSaveable { mutableStateOf("") }
    var myPostFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var dcconOnlyFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var commentContentFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var commentContentRegex by rememberSaveable { mutableStateOf("") }
    var dateFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var deleteNewestFirst by rememberSaveable { mutableStateOf(false) }
    var deleteQuestionPosts by rememberSaveable { mutableStateOf(false) }
    var minPostAgeDaysToDelete by rememberSaveable { mutableStateOf("5") }
    // 글+댓글 함께 지우기. 위 날짜 조건·최근부터 삭제·선택 갤러리는 '지금 설정 중인 종류' 값이고, 다른 종류 값은 아래에 맡겨 둔다.
    var deleteBoth by rememberSaveable { mutableStateOf(savedDeleteTargets.size == 2) }
    var noDeleteTarget by rememberSaveable { mutableStateOf(savedDeleteTargets.isEmpty()) }
    var commentFirst by rememberSaveable { mutableStateOf(getSavedDeleteCommentFirst(appContext)) }
    var runningPhases by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var otherSelectedGallList by session.otherSelectedGalleries
    var otherTrioStashed by rememberSaveable { mutableStateOf(false) } // false면 다른 종류도 지금 값을 그대로 따른다
    var otherDateFilterEnabled by rememberSaveable { mutableStateOf(false) }
    var otherDeleteNewestFirst by rememberSaveable { mutableStateOf(false) }
    var otherMinPostAgeDaysToDelete by rememberSaveable { mutableStateOf("5") }

    fun gallListOf(type: String) = if (type == "posting") postingGallList else commentGallList
    fun otherTypeOf(type: String) = if (type == "posting") "comment" else "posting"
    fun otherSelection() = otherSelectedGallList ?: gallListOf(otherTypeOf(deleteType)).keys.toList()
    fun otherDateFilter() = if (otherTrioStashed) otherDateFilterEnabled else dateFilterEnabled
    fun otherNewestFirst() = if (otherTrioStashed) otherDeleteNewestFirst else deleteNewestFirst
    fun otherMinAge() = if (otherTrioStashed) otherMinPostAgeDaysToDelete else minPostAgeDaysToDelete

    // 설정 중인 종류를 바꾸면 지금 값과 맡겨 둔 다른 종류 값을 맞바꾼다.
    fun switchEditedType(newType: String) {
        if (newType == deleteType) return
        val selection = otherSelection()
        val date = otherDateFilter()
        val newest = otherNewestFirst()
        val minAge = otherMinAge()
        otherSelectedGallList = selectedGallList
        otherDateFilterEnabled = dateFilterEnabled
        otherDeleteNewestFirst = deleteNewestFirst
        otherMinPostAgeDaysToDelete = minPostAgeDaysToDelete
        otherTrioStashed = true
        selectedGallList = selection
        dateFilterEnabled = date
        deleteNewestFirst = newest
        minPostAgeDaysToDelete = minAge
        deleteType = newType
    }

    // 종류별로 따로인 갤러리·날짜 조건·최근부터 삭제를 담은 단계 설정.
    fun phaseOf(type: String): DeletePhase {
        val edited = type == deleteType
        val selection = if (edited) selectedGallList else otherSelection()
        val date = if (edited) dateFilterEnabled else otherDateFilter()
        return DeletePhase(
            deleteType = type,
            selectedGalleries = selection,
            galleryMap = DeleteTaskStartValidator.selectedGalleryMap(selection, gallListOf(type)),
            dateFilterEnabled = date,
            deleteNewestFirst = if (edited) deleteNewestFirst else otherNewestFirst(),
            minPostAgeDaysToDelete = if (date) (if (edited) minPostAgeDaysToDelete else otherMinAge()).toIntOrNull() ?: 5 else -1
        )
    }

    fun phaseHasCompleteMap(type: String) = DeleteTaskStartValidator.hasCompleteGalleryMap(
        if (type == deleteType) selectedGallList else otherSelection(),
        gallListOf(type)
    )

    var recordGuestbookLog by remember(appContext) {
        mutableStateOf(getSavedRecordGuestbookLog(appContext))
    }


    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isCheckingDeletionSession by remember { mutableStateOf(false) }
    var deletionSessionCheckJob by remember { mutableStateOf<Job?>(null) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }
    var showStopDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isCompleted by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0f) }
    var currentGallery by remember { mutableStateOf("") }
    var totalPosts by remember { mutableStateOf(0) }
    var deletedPosts by remember { mutableStateOf(0) }
    var estimatedTimeLeft by remember { mutableStateOf(0L) }


    // 캡챠 다이얼로그는 서비스 상태를 직접 사용 (로컬 상태 제거)
    // var showCaptchaDialog by remember { mutableStateOf(false) }
    // var captchaFlag by remember { mutableStateOf(false) }


    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<SavedAccount?>(null) }
    var deleteTaskToRemove by remember { mutableStateOf<DeleteTaskProgress?>(null) }
    var restoringTaskId by remember { mutableStateOf<String?>(null) }
    var activeResumeTaskId by remember { mutableStateOf<String?>(null) }
    var restoringMessage by remember { mutableStateOf("") }

    // 탭 관련 상태
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var developerMode by remember(appContext) {
        mutableStateOf(getSavedDeveloperMode(appContext))
    }

    // 버전 체크 관련 상태
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingVersion by remember { mutableStateOf(false) }


    val profileImage = rememberUserProfileImage(loginInfo?.userId.orEmpty())
    val itemCounts by serviceManager.itemCounts.collectAsStateWithLifecycle()
    val isServiceDeleting by serviceManager.isDeleting.collectAsStateWithLifecycle()
    val isServiceConnected by serviceManager.isServiceConnected.collectAsStateWithLifecycle()
    val serviceProgress by serviceManager.progress.collectAsStateWithLifecycle()
    val serviceCurrentGallery by serviceManager.currentGallery.collectAsStateWithLifecycle()
    val serviceCurrentGalleryEstimatedTimeLeft by serviceManager.currentGalleryEstimatedTimeLeft.collectAsStateWithLifecycle()
    val serviceCurrentDeletionSpeedPerHour by serviceManager.currentDeletionSpeedPerHour.collectAsStateWithLifecycle()
    val serviceNextCaptchaEstimatedTimeLeft by serviceManager.nextCaptchaEstimatedTimeLeft.collectAsStateWithLifecycle()
    val serviceIsTwoCaptchaConfigured by serviceManager.isTwoCaptchaConfigured.collectAsStateWithLifecycle()
    val serviceTaskLoginId by serviceManager.currentTaskLoginId.collectAsStateWithLifecycle()
    val serviceDeleteType by serviceManager.currentDeleteType.collectAsStateWithLifecycle()
    val serviceDeletedCount by serviceManager.deletedCount.collectAsStateWithLifecycle()
    val serviceTotalCount by serviceManager.totalCount.collectAsStateWithLifecycle()
    val serviceDeleteLog by serviceManager.deleteLog.collectAsStateWithLifecycle()
    val serviceIsCompleted by serviceManager.isCompleted.collectAsStateWithLifecycle()
    val serviceErrorMessage by serviceManager.errorMessage.collectAsStateWithLifecycle()
    val serviceShowCaptchaDialog by serviceManager.showCaptchaDialog.collectAsStateWithLifecycle()
    val serviceCaptchaFlag by serviceManager.captchaFlag.collectAsStateWithLifecycle()

    val serviceGuestbookIsSending by serviceManager.isGuestbookSending.collectAsStateWithLifecycle()
    val commentCleanerRunning by serviceManager.isCommentCleanerRunning.collectAsStateWithLifecycle()

    // 댓글 자동 정리·방명록 등은 더보기 안의 화면이라 뒤로 가기를 누르면 더보기로 돌아간다.
    BackHandler(enabled = loginInfo != null && selectedTab in MORE_SUBPAGES) {
        selectedTab = MORE_TAB
    }

    LaunchedEffect(loginInfo != null, commentCleanerRunning, logoutStarted) {
        // 로그아웃 중에 서비스가 멈추면 지울 세션으로 다시 켜지는 것을 막는다.
        if (loginInfo != null && !commentCleanerRunning && !logoutStarted) {
            val settings = loadSavedCommentMonitor(androidCommentMonitorStorage(appContext))
            if (settings.keywords.isNotEmpty()) {
                serviceManager.startCommentCleaner(
                    cleaner,
                    settings.keywords,
                    settings.intervalSeconds,
                    settings.monitorMinutes
                )
            }
        }
    }
    val serviceGuestbookProgress by serviceManager.guestbookProgress.collectAsStateWithLifecycle()
    val serviceGuestbookProgressDone = serviceGuestbookProgress.done
    val serviceGuestbookProgressTotal = serviceGuestbookProgress.total
    val serviceGuestbookSuccessCount = serviceGuestbookProgress.successCount
    val serviceGuestbookFailCount = serviceGuestbookProgress.failCount

    // 방명록 관련 상태
    var guestbookUserListText by session.guestbookUsers
    var guestbookMessageText by session.guestbookMessage
    var guestbookShowConfirmDialog by remember { mutableStateOf(false) }
    var guestbookShowResultDialog by remember { mutableStateOf(false) }
    var showGuestbookProgressDialog by remember { mutableStateOf(false) }
    var guestbookCachedUserCount by remember { mutableIntStateOf(0) }

    var deleteTaskWasRunning by remember { mutableStateOf(false) }
    var guestbookTaskWasRunning by remember { mutableStateOf(false) }

    LaunchedEffect(loginInfo, serviceGuestbookIsSending) {
        if (!serviceGuestbookIsSending) {
            guestbookCachedUserCount = if (loginInfo == null) {
                0
            } else {
                guestbookSentUserCache.count(cleaner.getUserId())
            }
        }
    }

    fun requestUserDataRefresh() {
        userDataRefreshRequest++
    }

    fun handleExpiredGallListSession() {
        val expiredLoginId = cleaner.getUserId().ifBlank { id }
        val reloginPassword = savedAccounts
            .firstOrNull { it.id.equals(expiredLoginId, ignoreCase = true) }
            ?.password
            ?: pw
        cleaner.clearSession()
        serviceManager.stopCommentCleaner()
        serviceManager.clearLogs()
        loginInfo = null
        id = expiredLoginId
        pw = reloginPassword
        sessionExpired = true
        postingGallList = emptyMap()
        commentGallList = emptyMap()
        selectedGallList = emptyList()
        // 갤러리는 계정마다 달라 비우지만, 날짜 조건·최근부터 삭제는 지금 종류처럼 다른 종류 것도 그대로 둔다.
        otherSelectedGallList = null
        interruptedTasks = emptyList()
        deleteLog = emptyList()
        showDeleteProgressDialog = false
        isTwocaptchaValid = null
        isLoggingIn = false
    }

    LaunchedEffect(sessionCheckRequest) {
        if (sessionCheckRequest == 0 || loginInfo == null || isServiceDeleting) return@LaunchedEffect
        when (cleaner.checkLoginSession()) {
            false -> handleExpiredGallListSession()
            true -> sessionExpired = false
            null -> Unit
        }
    }

    LaunchedEffect(userDataRefreshRequest) {
        if (userDataRefreshRequest == 0) return@LaunchedEffect

        val uiLoginId = cleaner.getUserId()
        if (uiLoginId.isBlank()) {
            loginInfo = null
            id = serviceTaskLoginId
            postingGallList = emptyMap()
            commentGallList = emptyMap()
            selectedGallList = emptyList()
            otherSelectedGallList = null
        } else {
            try {
                loginInfo = cleaner.getUserInfo()

                val postingGallResult = cleaner.getGallList("posting")
                when (postingGallResult) {
                    GallListResult.SessionExpired -> {
                        handleExpiredGallListSession()
                        return@LaunchedEffect
                    }
                    is GallListResult.Success -> postingGallList = postingGallResult.galleries
                    GallListResult.Blocked -> Unit
                }

                val commentGallResult = cleaner.getGallList("comment")
                when (commentGallResult) {
                    GallListResult.SessionExpired -> {
                        handleExpiredGallListSession()
                        return@LaunchedEffect
                    }
                    is GallListResult.Success -> commentGallList = commentGallResult.galleries
                    GallListResult.Blocked -> Unit
                }

                selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(
                    selectedGallList,
                    gallListOf(deleteType)
                )
                otherSelectedGallList = otherSelectedGallList?.let {
                    DeleteTaskStartValidator.retainAvailableSelection(it, gallListOf(otherTypeOf(deleteType)))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DcinsideScreen", "사용자 데이터 새로고침 오류", e)
            }
        }

        interruptedTasks = withContext(session.storageDispatcher) {
            deleteTaskStore.getForLogin(uiLoginId.ifBlank { serviceTaskLoginId })
        }
    }

    LaunchedEffect(isServiceDeleting) {
        if (deleteTaskWasRunning && !isServiceDeleting) {
            requestUserDataRefresh()
        }
        deleteTaskWasRunning = isServiceDeleting
    }

    // 초고속 삭제가 도는 동안 백그라운드 확인을 걸어 두고, 앱이 먼저 끝난 걸 보면 화면에 없을 때만 알린다.
    val proxyJob = proxyCleaner.job
    val proxyLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(proxyJob?.id, proxyJob?.status, proxyJob?.needsSettlement) {
        val job = proxyJob ?: return@LaunchedEffect
        if (job.isActive || job.needsSettlement) ProxyJobWatcher.watch(appContext, job.id, proxyCleaner.accountId)
        else ProxyJobWatcher.finished(appContext, job, notify = !proxyLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    LaunchedEffect(serviceGuestbookIsSending) {
        if (serviceGuestbookIsSending) {
            showGuestbookProgressDialog = true
        }
        if (guestbookTaskWasRunning && !serviceGuestbookIsSending) {
            requestUserDataRefresh()
            if (serviceGuestbookProgressTotal > 0) {
                showGuestbookProgressDialog = true
            }
        }
        guestbookTaskWasRunning = serviceGuestbookIsSending
    }

    LaunchedEffect(Unit) {
        val savedKey = withContext(session.storageDispatcher) { getSavedTwoCaptchaKey(appContext) }
        if (savedKey.isNotBlank()) {
            twocaptchaKey = savedKey
            isTwocaptchaValid = null
            isCheckingTwocaptcha = true
            try {
                isTwocaptchaValid = cleaner.set2CaptchaKey(savedKey)
            } finally {
                isCheckingTwocaptcha = false
            }
        }
    }

    LaunchedEffect(isTwocaptchaValid, twocaptchaKey) {
        if (isTwocaptchaValid == true && twocaptchaKey.isNotBlank()) {
            withContext(session.storageDispatcher) { saveTwoCaptchaKey(appContext, twocaptchaKey) }
        }
    }


    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, serviceManager) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    serviceManager.setUiObservationEnabled(true)
                    sessionCheckRequest++
                }
                Lifecycle.Event.ON_STOP -> serviceManager.setUiObservationEnabled(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        serviceManager.setUiObservationEnabled(
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            sessionCheckRequest++
        }
        serviceManager.bindService()
        onDispose {
            lifecycle.removeObserver(observer)
            serviceManager.unbindService()
        }
    }

    LaunchedEffect(Unit) {
        val loadedAccounts = withContext(session.storageDispatcher) { getSavedAccounts(appContext) }
        if (loadedAccounts == null) {
            errorMessage = "저장된 계정을 읽지 못했습니다. 기존 데이터는 보존되어 있으며 앱을 다시 열어 재시도할 수 있습니다."
            showErrorDialog = true
        } else {
            savedAccounts = loadedAccounts
        }
        if (loginInfo != null) {
            interruptedTasks = withContext(session.storageDispatcher) { deleteTaskStore.getForLogin(cleaner.getUserId()) }
            requestUserDataRefresh()
        }
    }

    // 갤창랭킹 알림을 누르고 들어오면 진행 상황·결과가 있는 갤창랭킹 화면을 연다(로그인 전이면 로그인 뒤 그 화면이 보인다).
    LaunchedEffect(openGalleryRanking) {
        if (openGalleryRanking) {
            // 삭제 중에는 탭이 숨어 있으니 그때는 탭을 그대로 둔다.
            if (!isServiceDeleting && !isDeleting) selectedTab = RANKING_TAB
            onOpenGalleryRankingConsumed()
        }
    }

    LaunchedEffect(resumeTaskId) {
        // 이어하기 상자는 클리너 탭에만 있으니, 알림에서 이어하기로 들어오면 클리너 탭을 연다.
        if (resumeTaskId != null) selectedTab = 0
        withContext(session.storageDispatcher) { resumeTaskId?.let(deleteTaskStore::get) }?.let { task ->
            id = task.loginId
            pw = ""
        }
    }

    // GitHub Releases에서 최신 버전 가져오기
    LaunchedEffect(Unit) {
        isCheckingVersion = true
        try {
            latestVersion = ReleaseVersionChecker.fetchLatestVersion()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DcinsideScreen", "Failed to fetch latest version: ${e.message}")
        } finally {
            isCheckingVersion = false
        }
    }


    LaunchedEffect(isServiceDeleting, isServiceConnected) {
        if (!isServiceConnected) return@LaunchedEffect
        if (isServiceDeleting) {
            isDeleting = true
            if (serviceDeleteType == "posting" || serviceDeleteType == "comment") {
                switchEditedType(serviceDeleteType)
            }
            showDeleteConfirmDialog = false
            showDeleteProgressDialog = true
        } else if (!isServiceDeleting && isDeleting && !serviceIsCompleted) {
            isDeleting = false
            restoringTaskId = null
            if (activeResumeTaskId != null) {
                activeResumeTaskId = null
            }
            if (loginInfo != null) {
                interruptedTasks = withContext(session.storageDispatcher) { deleteTaskStore.getForLogin(cleaner.getUserId()) }
            } else if (serviceTaskLoginId.isNotBlank()) {
                id = serviceTaskLoginId
            }
        }
    }

    LaunchedEffect(serviceDeleteType, isServiceDeleting) {
        if (isServiceDeleting &&
            (serviceDeleteType == "posting" || serviceDeleteType == "comment")
        ) {
            // 글+댓글 작업은 도중에 종류가 바뀌니, 화면도 그 종류 설정으로 맞바꾼다.
            switchEditedType(serviceDeleteType)
        }
    }

    LaunchedEffect(serviceTaskLoginId, isServiceDeleting) {
        if (isServiceDeleting && serviceTaskLoginId.isNotBlank()) {
            id = serviceTaskLoginId
        }
    }

    LaunchedEffect(serviceIsCompleted) {
        if (serviceIsCompleted) {
            isCompleted = true
            isDeleting = false
            restoringTaskId = null
            activeResumeTaskId = null
            onResumeTaskConsumed()
            showDeleteProgressDialog = true
        }
    }

    LaunchedEffect(serviceProgress) {
        currentProgress = serviceProgress
    }

    LaunchedEffect(serviceCurrentGallery) {
        currentGallery = serviceCurrentGallery
    }

    LaunchedEffect(serviceCurrentGalleryEstimatedTimeLeft) {
        estimatedTimeLeft = serviceCurrentGalleryEstimatedTimeLeft
    }

    LaunchedEffect(serviceDeletedCount) {
        deletedPosts = serviceDeletedCount
    }

    LaunchedEffect(serviceTotalCount) {
        totalPosts = serviceTotalCount
    }

    LaunchedEffect(serviceDeleteLog) {
        deleteLog = serviceDeleteLog
    }

    LaunchedEffect(serviceErrorMessage) {
        serviceErrorMessage?.let { error ->
            errorMessage = error
            if (!serviceManager.isDeleting.value) isDeleting = false
            showDeleteProgressDialog = false
            showErrorDialog = true
            serviceManager.clearError()
        }
    }

    // 캡챠 상태 동기화는 제거 - 서비스 상태를 직접 사용
    // LaunchedEffect(serviceShowCaptchaDialog) {
    //     showCaptchaDialog = serviceShowCaptchaDialog
    // }

    // LaunchedEffect(serviceCaptchaFlag) {
    //     captchaFlag = serviceCaptchaFlag
    // }

    val coroutine = rememberCoroutineScope()


    val primaryColor = uiColors.primary
    val backgroundColor = uiColors.background
    val cardColor = uiColors.card
    val deleteUiActive = isServiceDeleting || isDeleting
    val displayedProgress = if (isServiceDeleting) serviceProgress else currentProgress
    val displayedGallery = if (isServiceDeleting) serviceCurrentGallery else currentGallery
    val displayedDeletedCount = if (isServiceDeleting) serviceDeletedCount else deletedPosts
    val displayedTotalCount = if (isServiceDeleting) serviceTotalCount else totalPosts
    val displayedDeleteLog = if (isServiceDeleting) serviceDeleteLog else deleteLog

    fun activeFiltersOf(type: String, date: Boolean, newest: Boolean, minAge: String) = buildList {
        if (type == "posting" && recommendFilterEnabled) add("추천 ${minRecommendToKeep.ifBlank { "1" }}개 이상 보존")
        if (type == "posting" && commentFilterEnabled) add("댓글 ${minCommentToKeep.ifBlank { "1" }}개 이상 보존")
        if (type == "posting" && viewFilterEnabled) add("조회 ${minViewToKeep.ifBlank { "1" }}회 이상 보존")
        if (type == "posting" && postContentFilterEnabled) add("제목 정규식")
        if (newest) {
            add(if (type == "posting") "최근 글부터 삭제" else "최근 댓글부터 삭제")
        }
        if (type == "posting" && deleteQuestionPosts) add("질문글도 삭제")
        if (type == "comment" && myPostFilterEnabled) add("내 글에 단 댓글만")
        if (type == "comment" && dcconOnlyFilterEnabled) add("디시콘 댓글만")
        if (type == "comment" && commentContentFilterEnabled) add("댓글 정규식")
        if (date) add("작성 후 ${minAge.ifBlank { "5" }}일 이상")
        if (twocaptchaKey.isNotBlank()) add("2Captcha 자동 해결")
    }

    DccleanerScreenContent(
        state = DccleanerScreenState(
            uiColors = uiColors,
            isDarkTheme = isDarkTheme,
            id = id,
            pw = pw,
            loginInfo = loginInfo,
            saveLogin = saveLogin,
            savedAccounts = savedAccounts,
            isLoggingIn = isLoggingIn,
            sessionExpired = sessionExpired,
            deleteUiActive = deleteUiActive,
            runningLoginId = serviceTaskLoginId,
            displayedGallery = displayedGallery,
            displayedProgress = displayedProgress,
            displayedDeletedCount = displayedDeletedCount,
            itemCounts = itemCounts,
            profileImage = profileImage,
            displayedTotalCount = displayedTotalCount,
            displayedDeleteLog = displayedDeleteLog,
            interruptedTasks = interruptedTasks,
            resumeEnabled = !isServiceDeleting && !isDeleting && restoringTaskId == null,
            restoringTaskId = restoringTaskId,
            focusedTaskId = resumeTaskId,
            restoringMessage = restoringMessage,
            selectedTab = selectedTab,
            developerMode = developerMode,
            commentCleanerRunning = commentCleanerRunning,
            postingGallList = postingGallList,
            commentGallList = commentGallList,
            deleteType = deleteType,
            selectedGallList = selectedGallList,
            twocaptchaKey = twocaptchaKey,
            isTwocaptchaValid = isTwocaptchaValid,
            isCheckingTwocaptcha = isCheckingTwocaptcha,
            minRecommendToKeep = minRecommendToKeep,
            minCommentToKeep = minCommentToKeep,
            minViewToKeep = minViewToKeep,
            recommendFilterEnabled = recommendFilterEnabled,
            commentFilterEnabled = commentFilterEnabled,
            viewFilterEnabled = viewFilterEnabled,
            postContentFilterEnabled = postContentFilterEnabled,
            postContentRegex = postContentRegex,
            myPostFilterEnabled = myPostFilterEnabled,
            dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
            commentContentFilterEnabled = commentContentFilterEnabled,
            commentContentRegex = commentContentRegex,
            dateFilterEnabled = dateFilterEnabled,
            deleteNewestFirst = deleteNewestFirst,
            deleteQuestionPosts = deleteQuestionPosts,
            minPostAgeDaysToDelete = minPostAgeDaysToDelete,
            recordGuestbookLog = recordGuestbookLog,
            latestVersion = latestVersion,
            currentVersion = BuildConfig.VERSION_NAME,
            isCheckingVersion = isCheckingVersion,
            showErrorDialog = showErrorDialog,
            errorMessage = errorMessage,
            deleteTaskToRemove = deleteTaskToRemove,
            isCheckingDeletionSession = isCheckingDeletionSession,
            showDeleteConfirmDialog = showDeleteConfirmDialog,
            activeFilters = activeFiltersOf(deleteType, dateFilterEnabled, deleteNewestFirst, minPostAgeDaysToDelete),
            deleteBoth = deleteBoth,
            noDeleteTarget = noDeleteTarget,
            commentFirst = commentFirst,
            otherSelectedGallList = otherSelection(),
            otherPhase = phaseOf(otherTypeOf(deleteType)),
            otherActiveFilters = activeFiltersOf(otherTypeOf(deleteType), otherDateFilter(), otherNewestFirst(), otherMinAge()),
            runningPhases = runningPhases,
            showDeleteProgressDialog = showDeleteProgressDialog,
            progressDeleteType = deleteType,
            isCompleted = isCompleted,
            isDeleting = deleteUiActive,
            estimatedTimeLeft = estimatedTimeLeft,
            currentDeletionSpeedPerHour = serviceCurrentDeletionSpeedPerHour,
            nextCaptchaEstimatedTimeLeft = serviceNextCaptchaEstimatedTimeLeft,
            isTwoCaptchaConfigured = serviceIsTwoCaptchaConfigured,
            showStopDeleteDialog = showStopDeleteDialog,
            showCaptchaDialog = serviceShowCaptchaDialog,
            accountToDelete = accountToDelete,
            showDeleteAccountDialog = showDeleteAccountDialog,
            guestbookUserListText = guestbookUserListText,
            guestbookMessageText = guestbookMessageText,
            guestbookShowConfirmDialog = guestbookShowConfirmDialog,
            guestbookShowResultDialog = guestbookShowResultDialog,
            showGuestbookProgressDialog = showGuestbookProgressDialog,
            guestbookIsSending = serviceGuestbookIsSending,
            guestbookProgressDone = serviceGuestbookProgressDone,
            guestbookProgressTotal = serviceGuestbookProgressTotal,
            guestbookSuccessCount = serviceGuestbookSuccessCount,
            guestbookFailCount = serviceGuestbookFailCount,
            guestbookCacheEnabled = true,
            guestbookCachedUserCount = guestbookCachedUserCount
        ),
        actions = DccleanerScreenActions(
            onDarkThemeChange = onDarkThemeChange,
            onIdChange = { id = it },
            onPwChange = { pw = it },
            onSaveLoginChange = { saveLogin = it },
            onSavedAccountClick = { account ->
                id = account.id
                pw = account.password
            },
            onDeleteSavedAccountClick = { account ->
                accountToDelete = account
                showDeleteAccountDialog = true
            },
            onLoginClick = {
                coroutine.launch {
                    id = id.trim()
                    isLoggingIn = true
                    val success = cleaner.login(id, pw)
                    if (!success) {
                        Log.w("DcinsideScreen", "로그인 실패 감지")
                        errorMessage = "로그인에 실패했습니다.\n아이디와 비밀번호를 확인해 주세요.\n디시인사이드 2단계 인증이 켜져 있으면 끄고 다시 시도해 주세요."
                        showErrorDialog = true
                        isLoggingIn = false
                        return@launch
                    }
                    if (saveLogin && id.isNotBlank() && pw.isNotBlank()) {
                        val userInfo = cleaner.getUserInfo()
                        val updatedAccounts = withContext(session.storageDispatcher) {
                            addSavedAccount(appContext, SavedAccount(id, pw, userInfo.nickname))
                        }
                        if (updatedAccounts != null) {
                            savedAccounts = updatedAccounts
                        } else {
                            snackbarHostState.showSnackbar("로그인 정보를 저장하지 못했습니다.")
                        }
                    }
                    loginInfo = cleaner.getUserInfo()
                    sessionExpired = false
                    interruptedTasks = withContext(session.storageDispatcher) { deleteTaskStore.getForLogin(cleaner.getUserId()) }
                    val posting = cleaner.getGallList("posting")
                    if (posting is GallListResult.SessionExpired) {
                        handleExpiredGallListSession()
                        return@launch
                    }
                    val comment = cleaner.getGallList("comment")
                    if (comment is GallListResult.SessionExpired) {
                        handleExpiredGallListSession()
                        return@launch
                    }
                    postingGallList = (posting as? GallListResult.Success)?.galleries ?: emptyMap()
                    commentGallList = (comment as? GallListResult.Success)?.galleries ?: emptyMap()
                    selectedGallList = gallListOf(deleteType).keys.toList()
                    otherSelectedGallList = null
                    isLoggingIn = false
                }
            },
            onLogoutClick = {
                if (!isLoggingOut) coroutine.launch {
                    isLoggingOut = true
                    logoutStarted = true
                    try {
                        deletionSessionCheckJob?.cancelAndJoin()
                        serviceManager.stopForLogout()
                        cleaner.clearSession()
                        suspendCancellableCoroutine<Unit> { continuation ->
                            CookieManager.getInstance().removeAllCookies { continuation.resume(Unit) }
                        }
                        // 로그아웃에 성공했을 때만 만들던 갤창랭킹·찾던 글과 결과를 지운다(실패하면 그대로 둔다).
                        clearGalleryRanking()
                        clearUserPostSearch()
                        val activity = context as Activity
                        activity.startActivity(Intent(activity, MainActivity::class.java))
                        activity.finish()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        logoutStarted = false
                        errorMessage = error.message ?: "로그아웃하지 못했습니다. 다시 시도해 주세요."
                        showErrorDialog = true
                    } finally {
                        isLoggingOut = false
                    }
                }
            },
            onStopRunningClick = { showStopDeleteDialog = true },
            onResumeTask = resume@{ task ->
                if (restoringTaskId != null) return@resume
                if (!DeleteTaskStartValidator.hasCompleteGalleryMap(task.selectedGalleries, task.galleryMap)) {
                    errorMessage = "저장된 작업의 갤러리 정보를 복원하지 못했습니다."
                    showErrorDialog = true
                    return@resume
                }
                activeResumeTaskId = task.id
                onResumeTaskConsumed()
                switchEditedType(task.deleteType)
                selectedTab = 0
                isCompleted = false
                selectedGallList = task.selectedGalleries
                val followUp = task.followUp
                deleteBoth = followUp != null
                noDeleteTarget = false
                runningPhases = if (followUp != null) listOf(task.deleteType, followUp.deleteType) else emptyList()
                if (followUp != null) {
                    commentFirst = task.deleteType == "comment"
                    otherSelectedGallList = followUp.selectedGalleries
                    otherDateFilterEnabled = followUp.dateFilterEnabled
                    otherDeleteNewestFirst = followUp.deleteNewestFirst
                    otherMinPostAgeDaysToDelete = followUp.minPostAgeDaysToDelete.takeIf { it >= 0 }?.toString() ?: "5"
                    otherTrioStashed = true
                }
                restoringTaskId = task.id
                restoringMessage = "저장된 설정으로 이어서 삭제를 시작합니다"
                coroutine.launch {
                    kotlinx.coroutines.delay(300)
                    restoringTaskId = null
                    isDeleting = true
                    showDeleteProgressDialog = true
                    // PC처럼 지금 등록된 키로 이어 한다(작업에 저장된 옛 키나 빈 키로 덮어쓰지 않게).
                    if (!serviceManager.resumeDeletion(cleaner, task.copy(twoCaptchaApiKey = twocaptchaKey))) {
                        isDeleting = false
                        showDeleteProgressDialog = false
                        activeResumeTaskId = null
                    }
                }
            },
            onDeleteTask = { deleteTaskToRemove = it },
            onDeleteAllTasks = {
                if (!isServiceDeleting && !isDeleting && restoringTaskId == null) {
                    val taskIds = interruptedTasks.map { it.id }
                    coroutine.launch {
                        val failedIds = withContext(session.storageDispatcher) {
                            taskIds.filterNot(deleteTaskStore::remove)
                        }
                        interruptedTasks = withContext(session.storageDispatcher) {
                            deleteTaskStore.getForLogin(cleaner.getUserId())
                        }
                        if (resumeTaskId != null && resumeTaskId in taskIds && resumeTaskId !in failedIds) {
                            onResumeTaskConsumed()
                        }
                        if (failedIds.isNotEmpty()) {
                            errorMessage = "작업 기록 ${failedIds.size}개를 삭제하지 못했습니다."
                            showErrorDialog = true
                        }
                    }
                }
            },
            onTabChange = { selectedTab = it },
            onDeveloperModeToggle = {
                developerMode = !developerMode
                saveDeveloperMode(appContext, developerMode)
            },
            onRemoteDeletionFinished = ::requestUserDataRefresh,
            // 백그라운드에서는 화면이 얼어 확인하지 못하므로 포그라운드 서비스가 확인하고 알린다.
            onChargeWaiting = { purchase ->
                if (purchase == null) ChargeWatchService.stop(appContext)
                else ChargeWatchService.start(appContext, purchase.id, purchase.amountWon, purchase.creditedCount)
            },
            onStopForProxyHandOff = {
                serviceManager.stopForHandOff().also { stopped ->
                    if (stopped) {
                        // 이어하기로 복원한 선택에는 이미 다 지워 목록에서 빠진 갤러리가 남을 수 있어, 시작 창을 열기 전에 거른다.
                        selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(selectedGallList, gallListOf(deleteType))
                        otherSelectedGallList = otherSelectedGallList?.let {
                            DeleteTaskStartValidator.retainAvailableSelection(it, gallListOf(otherTypeOf(deleteType)))
                        }
                        runningPhases = emptyList()
                        interruptedTasks = withContext(session.storageDispatcher) { deleteTaskStore.getForLogin(cleaner.getUserId()) }
                    }
                }
            },
            onStartCommentCleaner = { keywords, intervalSeconds, monitorMinutes ->
                serviceManager.startCommentCleaner(cleaner, keywords, intervalSeconds, monitorMinutes)
            },
            onStopCommentCleaner = serviceManager::stopCommentCleaner,
            onDeleteTypeChange = { newDeleteType ->
                switchEditedType(newDeleteType)
            },
            onDeleteTargetsChange = { targets ->
                noDeleteTarget = targets.isEmpty()
                deleteBoth = targets.size == 2
                // 하나만 남으면 그 종류를 지울 대상(=설정 중인 종류)으로 맞춘다.
                targets.singleOrNull()?.let { if (it != deleteType) switchEditedType(it) }
                saveDeleteTargets(appContext, targets)
            },
            onCommentFirstChange = {
                commentFirst = it
                saveDeleteCommentFirst(appContext, it)
            },
            onTwocaptchaKeyChange = {
                twocaptchaKey = it
                if (it.isBlank()) {
                    coroutine.launch(session.storageDispatcher) { removeSavedTwoCaptchaKey(appContext) }
                    cleaner.restore2CaptchaKey("")
                    // 도는 작업도 키를 지운 상태로 맞춘다(진행 화면 표시·다음 단계·이어 하기에 옛 키가 남지 않게).
                    serviceManager.useTwoCaptchaKey("")
                }
            },
            onTwocaptchaValidChange = { isTwocaptchaValid = it },
            onIsCheckingTwocaptchaChange = { isCheckingTwocaptcha = it },
            onShowDeleteDialog = showDeleteDialog@{
                if (noDeleteTarget) {
                    errorMessage = "지울 것(게시글·댓글)을 먼저 골라 주세요."
                    showErrorDialog = true
                    return@showDeleteDialog
                }
                val types = if (deleteBoth) listOf("comment", "posting") else listOf(deleteType)
                for (type in types) {
                    val selection = if (type == deleteType) selectedGallList else otherSelection()
                    if (selection.isEmpty()) {
                        errorMessage = when {
                            !deleteBoth -> "삭제할 갤러리를 먼저 골라 주세요."
                            type == "posting" -> "게시글을 지울 갤러리를 먼저 골라 주세요."
                            else -> "댓글을 지울 갤러리를 먼저 골라 주세요."
                        }
                        showErrorDialog = true
                        return@showDeleteDialog
                    }
                }
                if (!types.all(::phaseHasCompleteMap)) {
                    errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                    showErrorDialog = true
                    return@showDeleteDialog
                }
                try {
                    postContentRegex.toDeletionRegex("posting" in types && postContentFilterEnabled, "글 제목")
                    commentContentRegex.toDeletionRegex("comment" in types && commentContentFilterEnabled, "댓글 내용")
                } catch (e: IllegalArgumentException) {
                    errorMessage = e.message ?: "삭제 조건을 확인해 주세요."
                    showErrorDialog = true
                    return@showDeleteDialog
                }
                showDeleteConfirmDialog = true
                isCheckingDeletionSession = true
                deletionSessionCheckJob = coroutine.launch {
                    when (cleaner.checkLoginSession()) {
                        false -> {
                            showDeleteConfirmDialog = false
                            isCheckingDeletionSession = false
                            handleExpiredGallListSession()
                        }
                        null -> {
                            showDeleteConfirmDialog = false
                            isCheckingDeletionSession = false
                            errorMessage = "로그인 상태를 확인하지 못했습니다.\n잠시 후 다시 시도해 주세요."
                            showErrorDialog = true
                        }
                        true -> isCheckingDeletionSession = false
                    }
                }
            },
            onSelectedGallListChange = { selectedGallList = it },
            onMinRecommendToKeepChange = { minRecommendToKeep = it },
            onMinCommentToKeepChange = { minCommentToKeep = it },
            onMinViewToKeepChange = { minViewToKeep = it },
            onRecommendFilterEnabledChange = { recommendFilterEnabled = it },
            onCommentFilterEnabledChange = { commentFilterEnabled = it },
            onViewFilterEnabledChange = { viewFilterEnabled = it },
            onPostContentFilterEnabledChange = { postContentFilterEnabled = it },
            onPostContentRegexChange = { postContentRegex = it },
            onMyPostFilterEnabledChange = { myPostFilterEnabled = it },
            onDcconOnlyFilterEnabledChange = { dcconOnlyFilterEnabled = it },
            onCommentContentFilterEnabledChange = { commentContentFilterEnabled = it },
            onCommentContentRegexChange = { commentContentRegex = it },
            onDateFilterEnabledChange = { dateFilterEnabled = it },
            onDeleteNewestFirstChange = { deleteNewestFirst = it },
            onDeleteQuestionPostsChange = { deleteQuestionPosts = it },
            onMinPostAgeDaysToDeleteChange = { minPostAgeDaysToDelete = it },
            onRecordGuestbookLogChange = { enabled ->
                recordGuestbookLog = enabled
                saveRecordGuestbookLog(appContext, enabled)
            },
            onCaptchaSectionPosition = { captchaSectionScrollY = it },
            onDeleteOptionsSectionPosition = { deleteOptionsSectionScrollY = it },
            onFilterOptionsSectionPosition = { filterOptionsSectionScrollY = it },
            onValidateTwocaptchaKey = { key ->
                cleaner.set2CaptchaKey(key).also { valid ->
                    if (valid) serviceManager.useTwoCaptchaKey(key)
                }
            },
            onOpenUpdate = {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/dccleaner3/dccleaner/releases/latest")))
            },
            onDismissError = { showErrorDialog = false },
            onConfirmDeleteTaskRecord = {
                val task = deleteTaskToRemove
                deleteTaskToRemove = null
                if (task != null) coroutine.launch {
                    if (withContext(session.storageDispatcher) { deleteTaskStore.remove(task.id) }) {
                        interruptedTasks = withContext(session.storageDispatcher) { deleteTaskStore.getForLogin(cleaner.getUserId()) }
                        if (resumeTaskId == task.id) onResumeTaskConsumed()
                    } else {
                        errorMessage = "저장된 삭제 작업 기록을 삭제하지 못했습니다."
                        showErrorDialog = true
                    }
                }
            },
            onDismissDeleteTaskRecord = { deleteTaskToRemove = null },
            onConfirmStartDeletion = {
                showDeleteConfirmDialog = false
                coroutine.launch startDeletion@{
                    deletionSessionCheckJob = null
                    isCheckingDeletionSession = false
                    val firstType = if (!deleteBoth) deleteType else if (commentFirst) "comment" else "posting"
                    val secondType = otherTypeOf(firstType).takeIf { deleteBoth }
                    if (!listOfNotNull(firstType, secondType).all(::phaseHasCompleteMap)) {
                        errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                        showErrorDialog = true
                        return@startDeletion
                    }
                    val first = phaseOf(firstType)
                    val followUp = secondType?.let(::phaseOf)
                    runningPhases = if (followUp != null) listOf(firstType, followUp.deleteType) else emptyList()
                    restoringTaskId = null
                    activeResumeTaskId = null
                    onResumeTaskConsumed()
                    isDeleting = true
                    isCompleted = false
                    showDeleteProgressDialog = true
                    deleteLog = emptyList()
                    currentProgress = 0f
                    deletedPosts = 0
                    totalPosts = 0
                    val started = serviceManager.startDeletion(
                        cleaner = cleaner,
                        selectedGalleries = first.selectedGalleries,
                        deleteType = first.deleteType,
                        galleryMap = first.galleryMap,
                        twoCaptchaApiKey = twocaptchaKey,
                        recommendFilterEnabled = recommendFilterEnabled,
                        commentFilterEnabled = commentFilterEnabled,
                        viewFilterEnabled = viewFilterEnabled,
                        postContentFilterEnabled = postContentFilterEnabled,
                        commentContentFilterEnabled = commentContentFilterEnabled,
                        dateFilterEnabled = first.dateFilterEnabled,
                        deleteNewestFirst = first.deleteNewestFirst,
                        deleteQuestionPosts = deleteQuestionPosts,
                        minRecommendToKeep = if (recommendFilterEnabled) minRecommendToKeep.toIntOrNull() ?: 1 else -1,
                        minCommentToKeep = if (commentFilterEnabled) minCommentToKeep.toIntOrNull() ?: 1 else -1,
                        minViewToKeep = if (viewFilterEnabled) minViewToKeep.toIntOrNull() ?: 1 else -1,
                        myPostFilterEnabled = myPostFilterEnabled,
                        dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                        postContentRegex = if (postContentFilterEnabled) postContentRegex else "",
                        commentRegexFilter = if (commentContentFilterEnabled) commentContentRegex else "",
                        minPostAgeDaysToDelete = first.minPostAgeDaysToDelete,
                        recordGuestbookLog = recordGuestbookLog,
                        followUp = followUp
                    )
                    if (!started) {
                        isDeleting = false
                        showDeleteProgressDialog = false
                        runningPhases = emptyList()
                    }
                }
            },
            onDismissStartDeletion = {
                deletionSessionCheckJob?.cancel()
                deletionSessionCheckJob = null
                isCheckingDeletionSession = false
                showDeleteConfirmDialog = false
            },
            onCloseDeleteProgress = {
                showDeleteProgressDialog = false
                restoringTaskId = null
                activeResumeTaskId = null
                onResumeTaskConsumed()
                currentProgress = 0f
                deletedPosts = 0
                totalPosts = 0
                estimatedTimeLeft = 0L
                isCompleted = false
            },
            onCompleteDeleteProgress = {
                showDeleteProgressDialog = false
                restoringTaskId = null
                activeResumeTaskId = null
                onResumeTaskConsumed()
                serviceManager.dismissDeletionNotification()
                runningPhases = emptyList()
                currentProgress = 0f
                deletedPosts = 0
                totalPosts = 0
                estimatedTimeLeft = 0L
                isCompleted = false
            },
            onStopDeleteRequest = { showStopDeleteDialog = true },
            onConfirmStopDelete = {
                showStopDeleteDialog = false
                serviceManager.stopDeletion()
                deleteLog = deleteLog + "🛑 사용자에 의해 중단됨"
            },
            onDismissStopDelete = { showStopDeleteDialog = false },
            onOpenGallogForCaptcha = {
                val userId = serviceTaskLoginId.ifBlank { serviceManager.getCurrentTaskLoginId() }.ifBlank { cleaner.getUserId() }
                if (userId.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://gallog.dcinside.com/$userId")))
            },
            onResolveCaptcha = { serviceManager.resolveCaptcha() },
            onOpenAutoCaptchaGuide = {
                openLink(context, "https://dccleaner3.github.io/dccleaner/help#captcha-auto", isDarkTheme)
            },
            onConfirmDeleteAccount = {
                val account = accountToDelete
                showDeleteAccountDialog = false
                accountToDelete = null
                if (account != null) coroutine.launch {
                    val updatedAccounts = withContext(session.storageDispatcher) { removeSavedAccount(appContext, account.id) }
                    if (updatedAccounts != null) savedAccounts = updatedAccounts
                    else {
                        errorMessage = "저장된 계정을 삭제하지 못했습니다. 기존 데이터는 보존합니다."
                        showErrorDialog = true
                    }
                }
            },
            onDismissDeleteAccount = {
                showDeleteAccountDialog = false
                accountToDelete = null
            },
            onGuestbookUserListTextChange = { guestbookUserListText = it },
            onGuestbookMessageTextChange = { guestbookMessageText = it },
            onGuestbookShowConfirmDialogChange = { guestbookShowConfirmDialog = it },
            onGuestbookShowResultDialogChange = { guestbookShowResultDialog = it },
            onCloseGuestbookProgress = { showGuestbookProgressDialog = false },
            onGuestbookIsSendingChange = { },
            onGuestbookProgressDoneChange = { },
            onGuestbookProgressTotalChange = { },
            onGuestbookSuccessCountChange = { },
            onGuestbookFailCountChange = { },
            onFilterGuestbookCachedUsers = { userIds ->
                val cachedUserIds = guestbookSentUserCache.loadUserIds(cleaner.getUserId())
                withContext(Dispatchers.Default) {
                    val userIdsToSend = userIds.filterNot(cachedUserIds::contains)
                    GuestbookCacheFilterResult(
                        userIdsToSend = userIdsToSend,
                        skippedCount = userIds.size - userIdsToSend.size
                    )
                }
            },
            onClearGuestbookCachedUsers = {
                guestbookSentUserCache.clear(cleaner.getUserId()).also { cleared ->
                    if (cleared) guestbookCachedUserCount = 0
                }
            },
            onResolveGuestbookUserList = { url -> GuestbookUserListFetcher.fetch(url) },
            onStartGuestbookSend = { ids, message ->
                if (serviceManager.startGuestbook(cleaner, ids, message)) {
                    showGuestbookProgressDialog = true
                }
            }
        ),
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutine,
        localBannerJson = if (BuildConfig.DEBUG) BuildConfig.LOCAL_AD_JSON else null,
        applySystemBarsPadding = true,
        proxyCleaner = proxyCleaner
    )
    if (isLoggingOut) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("로그아웃 중") },
            text = { Text("진행 중인 요청을 중단하고 작업 기록을 보존하고 있습니다.") },
            confirmButton = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false, dismissOnClickOutside = false
            )
        )
    }

}
