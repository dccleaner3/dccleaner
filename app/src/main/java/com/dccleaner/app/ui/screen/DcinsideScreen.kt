package com.dccleaner.app.ui.screen

import com.dccleaner.app.MainActivity
import com.dccleaner.app.BuildConfig
import com.dccleaner.app.model.*
import com.dccleaner.app.model.dccleanerUiColors
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.CleanerLogSink
import com.dccleaner.app.network.GuestbookUserListFetcher
import com.dccleaner.app.service.ServiceManager
import com.dccleaner.app.storage.addSavedAccount
import com.dccleaner.app.storage.getSavedAccounts
import com.dccleaner.app.storage.getSavedTwoCaptchaKey
import com.dccleaner.app.storage.removeSavedAccount
import com.dccleaner.app.storage.removeSavedTwoCaptchaKey
import com.dccleaner.app.storage.saveTwoCaptchaKey
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.storage.GuestbookSentUserCache
import com.dccleaner.app.storage.getSavedRecordGuestbookLog
import com.dccleaner.app.storage.getSavedDeveloperMode
import com.dccleaner.app.storage.saveDeveloperMode
import com.dccleaner.app.storage.saveRecordGuestbookLog
import com.dccleaner.app.util.LogManager
import com.dccleaner.app.update.ReleaseVersionChecker
import com.dccleaner.app.ui.card.*
import com.dccleaner.app.ui.cleaner.DcCleanerTabContent
import com.dccleaner.app.ui.dialog.*
import com.dccleaner.app.ui.guestbook.GuestbookTabContent
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
import com.dccleaner.app.runtime.previewDeletion
import android.app.Activity
import android.content.Intent
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
    isDarkTheme: Boolean = false,
    onDarkThemeChange: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current
    val appContext = context.applicationContext
    val cleaner = remember(appContext) {
        val logManager = LogManager(appContext)
        Cleaner(CleanerLogSink { tag, message ->
            logManager.addLog(tag, message)
        })
    }
    val serviceManager = remember(appContext) { ServiceManager(appContext) }
    val deleteTaskStore = remember(appContext) { DeleteTaskStore(appContext) }
    val guestbookSentUserCache = remember(appContext) { GuestbookSentUserCache(appContext) }
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


    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var loginInfo by remember { mutableStateOf<UserInfo?>(null) }
    var saveLogin by remember { mutableStateOf(true) } // 로그인 정보 저장 여부 (기본값: true)
    var savedAccounts by remember { mutableStateOf<List<SavedAccount>>(emptyList()) }
    var interruptedTasks by remember { mutableStateOf<List<DeleteTaskProgress>>(emptyList()) }
    var isLoggingIn by remember { mutableStateOf(false) } // 로그인 중 상태
    var sessionExpired by remember { mutableStateOf(false) }


    var postingGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var commentGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedGallList by remember { mutableStateOf<List<String>>(emptyList()) }
    var userDataRefreshRequest by remember { mutableIntStateOf(0) }
    var sessionCheckRequest by remember { mutableIntStateOf(0) }


    var twocaptchaKey by remember { mutableStateOf("") }
    var isTwocaptchaValid by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingTwocaptcha by remember { mutableStateOf(false) }

    var deleteType by remember { mutableStateOf("posting") }
    var deleteLog by remember { mutableStateOf<List<String>>(emptyList()) }

    // 삭제 조건 설정
    var minRecommendToKeep by remember { mutableStateOf("1") }
    var minCommentToKeep by remember { mutableStateOf("1") }
    var minViewToKeep by remember { mutableStateOf("1") }
    var recommendFilterEnabled by remember { mutableStateOf(false) }
    var commentFilterEnabled by remember { mutableStateOf(false) }
    var viewFilterEnabled by remember { mutableStateOf(false) }
    var postContentFilterEnabled by remember { mutableStateOf(false) }
    var postContentRegex by remember { mutableStateOf("") }
    var myPostFilterEnabled by remember { mutableStateOf(false) }
    var dcconOnlyFilterEnabled by remember { mutableStateOf(false) }
    var commentContentFilterEnabled by remember { mutableStateOf(false) }
    var commentContentRegex by remember { mutableStateOf("") }
    var dateFilterEnabled by remember { mutableStateOf(false) }
    var deleteNewestFirst by remember { mutableStateOf(false) }
    var deleteQuestionPosts by remember { mutableStateOf(false) }
    var minPostAgeDaysToDelete by remember { mutableStateOf("5") }
    var recordGuestbookLog by remember(appContext) {
        mutableStateOf(getSavedRecordGuestbookLog(appContext))
    }


    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isInspectingDeletion by remember { mutableStateOf(false) }
    var deletionInspectionMessage by remember { mutableStateOf("") }
    var deletionPreview by remember { mutableStateOf<DeletionPreview?>(null) }
    var deletionInspectionJob by remember { mutableStateOf<Job?>(null) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }
    var showStopDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember {
        mutableStateOf(
            deleteTaskStore.getAll().any { task ->
                task.state == DeleteTaskState.RUNNING ||
                        task.state == DeleteTaskState.CAPTCHA_REQUIRED
            }
        )
    }
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
    var selectedTab by remember { mutableStateOf(0) }
    var developerMode by remember(appContext) {
        mutableStateOf(getSavedDeveloperMode(appContext))
    }

    // 대왕콘 관련 상태
    var daewangconGalleryId by remember { mutableStateOf("") }
    var daewangconPostNo by remember { mutableStateOf("") }
    var daewangconPostSubject by remember { mutableStateOf("테스트 제목") }
    var daewangconPostContent by remember { mutableStateOf("테스트 내용") }
    var daewangconCommentContent by remember { mutableStateOf("테스트 댓글") }
    var showDaewangconDialog by remember { mutableStateOf(false) }
    var showDaewangconProgressDialog by remember { mutableStateOf(false) }
    var showStopDaewangconDialog by remember { mutableStateOf(false) }
    var daewangconServerProgress by remember { mutableStateOf<DaewangconProgress?>(null) }

    // 버전 체크 관련 상태
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingVersion by remember { mutableStateOf(false) }


    val isServiceDeleting by serviceManager.isDeleting.collectAsStateWithLifecycle()
    val isServiceConnected by serviceManager.isServiceConnected.collectAsStateWithLifecycle()
    val serviceProgress by serviceManager.progress.collectAsStateWithLifecycle()
    val serviceCurrentGallery by serviceManager.currentGallery.collectAsStateWithLifecycle()
    val serviceCurrentGalleryEstimatedTimeLeft by serviceManager.currentGalleryEstimatedTimeLeft.collectAsStateWithLifecycle()
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

    // 대왕콘 관련 서비스 상태
    val isDaewangconRunning by serviceManager.isDaewangconRunning.collectAsStateWithLifecycle()
    val isDaewangconCompleted by serviceManager.isDaewangconCompleted.collectAsStateWithLifecycle()
    val daewangconErrorMessage by serviceManager.daewangconErrorMessage.collectAsStateWithLifecycle()
    val daewangconProgress by serviceManager.daewangconProgress.collectAsStateWithLifecycle()
    val daewangconLog by serviceManager.daewangconLog.collectAsStateWithLifecycle()
    val daewangconPostCount by serviceManager.daewangconPostCount.collectAsStateWithLifecycle()
    val daewangconCommentCount by serviceManager.daewangconCommentCount.collectAsStateWithLifecycle()
    val serviceGuestbookIsSending by serviceManager.isGuestbookSending.collectAsStateWithLifecycle()
    val commentCleanerRunning by serviceManager.isCommentCleanerRunning.collectAsStateWithLifecycle()

    LaunchedEffect(loginInfo != null, commentCleanerRunning) {
        if (loginInfo != null && !commentCleanerRunning) {
            val settings = getSavedCommentCleanerSettings(appContext)
            if (settings.enabled && settings.keywords.isNotEmpty()) {
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
    var guestbookUserListText by remember { mutableStateOf("") }
    var guestbookMessageText by remember { mutableStateOf("") }
    var guestbookShowConfirmDialog by remember { mutableStateOf(false) }
    var guestbookShowResultDialog by remember { mutableStateOf(false) }
    var showGuestbookProgressDialog by remember { mutableStateOf(false) }
    var guestbookCachedUserCount by remember { mutableIntStateOf(0) }

    var deleteTaskWasRunning by remember { mutableStateOf(false) }
    var daewangconTaskWasRunning by remember { mutableStateOf(false) }
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
        daewangconServerProgress = null
        id = expiredLoginId
        pw = reloginPassword
        sessionExpired = true
        postingGallList = emptyMap()
        commentGallList = emptyMap()
        selectedGallList = emptyList()
        interruptedTasks = emptyList()
        deleteLog = emptyList()
        showDeleteProgressDialog = false
        showDaewangconProgressDialog = false
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
            daewangconServerProgress = null
            id = serviceTaskLoginId
            postingGallList = emptyMap()
            commentGallList = emptyMap()
            selectedGallList = emptyList()
        } else {
            try {
                loginInfo = cleaner.getUserInfo()
                daewangconServerProgress = cleaner.getDaewangconProgress()

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

                val refreshedGallList = if (deleteType == "posting") {
                    postingGallList
                } else {
                    commentGallList
                }
                selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(
                    selectedGallList,
                    refreshedGallList
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("DcinsideScreen", "사용자 데이터 새로고침 오류", e)
            }
        }

        interruptedTasks = deleteTaskStore.getForLogin(
            uiLoginId.ifBlank { serviceTaskLoginId }
        )
    }

    LaunchedEffect(isServiceDeleting) {
        if (deleteTaskWasRunning && !isServiceDeleting) {
            requestUserDataRefresh()
        }
        deleteTaskWasRunning = isServiceDeleting
    }

    LaunchedEffect(isDaewangconRunning) {
        if (daewangconTaskWasRunning && !isDaewangconRunning) {
            requestUserDataRefresh()
        }
        daewangconTaskWasRunning = isDaewangconRunning
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

    LaunchedEffect(isDaewangconRunning, isDaewangconCompleted, daewangconErrorMessage) {
        if (isDaewangconRunning || isDaewangconCompleted || daewangconErrorMessage != null) {
            showDaewangconProgressDialog = true
        }
    }

    LaunchedEffect(Unit) {
        val savedKey = getSavedTwoCaptchaKey(appContext)
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
            saveTwoCaptchaKey(appContext, twocaptchaKey)
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
        // 저장된 계정 정보 로드
        savedAccounts = getSavedAccounts(context)
        onDispose {
            lifecycle.removeObserver(observer)
            serviceManager.unbindService()
        }
    }

    LaunchedEffect(resumeTaskId) {
        resumeTaskId?.let(deleteTaskStore::get)?.let { task ->
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
                deleteType = serviceDeleteType
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
                interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())
            } else if (serviceTaskLoginId.isNotBlank()) {
                id = serviceTaskLoginId
            }
        }
    }

    LaunchedEffect(serviceDeleteType, isServiceDeleting) {
        if (isServiceDeleting &&
            (serviceDeleteType == "posting" || serviceDeleteType == "comment")
        ) {
            deleteType = serviceDeleteType
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

    DccleanerScreenContent(
        state = DccleanerScreenState(
            uiColors = uiColors,
            isDarkTheme = isDarkTheme,
            id = id,
            pw = pw,
            loginInfo = loginInfo,
            daewangconServerProgress = daewangconServerProgress,
            saveLogin = saveLogin,
            savedAccounts = savedAccounts,
            isLoggingIn = isLoggingIn,
            sessionExpired = sessionExpired,
            deleteUiActive = deleteUiActive,
            runningLoginId = serviceTaskLoginId,
            displayedGallery = displayedGallery,
            displayedProgress = displayedProgress,
            displayedDeletedCount = displayedDeletedCount,
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
            isInspectingDeletion = isInspectingDeletion,
            deletionInspectionMessage = deletionInspectionMessage,
            deletionPreview = deletionPreview,
            showDeleteConfirmDialog = showDeleteConfirmDialog,
            activeFilters = buildList {
                if (deleteType == "posting" && recommendFilterEnabled) add("추천 ${minRecommendToKeep.ifBlank { "1" }}개 이상 보존")
                if (deleteType == "posting" && commentFilterEnabled) add("댓글 ${minCommentToKeep.ifBlank { "1" }}개 이상 보존")
                if (deleteType == "posting" && viewFilterEnabled) add("조회 ${minViewToKeep.ifBlank { "1" }}회 이상 보존")
                if (deleteType == "posting" && postContentFilterEnabled) add("글 정규식")
                if (deleteNewestFirst) {
                    add(if (deleteType == "posting") "최근 글부터 삭제" else "최근 댓글부터 삭제")
                }
                if (deleteType == "posting" && deleteQuestionPosts) add("질문글도 삭제")
                if (deleteType == "comment" && myPostFilterEnabled) add("내 글 필터")
                if (deleteType == "comment" && dcconOnlyFilterEnabled) add("디시콘 전용")
                if (deleteType == "comment" && commentContentFilterEnabled) add("댓글 정규식")
                if (dateFilterEnabled) add("작성 후 ${minPostAgeDaysToDelete.ifBlank { "5" }}일 이상")
                if (twocaptchaKey.isNotBlank()) add("2Captcha 자동 해결")
            },
            showDeleteProgressDialog = showDeleteProgressDialog,
            progressDeleteType = deleteType,
            isCompleted = isCompleted,
            isDeleting = deleteUiActive,
            estimatedTimeLeft = estimatedTimeLeft,
            nextCaptchaEstimatedTimeLeft = serviceNextCaptchaEstimatedTimeLeft,
            isTwoCaptchaConfigured = serviceIsTwoCaptchaConfigured,
            showStopDeleteDialog = showStopDeleteDialog,
            showCaptchaDialog = serviceShowCaptchaDialog,
            accountToDelete = accountToDelete,
            showDeleteAccountDialog = showDeleteAccountDialog,
            showDaewangconDialog = showDaewangconDialog,
            showDaewangconProgressDialog = showDaewangconProgressDialog,
            showStopDaewangconDialog = showStopDaewangconDialog,
            isDaewangconRunning = isDaewangconRunning,
            isDaewangconCompleted = isDaewangconCompleted,
            daewangconErrorMessage = daewangconErrorMessage,
            daewangconProgress = daewangconProgress,
            daewangconLog = daewangconLog,
            daewangconPostCount = daewangconPostCount,
            daewangconCommentCount = daewangconCommentCount,
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
                    isLoggingIn = true
                    daewangconServerProgress = null
                    val success = cleaner.login(id, pw)
                    if (!success) {
                        Log.w("DcinsideScreen", "로그인 실패 감지")
                        errorMessage = "로그인에 실패했습니다.\n아이디와 비밀번호를 확인해주세요."
                        showErrorDialog = true
                        isLoggingIn = false
                        return@launch
                    }
                    if (saveLogin && id.isNotBlank() && pw.isNotBlank()) {
                        val userInfo = cleaner.getUserInfo()
                        val updatedAccounts = addSavedAccount(
                            appContext,
                            SavedAccount(id, pw, userInfo.nickname)
                        )
                        if (updatedAccounts != null) {
                            savedAccounts = updatedAccounts
                        } else {
                            snackbarHostState.showSnackbar("로그인 정보를 저장하지 못했습니다.")
                        }
                    }
                    loginInfo = cleaner.getUserInfo()
                    sessionExpired = false
                    daewangconServerProgress = cleaner.getDaewangconProgress()
                    interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())
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
                    selectedGallList = if (deleteType == "posting") postingGallList.keys.toList() else commentGallList.keys.toList()
                    isLoggingIn = false
                }
            },
            onLogoutClick = {
                val activity = context as Activity
                activity.finishAffinity()
                val intent = Intent(activity, MainActivity::class.java)
                activity.startActivity(intent)
                Runtime.getRuntime().exit(0)
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
                deleteType = task.deleteType
                selectedTab = 0
                isCompleted = false
                selectedGallList = task.selectedGalleries
                restoringTaskId = task.id
                restoringMessage = "저장된 설정으로 이어서 삭제를 시작합니다"
                coroutine.launch {
                    kotlinx.coroutines.delay(300)
                    restoringTaskId = null
                    isDeleting = true
                    showDeleteProgressDialog = true
                    if (!serviceManager.resumeDeletion(cleaner, task)) {
                        isDeleting = false
                        showDeleteProgressDialog = false
                        activeResumeTaskId = null
                    }
                }
            },
            onDeleteTask = { deleteTaskToRemove = it },
            onTabChange = { selectedTab = it },
            onDeveloperModeToggle = {
                developerMode = !developerMode
                saveDeveloperMode(appContext, developerMode)
            },
            onRequestWriteCookies = cleaner::getWebViewSessionCookies,
            onStartCommentCleaner = { keywords, intervalSeconds, monitorMinutes ->
                serviceManager.startCommentCleaner(cleaner, keywords, intervalSeconds, monitorMinutes)
            },
            onStopCommentCleaner = serviceManager::stopCommentCleaner,
            onOpenManual = { tab ->
                val manualUrl = if (tab == 0) "https://dccleaner3.github.io/dccleaner/cleaner" else "https://dccleaner3.github.io/dccleaner/guestbook"
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(manualUrl)))
            },
            onDeleteTypeChange = { newDeleteType ->
                if (deleteType != newDeleteType) {
                    deleteType = newDeleteType
                    selectedGallList = if (newDeleteType == "posting") postingGallList.keys.toList() else commentGallList.keys.toList()
                }
            },
            onTwocaptchaKeyChange = {
                twocaptchaKey = it
                if (it.isBlank()) {
                    removeSavedTwoCaptchaKey(appContext)
                    cleaner.restore2CaptchaKey("")
                }
            },
            onTwocaptchaValidChange = { isTwocaptchaValid = it },
            onIsCheckingTwocaptchaChange = { isCheckingTwocaptcha = it },
            onShowDeleteDialog = showDeleteDialog@{
                val currentGallList = if (deleteType == "posting") postingGallList else commentGallList
                if (!DeleteTaskStartValidator.hasCompleteGalleryMap(selectedGallList, currentGallList)) {
                    errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                    showErrorDialog = true
                    return@showDeleteDialog
                }
                deletionPreview = null
                deletionInspectionMessage = "로그인 상태 확인 중"
                deletionInspectionJob = coroutine.launch {
                    when (cleaner.checkLoginSession()) {
                        false -> {
                            handleExpiredGallListSession()
                        }
                        null -> {
                            errorMessage = "로그인 상태를 확인하지 못했습니다.\n잠시 후 다시 시도해 주세요."
                            showErrorDialog = true
                        }
                        true -> {
                            try {
                                showDeleteConfirmDialog = true
                                isInspectingDeletion = true
                                deletionInspectionMessage = "미리보기를 불러오는 중..."
                                val result = previewDeletion(
                                    cleaner = cleaner,
                                    task = DeleteTaskProgress(
                                        loginId = cleaner.getUserId(),
                                        deleteType = deleteType,
                                        selectedGalleries = selectedGallList,
                                        galleryMap = DeleteTaskStartValidator.selectedGalleryMap(selectedGallList, currentGallList),
                                        recommendFilterEnabled = recommendFilterEnabled,
                                        commentFilterEnabled = commentFilterEnabled,
                                        viewFilterEnabled = viewFilterEnabled,
                                        postContentFilterEnabled = postContentFilterEnabled,
                                        commentContentFilterEnabled = commentContentFilterEnabled,
                                        dateFilterEnabled = dateFilterEnabled,
                                        deleteNewestFirst = deleteNewestFirst,
                                        deleteQuestionPosts = deleteQuestionPosts,
                                        minRecommendToKeep = if (recommendFilterEnabled) minRecommendToKeep.toIntOrNull() ?: 1 else -1,
                                        minCommentToKeep = if (commentFilterEnabled) minCommentToKeep.toIntOrNull() ?: 1 else -1,
                                        minViewToKeep = if (viewFilterEnabled) minViewToKeep.toIntOrNull() ?: 1 else -1,
                                        myPostFilterEnabled = myPostFilterEnabled,
                                        dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                                        postContentRegex = if (postContentFilterEnabled) postContentRegex else "",
                                        commentRegexFilter = if (commentContentFilterEnabled) commentContentRegex else "",
                                        minPostAgeDaysToDelete = if (dateFilterEnabled) minPostAgeDaysToDelete.toIntOrNull() ?: 5 else -1
                                    ),
                                    onUpdate = { deletionPreview = it }
                                )
                                deletionPreview = result
                                isInspectingDeletion = false
                                if (result.items.isEmpty()) {
                                    deletionInspectionMessage = "첫 페이지에 삭제 예정 항목이 없습니다."
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IllegalArgumentException) {
                                isInspectingDeletion = false
                                showDeleteConfirmDialog = false
                                errorMessage = e.message ?: "삭제 조건을 확인해 주세요."
                                showErrorDialog = true
                            } catch (e: Exception) {
                                isInspectingDeletion = false
                                deletionInspectionMessage = "미리보기를 불러오지 못했습니다. 바로 삭제할 수 있습니다."
                            }
                        }
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
            onValidateTwocaptchaKey = { cleaner.set2CaptchaKey(it) },
            onOpenUpdate = {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/dccleaner3/dccleaner/releases/latest")))
            },
            onDismissError = { showErrorDialog = false },
            onConfirmDeleteTaskRecord = {
                deleteTaskToRemove?.let { task ->
                    if (deleteTaskStore.remove(task.id)) {
                        interruptedTasks = deleteTaskStore.getForLogin(cleaner.getUserId())
                        if (resumeTaskId == task.id) onResumeTaskConsumed()
                    } else {
                        errorMessage = "저장된 삭제 작업 기록을 삭제하지 못했습니다."
                        showErrorDialog = true
                    }
                }
                deleteTaskToRemove = null
            },
            onDismissDeleteTaskRecord = { deleteTaskToRemove = null },
            onConfirmStartDeletion = {
                showDeleteConfirmDialog = false
                coroutine.launch startDeletion@{
                    deletionInspectionJob?.cancelAndJoin()
                    deletionInspectionJob = null
                    isInspectingDeletion = false
                    val currentGallList = if (deleteType == "posting") postingGallList else commentGallList
                    if (!DeleteTaskStartValidator.hasCompleteGalleryMap(selectedGallList, currentGallList)) {
                        errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                        showErrorDialog = true
                        return@startDeletion
                    }
                    val selectedGalleryMap = DeleteTaskStartValidator.selectedGalleryMap(selectedGallList, currentGallList)
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
                        selectedGalleries = selectedGallList,
                        deleteType = deleteType,
                        galleryMap = selectedGalleryMap,
                        twoCaptchaApiKey = twocaptchaKey,
                        recommendFilterEnabled = recommendFilterEnabled,
                        commentFilterEnabled = commentFilterEnabled,
                        viewFilterEnabled = viewFilterEnabled,
                        postContentFilterEnabled = postContentFilterEnabled,
                        commentContentFilterEnabled = commentContentFilterEnabled,
                        dateFilterEnabled = dateFilterEnabled,
                        deleteNewestFirst = deleteNewestFirst,
                        deleteQuestionPosts = deleteQuestionPosts,
                        minRecommendToKeep = if (recommendFilterEnabled) minRecommendToKeep.toIntOrNull() ?: 1 else -1,
                        minCommentToKeep = if (commentFilterEnabled) minCommentToKeep.toIntOrNull() ?: 1 else -1,
                        minViewToKeep = if (viewFilterEnabled) minViewToKeep.toIntOrNull() ?: 1 else -1,
                        myPostFilterEnabled = myPostFilterEnabled,
                        dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                        postContentRegex = if (postContentFilterEnabled) postContentRegex else "",
                        commentRegexFilter = if (commentContentFilterEnabled) commentContentRegex else "",
                        minPostAgeDaysToDelete = if (dateFilterEnabled) minPostAgeDaysToDelete.toIntOrNull() ?: 5 else -1,
                        recordGuestbookLog = recordGuestbookLog
                    )
                    if (!started) {
                        isDeleting = false
                        showDeleteProgressDialog = false
                    }
                }
            },
            onDismissStartDeletion = {
                deletionInspectionJob?.cancel()
                deletionInspectionJob = null
                isInspectingDeletion = false
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
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://dccleaner3.github.io/dccleaner/cleaner#-2captcha-%EC%9E%90%EB%8F%99-%ED%95%B4%EA%B2%B0")).addCategory(Intent.CATEGORY_BROWSABLE)
                )
            },
            onConfirmDeleteAccount = {
                accountToDelete?.let { account ->
                    removeSavedAccount(appContext, account.id)?.let {
                        savedAccounts = it
                    }
                }
                showDeleteAccountDialog = false
                accountToDelete = null
            },
            onDismissDeleteAccount = {
                showDeleteAccountDialog = false
                accountToDelete = null
            },
            onStartDaewangconRequest = { showDaewangconDialog = true },
            onConfirmStartDaewangcon = {
                showDaewangconDialog = false
                showDaewangconProgressDialog = true
                serviceManager.startDaewangcon(cleaner, daewangconGalleryId, daewangconPostNo, daewangconPostSubject, daewangconPostContent, daewangconCommentContent)
            },
            onDismissStartDaewangcon = { showDaewangconDialog = false },
            onCloseDaewangconProgress = {
                showDaewangconProgressDialog = false
                serviceManager.dismissDaewangconNotification()
            },
            onStopDaewangconRequest = { showStopDaewangconDialog = true },
            onConfirmStopDaewangcon = {
                showStopDaewangconDialog = false
                serviceManager.stopDaewangcon()
            },
            onDismissStopDaewangcon = { showStopDaewangconDialog = false },
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
        applySystemBarsPadding = true
    )
}
