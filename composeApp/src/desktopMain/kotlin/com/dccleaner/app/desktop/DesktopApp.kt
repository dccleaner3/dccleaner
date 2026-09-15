package com.dccleaner.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskStartValidator
import com.dccleaner.app.model.DaewangconProgress
import com.dccleaner.app.model.GallListResult
import com.dccleaner.app.model.SavedAccount
import com.dccleaner.app.model.UserInfo
import com.dccleaner.app.model.dccleanerUiColors
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.CleanerLogSink
import com.dccleaner.app.network.GuestbookUserListFetcher
import com.dccleaner.app.platform.DesktopExternalNavigator
import com.dccleaner.app.runtime.DccleanerTaskController
import com.dccleaner.app.runtime.GuestbookExecutionRunner
import com.dccleaner.app.runtime.previewDeletion
import com.dccleaner.app.update.ReleaseVersionChecker
import com.dccleaner.app.ui.screen.DccleanerScreenActions
import com.dccleaner.app.ui.screen.DccleanerScreenContent
import com.dccleaner.app.ui.screen.DccleanerScreenState
import com.dccleaner.app.ui.guestbook.GuestbookCacheFilterResult
import com.dccleaner.app.ui.theme.DccleanerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

@Composable
fun DesktopDccleanerApp(modifier: Modifier = Modifier) {
    val taskStore = remember { DesktopDeleteTaskStore() }
    val themeStore = remember { DesktopThemePreferenceStore() }
    val logSink = remember { DesktopFileLogSink() }
    val cleaner = remember {
        Cleaner(logSink = CleanerLogSink { tag, message -> logSink.addLog(tag, message) })
    }
    val controller = remember {
        DccleanerTaskController(
            deleteTaskStore = taskStore,
            logSink = logSink,
            notifier = DesktopTrayNotifier()
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
    var saveLogin by remember { mutableStateOf(false) }
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
    var deleteType by remember { mutableStateOf("posting") }
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
    var recordGuestbookLog by remember {
        mutableStateOf(themeStore.getRecordGuestbookLog())
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isInspectingDeletion by remember { mutableStateOf(false) }
    var deletionInspectionMessage by remember { mutableStateOf("") }
    var deletionPreview by remember { mutableStateOf<com.dccleaner.app.model.DeletionPreview?>(null) }
    var deletionInspectionJob by remember { mutableStateOf<Job?>(null) }
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

    var daewangconGalleryId by remember { mutableStateOf("") }
    var daewangconPostNo by remember { mutableStateOf("") }
    var daewangconPostSubject by remember { mutableStateOf("테스트 제목") }
    var daewangconPostContent by remember { mutableStateOf("테스트 내용") }
    var daewangconCommentContent by remember { mutableStateOf("테스트 댓글") }
    var showDaewangconDialog by remember { mutableStateOf(false) }
    var showDaewangconProgressDialog by remember { mutableStateOf(false) }
    var showStopDaewangconDialog by remember { mutableStateOf(false) }
    var daewangconServerProgress by remember { mutableStateOf<DaewangconProgress?>(null) }

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
    var daewangconTaskWasRunning by remember { mutableStateOf(false) }
    var guestbookTaskWasRunning by remember { mutableStateOf(false) }

    val isDeleting by controller.isDeleting.collectAsState()
    val isCompleted by controller.isCompleted.collectAsState()
    val progress by controller.progress.collectAsState()
    val currentGallery by controller.currentGallery.collectAsState()
    val estimatedTimeLeft by controller.currentGalleryEstimatedTimeLeft.collectAsState()
    val nextCaptchaEstimatedTimeLeft by controller.nextCaptchaEstimatedTimeLeft.collectAsState()
    val isTwoCaptchaConfigured by controller.isTwoCaptchaConfigured.collectAsState()
    val serviceTaskLoginId by controller.currentTaskLoginId.collectAsState()
    val serviceDeleteType by controller.currentDeleteType.collectAsState()
    val deletedCount by controller.deletedCount.collectAsState()
    val totalCount by controller.totalCount.collectAsState()
    val controllerDeleteLog by controller.deleteLog.collectAsState()
    val serviceErrorMessage by controller.errorMessage.collectAsState()
    val showCaptchaDialog by controller.showCaptchaDialog.collectAsState()
    val isDaewangconRunning by controller.isDaewangconRunning.collectAsState()
    val isDaewangconCompleted by controller.isDaewangconCompleted.collectAsState()
    val daewangconErrorMessage by controller.daewangconErrorMessage.collectAsState()
    val daewangconProgress by controller.daewangconProgress.collectAsState()
    val daewangconLog by controller.daewangconLog.collectAsState()
    val daewangconPostCount by controller.daewangconPostCount.collectAsState()
    val daewangconCommentCount by controller.daewangconCommentCount.collectAsState()

    fun requestUserDataRefresh() {
        userDataRefreshRequest++
    }

    fun handleExpiredGallListSession() {
        cleaner.clearSession()
        controller.clearLogs()
        loginInfo = null
        daewangconServerProgress = null
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

                val refreshedGallList = if (deleteType == "posting") {
                    postingGallList
                } else {
                    commentGallList
                }
                selectedGallList = DeleteTaskStartValidator.retainAvailableSelection(
                    selectedGallList,
                    refreshedGallList
                )
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

    LaunchedEffect(isDaewangconRunning) {
        if (daewangconTaskWasRunning && !isDaewangconRunning) {
            requestUserDataRefresh()
        }
        daewangconTaskWasRunning = isDaewangconRunning
    }

    LaunchedEffect(guestbookIsSending) {
        if (guestbookTaskWasRunning && !guestbookIsSending) {
            requestUserDataRefresh()
        }
        guestbookTaskWasRunning = guestbookIsSending
    }

    LaunchedEffect(isDaewangconRunning, isDaewangconCompleted, daewangconErrorMessage) {
        if (isDaewangconRunning || isDaewangconCompleted || daewangconErrorMessage != null) {
            showDaewangconProgressDialog = true
        }
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
    val state = DccleanerScreenState(
        uiColors = uiColors,
        isDarkTheme = isDarkTheme,
        id = id,
        pw = pw,
        loginInfo = loginInfo,
        daewangconServerProgress = daewangconServerProgress,
        saveLogin = saveLogin,
        credentialStorageSupported = false,
        savedAccounts = savedAccounts,
        isLoggingIn = isLoggingIn,
        sessionExpired = sessionExpired,
        deleteUiActive = isDeleting,
        runningLoginId = serviceTaskLoginId,
        displayedGallery = currentGallery,
        displayedProgress = progress,
        displayedDeletedCount = deletedCount,
        displayedTotalCount = totalCount,
        displayedDeleteLog = displayedDeleteLog,
        interruptedTasks = interruptedTasks,
        resumeEnabled = !isDeleting && restoringTaskId == null,
        restoringTaskId = restoringTaskId,
        focusedTaskId = null,
        restoringMessage = restoringMessage,
        selectedTab = selectedTab,
        developerMode = developerMode,
        commentCleanerRunning = false,
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
        isInspectingDeletion = isInspectingDeletion,
        deletionInspectionMessage = deletionInspectionMessage,
        deletionPreview = deletionPreview,
        showDeleteConfirmDialog = showDeleteConfirmDialog,
        activeFilters = activeFilters(deleteType, recommendFilterEnabled, commentFilterEnabled, viewFilterEnabled, postContentFilterEnabled, myPostFilterEnabled, dcconOnlyFilterEnabled, commentContentFilterEnabled, dateFilterEnabled, deleteNewestFirst, deleteQuestionPosts, twocaptchaKey, minRecommendToKeep, minCommentToKeep, minViewToKeep, minPostAgeDaysToDelete),
        showDeleteProgressDialog = showDeleteProgressDialog,
        progressDeleteType = serviceDeleteType.ifBlank { deleteType },
        isCompleted = isCompleted,
        isDeleting = isDeleting,
        estimatedTimeLeft = estimatedTimeLeft,
        nextCaptchaEstimatedTimeLeft = nextCaptchaEstimatedTimeLeft,
        isTwoCaptchaConfigured = isTwoCaptchaConfigured,
        showStopDeleteDialog = showStopDeleteDialog,
        showCaptchaDialog = showCaptchaDialog,
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
            onSaveLoginChange = { saveLogin = false },
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
                    isLoggingIn = true
                    daewangconServerProgress = null
                    val success = cleaner.login(id, pw)
                    if (!success) {
                        errorMessage = "로그인에 실패했습니다.\n아이디와 비밀번호를 확인해주세요."
                        showErrorDialog = true
                        isLoggingIn = false
                        return@launch
                    }
                    controller.setCleaner(cleaner)
                    loginInfo = cleaner.getUserInfo()
                    sessionExpired = false
                    daewangconServerProgress = cleaner.getDaewangconProgress()
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
                    selectedGallList = if (deleteType == "posting") {
                        postingGallList.keys.toList()
                    } else {
                        commentGallList.keys.toList()
                    }
                    interruptedTasks = taskStore.getForLogin(cleaner.getUserId())
                    isLoggingIn = false
                }
            },
            onLogoutClick = {
                cleaner.clearSession()
                controller.clearLogs()
                loginInfo = null
                sessionExpired = false
                daewangconServerProgress = null
                id = ""
                pw = ""
                postingGallList = emptyMap()
                commentGallList = emptyMap()
                selectedGallList = emptyList()
                interruptedTasks = emptyList()
                deleteLog = emptyList()
                showDeleteProgressDialog = false
                showDaewangconProgressDialog = false
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
                        deleteType = task.deleteType
                        selectedGallList = task.selectedGalleries
                        restoringTaskId = null
                        showDeleteProgressDialog = true
                        controller.resumeDeletion(task.copy(twoCaptchaApiKey = twocaptchaKey))
                    }
                }
            },
            onDeleteTask = { deleteTaskToRemove = it },
            onTabChange = { selectedTab = it },
            onDeveloperModeToggle = {
                developerMode = !developerMode
                themeStore.saveDeveloperMode(developerMode)
            },
            onRequestWriteCookies = cleaner::getWebViewSessionCookies,
            onStartCommentCleaner = { _, _, _ -> },
            onStopCommentCleaner = {},
            onOpenManual = { tab ->
                DesktopExternalNavigator.openUrl(
                    if (tab == 0) "https://dccleaner3.github.io/dccleaner/cleaner" else "https://dccleaner3.github.io/dccleaner/guestbook"
                )
            },
            onDeleteTypeChange = {
                if (deleteType != it) {
                    deleteType = it
                    selectedGallList = if (it == "posting") postingGallList.keys.toList() else commentGallList.keys.toList()
                }
            },
            onTwocaptchaKeyChange = {
                twocaptchaKey = it
                if (it.isBlank()) {
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
                themeStore.saveRecordGuestbookLog(enabled)
            },
            onCaptchaSectionPosition = {},
            onDeleteOptionsSectionPosition = {},
            onFilterOptionsSectionPosition = {},
            onValidateTwocaptchaKey = { key ->
                cleaner.set2CaptchaKey(key)
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
                    showDeleteProgressDialog = true
                    deleteLog = emptyList()
                    controller.startDeletion(
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
                }
            },
            onDismissStartDeletion = {
                deletionInspectionJob?.cancel()
                deletionInspectionJob = null
                isInspectingDeletion = false
                showDeleteConfirmDialog = false
            },
            onCloseDeleteProgress = { showDeleteProgressDialog = false },
            onCompleteDeleteProgress = {
                showDeleteProgressDialog = false
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
                DesktopExternalNavigator.openUrl("https://dccleaner3.github.io/dccleaner/cleaner#-2captcha-%EC%9E%90%EB%8F%99-%ED%95%B4%EA%B2%B0")
            },
            onConfirmDeleteAccount = {
                savedAccounts = emptyList()
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
                controller.setCleaner(cleaner)
                controller.startDaewangcon(
                    galleryId = daewangconGalleryId,
                    postNo = daewangconPostNo,
                    postSubject = daewangconPostSubject,
                    postContent = daewangconPostContent,
                    commentContent = daewangconCommentContent
                )
            },
            onDismissStartDaewangcon = { showDaewangconDialog = false },
            onCloseDaewangconProgress = {
                showDaewangconProgressDialog = false
                controller.acknowledgeDaewangconResult()
            },
            onStopDaewangconRequest = { showStopDaewangconDialog = true },
            onConfirmStopDaewangcon = {
                showStopDaewangconDialog = false
                controller.stopDaewangcon()
            },
            onDismissStopDaewangcon = { showStopDaewangconDialog = false },
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
            maxContentWidth = 520.dp
        )
    }
}

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
}
