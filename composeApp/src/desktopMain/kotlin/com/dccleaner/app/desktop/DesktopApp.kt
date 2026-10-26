package com.dccleaner.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.DeletePhase
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskStartValidator
import com.dccleaner.app.model.GallListResult
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.model.UserInfo
import com.dccleaner.app.model.dccleanerUiColors
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.runtime.CommentMonitorConfig
import com.dccleaner.app.runtime.runCommentMonitor
import com.dccleaner.app.ui.screen.desktopCommentMonitorStorage
import com.dccleaner.app.ui.screen.loadSavedCommentMonitor
import com.dccleaner.app.network.CleanerLogSink
import com.dccleaner.app.network.GuestbookUserListFetcher
import com.dccleaner.app.platform.DesktopExternalNavigator
import com.dccleaner.app.proxy.SupabaseProxyCleanerBackend
import com.dccleaner.app.runtime.DccleanerTaskController
import com.dccleaner.app.runtime.GuestbookExecutionRunner
import com.dccleaner.app.runtime.toDeletionRegex
import com.dccleaner.app.update.ReleaseVersionChecker
import com.dccleaner.app.ranking.GalleryRankingRunner
import com.dccleaner.app.ranking.GalleryRankingState
import com.dccleaner.app.ui.ranking.clearGalleryRanking
import com.dccleaner.app.ui.userposts.clearUserPostSearch
import com.dccleaner.app.ui.screen.DccleanerScreenActions
import com.dccleaner.app.ui.screen.DccleanerScreenContent
import com.dccleaner.app.ui.screen.DccleanerScreenState
import com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult
import com.dccleaner.app.ui.proxy.ProxyCleanerController
import com.dccleaner.app.ui.proxy.proxyJobFinishedMessage
import androidx.compose.ui.platform.LocalWindowInfo
import com.dccleaner.app.ui.theme.DccleanerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun DesktopDccleanerApp(modifier: Modifier = Modifier) {
    val taskStore = remember { DesktopDeleteTaskStore() }
    val themeStore = remember { DesktopThemePreferenceStore() }
    val credentialStore = remember {
        if (credentialStorageSupported()) runCatching { DesktopCredentialStore() }.getOrNull() else null
    }
    val proxyStorage = remember { DesktopProxyCleanerStorage(credentialStore) }
    val proxyBackend = remember(proxyStorage) { SupabaseProxyCleanerBackend(proxyStorage) }
    val proxyCleaner = remember(proxyBackend) { ProxyCleanerController(proxyBackend, proxyStorage) }
    DisposableEffect(proxyBackend) { onDispose { proxyBackend.close() } }
    val logSink = remember { DesktopFileLogSink() }
    val cleaner = remember {
        Cleaner(logSink = CleanerLogSink { tag, message -> logSink.addLog(tag, message) })
    }
    // 트레이 아이콘이 하나만 생기도록 삭제 엔진과 초고속 삭제·충전 알림이 같은 알림기를 쓴다.
    val trayNotifier = remember { DesktopTrayNotifier() }
    val windowInfo = LocalWindowInfo.current
    // 갤창랭킹은 오래 걸려 창을 내려 두기 쉽다. 만들던 것이 끝났을 때 창을 보고 있지 않으면 알린다(취소는 알리지 않는다).
    LaunchedEffect(Unit) {
        var wasRunning = false
        GalleryRankingRunner.state.collect { state ->
            if (wasRunning && !windowInfo.isWindowFocused) when (state) {
                is GalleryRankingState.Done -> trayNotifier.notify("갤창랭킹", "${state.result.gallery.name} 갤창랭킹이 완성됐어요")
                // 이유 문구가 이미 "갤창랭킹을 만들지 못했어요…"인 때가 있어, 실패는 제목에만 쓴다.
                is GalleryRankingState.Failed -> trayNotifier.notify("갤창랭킹을 만들지 못했어요", state.message.removePrefix("갤창랭킹을 만들지 못했어요. "))
                else -> Unit
            }
            wasRunning = state is GalleryRankingState.Running
        }
    }
    val controller = remember {
        DccleanerTaskController(
            deleteTaskStore = taskStore,
            logSink = logSink,
            notifier = trayNotifier
        ).also { it.setCleaner(cleaner) }
    }
    DisposableEffect(Unit) {
        onDispose { controller.close() }
    }

    val coroutine = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkTheme by remember {
        mutableStateOf(themeStore.getDarkTheme(systemDarkTheme))
    }
    val uiColors = remember(isDarkTheme) { dccleanerUiColors(isDarkTheme) }

    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var loginInfo by remember { mutableStateOf<UserInfo?>(null) }
    var saveLogin by remember { mutableStateOf(credentialStore != null) }
    var savedAccounts by remember { mutableStateOf(emptyList<SavedAccount>()) }
    var interruptedTasks by remember { mutableStateOf<List<DeleteTaskProgress>>(emptyList()) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var sessionExpired by remember { mutableStateOf(false) }
    var postingGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var commentGallList by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedGallList by remember { mutableStateOf<List<String>>(emptyList()) }
    var twocaptchaKey by remember { mutableStateOf("") }
    var isTwocaptchaValid by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingTwocaptcha by remember { mutableStateOf(false) }
    // 마지막에 고른 지울 대상. 처음이면 아무것도 안 고른 상태로 시작한다.
    val savedDeleteTargets = remember { themeStore.getDeleteTargets() }
    var deleteType by remember { mutableStateOf(if (savedDeleteTargets == setOf("comment")) "comment" else "posting") }
    var deleteLog by remember { mutableStateOf<List<String>>(emptyList()) }

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
    // 글+댓글 함께 지우기. 위 갤러리·날짜·최근부터 값은 편집 중인 종류 것이고, 다른 종류 것은 otherTypeSettings에 보관한다(null이면 아직 안 건드림).
    var deleteBoth by remember { mutableStateOf(savedDeleteTargets.size == 2) }
    var noDeleteTarget by remember { mutableStateOf(savedDeleteTargets.isEmpty()) }
    var commentFirst by remember { mutableStateOf(themeStore.getDeleteCommentFirst()) }
    var otherTypeSettings by remember { mutableStateOf<TypeSettings?>(null) }
    // true면 보관값의 갤러리 선택 대신 '그 종류 전체'를 쓴다. 다시 로그인해도 날짜 조건 등은 지키고 선택만 새로 고르게 한다.
    var otherSelectAll by remember { mutableStateOf(false) }
    var runningPhases by remember { mutableStateOf(emptyList<String>()) }
    var recordGuestbookLog by remember {
        mutableStateOf(themeStore.getRecordGuestbookLog())
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isCheckingDeletionSession by remember { mutableStateOf(false) }
    var deletionSessionCheckJob by remember { mutableStateOf<Job?>(null) }
    var showDeleteProgressDialog by remember { mutableStateOf(false) }
    var showStopDeleteDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<SavedAccount?>(null) }
    var deleteTaskToRemove by remember { mutableStateOf<DeleteTaskProgress?>(null) }
    var restoringTaskId by remember { mutableStateOf<String?>(null) }
    var restoringMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var developerMode by remember {
        mutableStateOf(themeStore.getDeveloperMode())
    }

    var guestbookUserListText by remember { mutableStateOf("") }
    var guestbookMessageText by remember { mutableStateOf("") }
    var guestbookShowConfirmDialog by remember { mutableStateOf(false) }
    var guestbookShowResultDialog by remember { mutableStateOf(false) }
    var showGuestbookProgressDialog by remember { mutableStateOf(false) }
    var guestbookIsSending by remember { mutableStateOf(false) }
    var guestbookProgressDone by remember { mutableStateOf(0) }
    var guestbookProgressTotal by remember { mutableStateOf(0) }
    var guestbookSuccessCount by remember { mutableStateOf(0) }
    var guestbookFailCount by remember { mutableStateOf(0) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingVersion by remember { mutableStateOf(false) }
    var userDataRefreshRequest by remember { mutableIntStateOf(0) }
    var deleteTaskWasRunning by remember { mutableStateOf(false) }
    var guestbookTaskWasRunning by remember { mutableStateOf(false) }

    LaunchedEffect(credentialStore) {
        if (credentialStore == null) {
            if (credentialStorageSupported()) {
                snackbarHostState.showSnackbar("운영체제 보안 저장소를 열지 못해 로그인 정보 저장을 사용할 수 없습니다.")
            }
            return@LaunchedEffect
        }
        try {
            savedAccounts = withContext(Dispatchers.IO) { credentialStore.getAccounts() }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("저장된 계정을 읽지 못했습니다. 기존 데이터는 보존됩니다.")
        }
        try {
            val savedKey = withContext(Dispatchers.IO) { credentialStore.getTwoCaptchaKey() }
            if (savedKey.isNotBlank()) {
                twocaptchaKey = savedKey
                isCheckingTwocaptcha = true
                try {
                    isTwocaptchaValid = cleaner.set2CaptchaKey(savedKey)
                    if (isTwocaptchaValid == true) controller.useTwoCaptchaKey(savedKey)
                } finally {
                    isCheckingTwocaptcha = false
                }
            }
        } catch (_: Exception) {
            snackbarHostState.showSnackbar("저장된 2Captcha 키를 읽지 못했습니다. 기존 데이터는 보존됩니다.")
        }
    }

    val isDeleting by controller.isDeleting.collectAsState()
    val isCompleted by controller.isCompleted.collectAsState()
    val progress by controller.progress.collectAsState()
    val currentGallery by controller.currentGallery.collectAsState()
    val estimatedTimeLeft by controller.currentGalleryEstimatedTimeLeft.collectAsState()
    val currentDeletionSpeedPerHour by controller.currentDeletionSpeedPerHour.collectAsState()
    val nextCaptchaEstimatedTimeLeft by controller.nextCaptchaEstimatedTimeLeft.collectAsState()
    val isTwoCaptchaConfigured by controller.isTwoCaptchaConfigured.collectAsState()
    val serviceTaskLoginId by controller.currentTaskLoginId.collectAsState()
    val serviceDeleteType by controller.currentDeleteType.collectAsState()
    val itemCounts by controller.itemCounts.collectAsState()
    val deletedCount by controller.deletedCount.collectAsState()
    val totalCount by controller.totalCount.collectAsState()
    val controllerDeleteLog by controller.deleteLog.collectAsState()
    val serviceErrorMessage by controller.errorMessage.collectAsState()
    val showCaptchaDialog by controller.showCaptchaDialog.collectAsState()

    fun requestUserDataRefresh() {
        userDataRefreshRequest++
    }

    fun gallListOf(type: String) = if (type == "posting") postingGallList else commentGallList

    // 종류별 갤러리·날짜 조건·최근부터 삭제. 안 건드린 다른 종류는 갤러리 전체 + 지금 값 복사로 시작한다.
    fun settingsOf(type: String): TypeSettings =
        if (type == deleteType) TypeSettings(selectedGallList, dateFilterEnabled, deleteNewestFirst, minPostAgeDaysToDelete)
        else otherTypeSettings?.let { if (otherSelectAll) it.copy(selection = gallListOf(type).keys.toList()) else it }
            ?: TypeSettings(gallListOf(type).keys.toList(), dateFilterEnabled, deleteNewestFirst, minPostAgeDaysToDelete)

    // 화면을 그릴 때마다 다른 종류로 부른다. 보관값에 목록에서 사라진 갤러리가 남아 있어도(이어 하기 복원, 새로고침 중간) 죽지 않게 있는 것만 담는다.
    // 시작할 때는 hasCompleteGalleryMap으로 먼저 걸러 둘이 같다.
    fun phaseOf(type: String): DeletePhase = settingsOf(type).let {
        val gallList = gallListOf(type)
        DeletePhase(
            deleteType = type,
            selectedGalleries = it.selection,
            galleryMap = DeleteTaskStartValidator.selectedGalleryMap(
                DeleteTaskStartValidator.retainAvailableSelection(it.selection, gallList),
                gallList
            ),
            dateFilterEnabled = it.dateFilterEnabled,
            deleteNewestFirst = it.deleteNewestFirst,
            minPostAgeDaysToDelete = if (it.dateFilterEnabled) it.minPostAgeDaysToDelete.toIntOrNull() ?: 5 else -1
        )
    }

    // 편집 중인 종류를 바꾸면 화면 값과 보관값을 맞바꾼다.
    fun switchEditedType(type: String) {
        if (deleteType == type) return
        val next = settingsOf(type)
        otherTypeSettings = settingsOf(deleteType)
        otherSelectAll = false
        deleteType = type
        selectedGallList = next.selection
        dateFilterEnabled = next.dateFilterEnabled
        deleteNewestFirst = next.deleteNewestFirst
        minPostAgeDaysToDelete = next.minPostAgeDaysToDelete
    }

    // 댓글 자동 정리. 안드로이드 서비스와 같은 감시 루프를 앱이 켜져 있는 동안 돌린다.
    var commentMonitorJob by remember { mutableStateOf<Job?>(null) }

    fun stopCommentMonitor() {
        commentMonitorJob?.cancel()
        commentMonitorJob = null
    }

    fun startCommentMonitor(keywords: List<String>, intervalSeconds: Int, monitorMinutes: Int) {
        val config = CommentMonitorConfig.of(cleaner, keywords, intervalSeconds, monitorMinutes)
        if (!config.hasWork) return stopCommentMonitor()
        val previous = commentMonitorJob
        previous?.cancel()
        commentMonitorJob = coroutine.launch {
            try {
                previous?.join()
                runCommentMonitor(config) { System.nanoTime() / 1_000_000 }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 예상 못 한 오류로 감시가 멈추면 스위치도 꺼진 상태로 보이게 한다.
            } finally {
                if (commentMonitorJob === currentCoroutineContext()[Job]) commentMonitorJob = null
            }
        }
    }

    LaunchedEffect(loginInfo != null) {
        if (loginInfo == null) {
            stopCommentMonitor()
        } else if (commentMonitorJob == null) {
            val saved = withContext(Dispatchers.IO) { loadSavedCommentMonitor(desktopCommentMonitorStorage()) }
            startCommentMonitor(saved.keywords, saved.intervalSeconds, saved.monitorMinutes)
        }
    }

    fun handleExpiredGallListSession() {
        cleaner.clearSession()
        controller.clearLogs()
        loginInfo = null
        sessionExpired = true
        postingGallList = emptyMap()
        commentGallList = emptyMap()
        selectedGallList = emptyList()
        // 다시 로그인하면 다른 종류 갤러리는 전체로 두되, 날짜 조건·최근부터는 지킨다(몰래 더 지우지 않게).
        otherSelectAll = true
        interruptedTasks = emptyList()
        deleteLog = emptyList()
        showDeleteProgressDialog = false
        isTwocaptchaValid = null
        isLoggingIn = false
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
            otherSelectAll = true
        } else {
            try {
                loginInfo = cleaner.getUserInfo()

                val posting = cleaner.getGallList("posting")
                when (posting) {
                    GallListResult.SessionExpired -> {
                        handleExpiredGallListSession()
                        return@LaunchedEffect
                    }
                    is GallListResult.Success -> postingGallList = posting.galleries
                    GallListResult.Blocked -> Unit
                }

                val comment = cleaner.getGallList("comment")
                when (comment) {
                    GallListResult.SessionExpired -> {
                        handleExpiredGallListSession()
                        return@LaunchedEffect
                    }
                    is GallListResult.Success -> commentGallList = comment.galleries
                    GallListResult.Blocked -> Unit
                }

                selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(
                    selectedGallList,
                    gallListOf(deleteType)
                )
                val otherType = if (deleteType == "posting") "comment" else "posting"
                if (!otherSelectAll) otherTypeSettings = otherTypeSettings?.let {
                    it.copy(selection = DeleteTaskStartValidator.retainAvailableSelection(it.selection, gallListOf(otherType)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        interruptedTasks = taskStore.getForLogin(uiLoginId.ifBlank { serviceTaskLoginId })
    }

    LaunchedEffect(isDeleting) {
        if (deleteTaskWasRunning && !isDeleting) {
            // The controller remains the source of truth while a task is running. Keep a
            // snapshot when it stops so the progress dialog does not briefly fall back to
            // the empty UI-local log.
            deleteLog = controllerDeleteLog
            requestUserDataRefresh()
        }
        deleteTaskWasRunning = isDeleting
    }

    LaunchedEffect(guestbookIsSending) {
        if (guestbookTaskWasRunning && !guestbookIsSending) {
            requestUserDataRefresh()
        }
        guestbookTaskWasRunning = guestbookIsSending
    }

    LaunchedEffect(Unit) {
        isCheckingVersion = true
        try {
            latestVersion = ReleaseVersionChecker.fetchLatestVersion()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            latestVersion = null
        } finally {
            isCheckingVersion = false
        }
    }
    LaunchedEffect(serviceErrorMessage) {
        serviceErrorMessage?.let {
            errorMessage = it
            showDeleteProgressDialog = false
            showErrorDialog = true
            controller.clearError()
        }
    }
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            deleteLog = controllerDeleteLog
            showDeleteProgressDialog = true
        }
    }

    // A stop changes isDeleting before the UI-local snapshot is applied. Prefer the
    // controller's retained log whenever it is available so the dialog never clears.
    val displayedDeleteLog = controllerDeleteLog.ifEmpty { deleteLog }
    val otherType = if (deleteType == "posting") "comment" else "posting"
    val otherSettings = settingsOf(otherType)
    val state = DccleanerScreenState(
        uiColors = uiColors,
        isDarkTheme = isDarkTheme,
        id = id,
        pw = pw,
        loginInfo = loginInfo,
        profileImage = rememberDesktopUserProfileImage(loginInfo?.userId.orEmpty()),
        saveLogin = saveLogin,
        credentialStorageSupported = credentialStore != null,
        savedAccounts = savedAccounts,
        isLoggingIn = isLoggingIn,
        sessionExpired = sessionExpired,
        deleteUiActive = isDeleting,
        runningLoginId = serviceTaskLoginId,
        displayedGallery = currentGallery,
        displayedProgress = progress,
        displayedDeletedCount = deletedCount,
        itemCounts = itemCounts,
        displayedTotalCount = totalCount,
        displayedDeleteLog = displayedDeleteLog,
        interruptedTasks = interruptedTasks,
        resumeEnabled = !isDeleting && restoringTaskId == null,
        restoringTaskId = restoringTaskId,
        focusedTaskId = null,
        restoringMessage = restoringMessage,
        selectedTab = selectedTab,
        developerMode = developerMode,
        commentCleanerRunning = commentMonitorJob != null,
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
        currentVersion = DesktopBuildConfig.VERSION_NAME,
        isCheckingVersion = isCheckingVersion,
        showErrorDialog = showErrorDialog,
        errorMessage = errorMessage,
        deleteTaskToRemove = deleteTaskToRemove,
        isCheckingDeletionSession = isCheckingDeletionSession,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        activeFilters = activeFilters(deleteType, recommendFilterEnabled, commentFilterEnabled, viewFilterEnabled, postContentFilterEnabled, myPostFilterEnabled, dcconOnlyFilterEnabled, commentContentFilterEnabled, dateFilterEnabled, deleteNewestFirst, deleteQuestionPosts, twocaptchaKey, minRecommendToKeep, minCommentToKeep, minViewToKeep, minPostAgeDaysToDelete),
        showDeleteProgressDialog = showDeleteProgressDialog,
        progressDeleteType = serviceDeleteType.ifBlank { deleteType },
        deleteBoth = deleteBoth,
        noDeleteTarget = noDeleteTarget,
        commentFirst = commentFirst,
        otherSelectedGallList = otherSettings.selection,
        otherPhase = phaseOf(otherType),
        otherActiveFilters = activeFilters(otherType, recommendFilterEnabled, commentFilterEnabled, viewFilterEnabled, postContentFilterEnabled, myPostFilterEnabled, dcconOnlyFilterEnabled, commentContentFilterEnabled, otherSettings.dateFilterEnabled, otherSettings.deleteNewestFirst, deleteQuestionPosts, twocaptchaKey, minRecommendToKeep, minCommentToKeep, minViewToKeep, otherSettings.minPostAgeDaysToDelete),
        runningPhases = runningPhases,
        isCompleted = isCompleted,
        isDeleting = isDeleting,
        estimatedTimeLeft = estimatedTimeLeft,
        currentDeletionSpeedPerHour = currentDeletionSpeedPerHour,
        nextCaptchaEstimatedTimeLeft = nextCaptchaEstimatedTimeLeft,
        isTwoCaptchaConfigured = isTwoCaptchaConfigured,
        showStopDeleteDialog = showStopDeleteDialog,
        showCaptchaDialog = showCaptchaDialog,
        accountToDelete = accountToDelete,
        showDeleteAccountDialog = showDeleteAccountDialog,
        guestbookUserListText = guestbookUserListText,
        guestbookMessageText = guestbookMessageText,
        guestbookShowConfirmDialog = guestbookShowConfirmDialog,
        guestbookShowResultDialog = guestbookShowResultDialog,
        showGuestbookProgressDialog = showGuestbookProgressDialog,
        guestbookIsSending = guestbookIsSending,
        guestbookProgressDone = guestbookProgressDone,
        guestbookProgressTotal = guestbookProgressTotal,
        guestbookSuccessCount = guestbookSuccessCount,
        guestbookFailCount = guestbookFailCount,
        guestbookCacheEnabled = false,
        guestbookCachedUserCount = 0
    )

    DccleanerTheme(darkTheme = isDarkTheme) {
        DccleanerScreenContent(
            state = state,
            actions = DccleanerScreenActions(
            onDarkThemeChange = { enabled ->
                isDarkTheme = enabled
                themeStore.saveDarkTheme(enabled)
            },
            onIdChange = { id = it },
            onPwChange = { pw = it },
            onSaveLoginChange = { saveLogin = it },
            onSavedAccountClick = {
                id = it.id
                pw = it.password
            },
            onDeleteSavedAccountClick = {
                accountToDelete = it
                showDeleteAccountDialog = true
            },
            onLoginClick = {
                coroutine.launch {
                    id = id.trim()
                    isLoggingIn = true
                    val success = cleaner.login(id, pw)
                    if (!success) {
                        errorMessage = "로그인에 실패했습니다.\n아이디와 비밀번호를 확인해 주세요.\n디시인사이드 2단계 인증이 켜져 있으면 끄고 다시 시도해 주세요."
                        showErrorDialog = true
                        isLoggingIn = false
                        return@launch
                    }
                    controller.setCleaner(cleaner)
                    loginInfo = cleaner.getUserInfo()
                    if (saveLogin && credentialStore != null && id.isNotBlank() && pw.isNotBlank()) {
                        val account = SavedAccount(id, pw, loginInfo?.nickname.orEmpty())
                        try {
                            savedAccounts = withContext(Dispatchers.IO) {
                                credentialStore.addAccount(account)
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("로그인 정보를 저장하지 못했습니다.")
                        }
                    }
                    sessionExpired = false
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
                    otherSelectAll = true
                    interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
                    isLoggingIn = false
                }
            },
            onLogoutClick = {
                cleaner.clearSession()
                // 로그아웃하면 만들던 갤창랭킹·찾던 글과 결과도 지운다.
                clearGalleryRanking()
                clearUserPostSearch()
                controller.clearLogs()
                loginInfo = null
                sessionExpired = false
                id = ""
                pw = ""
                postingGallList = emptyMap()
                commentGallList = emptyMap()
                selectedGallList = emptyList()
                otherTypeSettings = null
                otherSelectAll = false
                interruptedTasks = emptyList()
                deleteLog = emptyList()
                showDeleteProgressDialog = false
                isTwocaptchaValid = null
            },
            onStopRunningClick = { showStopDeleteDialog = true },
            onResumeTask = { task ->
                if (!DeleteTaskStartValidator.hasCompleteGalleryMap(task.selectedGalleries, task.galleryMap)) {
                    errorMessage = "저장된 작업의 갤러리 정보를 복원하지 못했습니다."
                    showErrorDialog = true
                } else {
                    restoringTaskId = task.id
                    restoringMessage = "저장된 설정으로 이어서 삭제를 시작합니다"
                    coroutine.launch {
                        kotlinx.coroutines.delay(400)
                        switchEditedType(task.deleteType)
                        selectedGallList = task.selectedGalleries
                        val followUp = task.followUp
                        deleteBoth = followUp != null
                        noDeleteTarget = false
                        runningPhases = if (followUp != null) listOf(task.deleteType, followUp.deleteType) else emptyList()
                        if (followUp != null) {
                            commentFirst = task.deleteType == "comment"
                            otherSelectAll = false
                            otherTypeSettings = TypeSettings(
                                selection = followUp.selectedGalleries,
                                dateFilterEnabled = followUp.dateFilterEnabled,
                                deleteNewestFirst = followUp.deleteNewestFirst,
                                minPostAgeDaysToDelete = followUp.minPostAgeDaysToDelete.takeIf { it >= 0 }?.toString() ?: "5"
                            )
                        }
                        restoringTaskId = null
                        showDeleteProgressDialog = true
                        controller.resumeDeletion(task.copy(twoCaptchaApiKey = twocaptchaKey))
                    }
                }
            },
            onDeleteTask = { deleteTaskToRemove = it },
            onDeleteAllTasks = {
                if (!isDeleting && restoringTaskId == null) {
                    val failedCount = interruptedTasks.count { !taskStore.remove(it.id) }
                    interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
                    if (failedCount > 0) {
                        errorMessage = "작업 기록 ${failedCount}개를 삭제하지 못했습니다."
                        showErrorDialog = true
                    }
                }
            },
            onTabChange = { selectedTab = it },
            onDeveloperModeToggle = {
                developerMode = !developerMode
                themeStore.saveDeveloperMode(developerMode)
            },
            onRemoteDeletionFinished = {
                requestUserDataRefresh()
                // 앱 창을 보고 있지 않을 때만 끝났다고 알린다.
                proxyCleaner.job?.let { if (!windowInfo.isWindowFocused) trayNotifier.notify("초고속 삭제", proxyJobFinishedMessage(it)) }
            },
            onChargeNotice = { message -> if (!windowInfo.isWindowFocused) trayNotifier.notify("초고속 삭제 충전", message) },
            onStopForProxyHandOff = {
                controller.stopForHandOff().also { stopped ->
                    if (stopped) {
                        // 이어하기로 복원한 선택에는 이미 다 지워 목록에서 빠진 갤러리가 남을 수 있어, 시작 창을 열기 전에 거른다.
                        selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(selectedGallList, gallListOf(deleteType))
                        val otherType = if (deleteType == "posting") "comment" else "posting"
                        if (!otherSelectAll) otherTypeSettings = otherTypeSettings?.let {
                            it.copy(selection = DeleteTaskStartValidator.retainAvailableSelection(it.selection, gallListOf(otherType)))
                        }
                        runningPhases = emptyList()
                        interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
                    }
                }
            },
            onStartCommentCleaner = ::startCommentMonitor,
            onStopCommentCleaner = ::stopCommentMonitor,
            onDeleteTypeChange = ::switchEditedType,
            onDeleteTargetsChange = { targets ->
                noDeleteTarget = targets.isEmpty()
                deleteBoth = targets.size == 2
                // 하나만 남으면 그 종류를 지울 대상(=설정 중인 종류)으로 맞춘다.
                targets.singleOrNull()?.let { if (it != deleteType) switchEditedType(it) }
                themeStore.saveDeleteTargets(targets)
            },
            onCommentFirstChange = {
                commentFirst = it
                themeStore.saveDeleteCommentFirst(it)
            },
            onTwocaptchaKeyChange = {
                twocaptchaKey = it
                if (it.isBlank()) {
                    // 도는 작업도 키를 지운 상태로 맞춘다(진행 화면 표시·다음 단계·이어 하기에 옛 키가 남지 않게).
                    cleaner.restore2CaptchaKey("")
                    controller.useTwoCaptchaKey("")
                    if (credentialStore != null) coroutine.launch {
                        try {
                            withContext(Dispatchers.IO) { credentialStore.removeTwoCaptchaKey() }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("저장된 2Captcha 키를 삭제하지 못했습니다.")
                        }
                    }
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
                    val selection = settingsOf(type).selection
                    if (selection.isEmpty()) {
                        errorMessage = if (deleteBoth) {
                            "${if (type == "posting") "게시글" else "댓글"}을 지울 갤러리를 먼저 골라 주세요."
                        } else {
                            "삭제할 갤러리를 먼저 골라 주세요."
                        }
                        showErrorDialog = true
                        return@showDeleteDialog
                    }
                    if (!DeleteTaskStartValidator.hasCompleteGalleryMap(selection, gallListOf(type))) {
                        errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                        showErrorDialog = true
                        return@showDeleteDialog
                    }
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
                themeStore.saveRecordGuestbookLog(enabled)
            },
            onCaptchaSectionPosition = {},
            onDeleteOptionsSectionPosition = {},
            onFilterOptionsSectionPosition = {},
            onValidateTwocaptchaKey = { key ->
                cleaner.set2CaptchaKey(key).also { valid ->
                    if (valid) {
                        controller.useTwoCaptchaKey(key)
                        if (credentialStore != null && twocaptchaKey == key) {
                            try {
                                withContext(Dispatchers.IO) { credentialStore.saveTwoCaptchaKey(key) }
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar("2Captcha 키를 저장하지 못했습니다.")
                            }
                        }
                    }
                }
            },
            onOpenUpdate = { DesktopExternalNavigator.openUrl("https://github.com/dccleaner3/dccleaner/releases/latest") },
            onDismissError = { showErrorDialog = false },
            onConfirmDeleteTaskRecord = {
                deleteTaskToRemove?.let {
                    if (taskStore.remove(it.id)) {
                        interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
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
                    deletionSessionCheckJob = null
                    isCheckingDeletionSession = false
                    val firstType = if (!deleteBoth) deleteType else if (commentFirst) "comment" else "posting"
                    val types = if (deleteBoth) listOf(firstType, if (firstType == "posting") "comment" else "posting") else listOf(firstType)
                    if (types.any { !DeleteTaskStartValidator.hasCompleteGalleryMap(settingsOf(it).selection, gallListOf(it)) }) {
                        errorMessage = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
                        showErrorDialog = true
                        return@startDeletion
                    }
                    val first = phaseOf(firstType)
                    val followUp = types.getOrNull(1)?.let(::phaseOf)
                    runningPhases = if (followUp != null) types else emptyList()
                    restoringTaskId = null
                    showDeleteProgressDialog = true
                    deleteLog = emptyList()
                    controller.startDeletion(
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
                }
            },
            onDismissStartDeletion = {
                deletionSessionCheckJob?.cancel()
                deletionSessionCheckJob = null
                isCheckingDeletionSession = false
                showDeleteConfirmDialog = false
            },
            onCloseDeleteProgress = { showDeleteProgressDialog = false },
            onCompleteDeleteProgress = {
                showDeleteProgressDialog = false
                runningPhases = emptyList()
                deleteLog = controllerDeleteLog.ifEmpty { deleteLog }
                interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
            },
            onStopDeleteRequest = { showStopDeleteDialog = true },
            onConfirmStopDelete = {
                showStopDeleteDialog = false
                controller.stopDeletion()
                interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
            },
            onDismissStopDelete = { showStopDeleteDialog = false },
            onOpenGallogForCaptcha = {
                val userId = serviceTaskLoginId.ifBlank { cleaner.getUserId() }
                if (userId.isNotBlank()) DesktopExternalNavigator.openUrl("https://gallog.dcinside.com/$userId")
            },
            onResolveCaptcha = { controller.resolveCaptcha() },
            onOpenAutoCaptchaGuide = {
                DesktopExternalNavigator.openUrl("https://dccleaner3.github.io/dccleaner/help#captcha-auto")
            },
            onConfirmDeleteAccount = {
                val accountId = accountToDelete?.id
                showDeleteAccountDialog = false
                accountToDelete = null
                if (accountId != null && credentialStore != null) coroutine.launch {
                    try {
                        savedAccounts = withContext(Dispatchers.IO) { credentialStore.removeAccount(accountId) }
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar("저장된 계정을 삭제하지 못했습니다.")
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
            onGuestbookIsSendingChange = { guestbookIsSending = it },
            onGuestbookProgressDoneChange = { guestbookProgressDone = it },
            onGuestbookProgressTotalChange = { guestbookProgressTotal = it },
            onGuestbookSuccessCountChange = { guestbookSuccessCount = it },
            onGuestbookFailCountChange = { guestbookFailCount = it },
            onFilterGuestbookCachedUsers = { userIds ->
                GuestbookCacheFilterResult(userIdsToSend = userIds, skippedCount = 0)
            },
            onClearGuestbookCachedUsers = { true },
            onResolveGuestbookUserList = { url -> GuestbookUserListFetcher.fetch(url) },
            onStartGuestbookSend = { ids, message ->
                showGuestbookProgressDialog = true
                coroutine.launch {
                    try {
                        guestbookIsSending = true
                        GuestbookExecutionRunner.run(
                            userIds = ids,
                            message = message,
                            send = cleaner::writeGuestbook
                        ) { progress ->
                            guestbookProgressDone = progress.done
                            guestbookProgressTotal = progress.total
                            guestbookSuccessCount = progress.successCount
                            guestbookFailCount = progress.failCount
                        }
                        showGuestbookProgressDialog = true
                    } catch (error: CancellationException) {
                        throw error
                    } finally {
                        guestbookIsSending = false
                    }
                }
            }
            ),
            modifier = modifier,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutine,
            applySystemBarsPadding = false,
            maxContentWidth = 520.dp,
            proxyCleaner = proxyCleaner
        )
    }
}

@Composable
private fun rememberDesktopUserProfileImage(userId: String): ImageBitmap? = key(userId) {
    val image by produceState<ImageBitmap?>(null) {
        if (userId.isNotBlank()) value = loadDesktopUserProfileImage(userId)
    }
    image
}

private suspend fun loadDesktopUserProfileImage(userId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val encodedId = URLEncoder.encode(userId, StandardCharsets.UTF_8)
    val url = "https://dcimg2.dcinside.co.kr/gallog_upimg.php?mode=profile&gid=$encodedId&t=${System.currentTimeMillis() / 1_000}"
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val limit = 2 * 1024 * 1024
        if (connection.contentLengthLong > limit) return@withContext null
        val bytes = connection.inputStream.use { it.readNBytes(limit + 1) }
        if (bytes.size > limit) return@withContext null
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

/** 종류마다 따로 정하는 갤러리·날짜 조건·최근부터 삭제(편집 중이 아닌 종류 보관용). */
private data class TypeSettings(
    val selection: List<String>,
    val dateFilterEnabled: Boolean,
    val deleteNewestFirst: Boolean,
    val minPostAgeDaysToDelete: String
)

private fun activeFilters(
    deleteType: String,
    recommendFilterEnabled: Boolean,
    commentFilterEnabled: Boolean,
    viewFilterEnabled: Boolean,
    postContentFilterEnabled: Boolean,
    myPostFilterEnabled: Boolean,
    dcconOnlyFilterEnabled: Boolean,
    commentContentFilterEnabled: Boolean,
    dateFilterEnabled: Boolean,
    deleteNewestFirst: Boolean,
    deleteQuestionPosts: Boolean,
    twocaptchaKey: String,
    minRecommendToKeep: String,
    minCommentToKeep: String,
    minViewToKeep: String,
    minPostAgeDaysToDelete: String
): List<String> = buildList {
    if (deleteType == "posting" && recommendFilterEnabled) add("추천 ${minRecommendToKeep.ifBlank { "1" }}개 이상 보존")
    if (deleteType == "posting" && commentFilterEnabled) add("댓글 ${minCommentToKeep.ifBlank { "1" }}개 이상 보존")
    if (deleteType == "posting" && viewFilterEnabled) add("조회 ${minViewToKeep.ifBlank { "1" }}회 이상 보존")
    if (deleteType == "posting" && postContentFilterEnabled) add("제목 정규식")
    if (deleteNewestFirst) {
        add(if (deleteType == "posting") "최근 글부터 삭제" else "최근 댓글부터 삭제")
    }
    if (deleteType == "posting" && deleteQuestionPosts) add("질문글도 삭제")
    if (deleteType == "comment" && myPostFilterEnabled) add("내 글에 단 댓글만")
    if (deleteType == "comment" && dcconOnlyFilterEnabled) add("디시콘 댓글만")
    if (deleteType == "comment" && commentContentFilterEnabled) add("댓글 정규식")
    if (dateFilterEnabled) add("작성 후 ${minPostAgeDaysToDelete.ifBlank { "5" }}일 이상")
    if (twocaptchaKey.isNotBlank()) add("2Captcha 자동 해결")
}
