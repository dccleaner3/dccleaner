package com.dccleaner.app.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskStartValidator
import com.dccleaner.app.runtime.GuestbookExecutionProgress
import com.dccleaner.app.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class ServiceManager(context: Context) {
    private val context = context.applicationContext
    private var dcCleanerService: DcCleanerService? = null
    private var isServiceBound = false
    private var isServiceBinding = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var serviceObserverJob: Job? = null
    private var isUiObservationEnabled = false
    private var pendingDeletion: PendingDeletion? = null
    private var pendingResume: Pair<Cleaner, DeleteTaskProgress>? = null
    private var pendingDaewangcon: PendingDaewangcon? = null
    private var pendingGuestbook: PendingGuestbook? = null
    private var pendingCommentCleaner: PendingCommentCleaner? = null
    val logManager = LogManager(context)

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()


    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()


    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentGallery = MutableStateFlow("")
    val currentGallery: StateFlow<String> = _currentGallery.asStateFlow()

    private val _currentGalleryEstimatedTimeLeft = MutableStateFlow(0L)
    val currentGalleryEstimatedTimeLeft: StateFlow<Long> = _currentGalleryEstimatedTimeLeft.asStateFlow()

    private val _nextCaptchaEstimatedTimeLeft = MutableStateFlow(0L)
    val nextCaptchaEstimatedTimeLeft: StateFlow<Long> = _nextCaptchaEstimatedTimeLeft.asStateFlow()

    private val _isTwoCaptchaConfigured = MutableStateFlow(false)
    val isTwoCaptchaConfigured: StateFlow<Boolean> = _isTwoCaptchaConfigured.asStateFlow()

    private val _currentTaskLoginId = MutableStateFlow("")
    val currentTaskLoginId: StateFlow<String> = _currentTaskLoginId.asStateFlow()

    private val _currentDeleteType = MutableStateFlow("")
    val currentDeleteType: StateFlow<String> = _currentDeleteType.asStateFlow()


    private val _deletedCount = MutableStateFlow(0)
    val deletedCount: StateFlow<Int> = _deletedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()


    private val _deleteLog = MutableStateFlow<List<String>>(emptyList())
    val deleteLog: StateFlow<List<String>> = _deleteLog.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _showCaptchaDialog = MutableStateFlow(false)
    val showCaptchaDialog: StateFlow<Boolean> = _showCaptchaDialog.asStateFlow()

    private val _captchaFlag = MutableStateFlow(false)
    val captchaFlag: StateFlow<Boolean> = _captchaFlag.asStateFlow()

    // 대왕콘 관련 StateFlow
    private val _isDaewangconRunning = MutableStateFlow(false)
    val isDaewangconRunning: StateFlow<Boolean> = _isDaewangconRunning.asStateFlow()

    private val _isDaewangconCompleted = MutableStateFlow(false)
    val isDaewangconCompleted: StateFlow<Boolean> = _isDaewangconCompleted.asStateFlow()

    private val _daewangconErrorMessage = MutableStateFlow<String?>(null)
    val daewangconErrorMessage: StateFlow<String?> = _daewangconErrorMessage.asStateFlow()

    private val _daewangconProgress = MutableStateFlow(0f)
    val daewangconProgress: StateFlow<Float> = _daewangconProgress.asStateFlow()

    private val _daewangconLog = MutableStateFlow<List<String>>(emptyList())
    val daewangconLog: StateFlow<List<String>> = _daewangconLog.asStateFlow()

    private val _daewangconPostCount = MutableStateFlow(0)
    val daewangconPostCount: StateFlow<Int> = _daewangconPostCount.asStateFlow()

    private val _daewangconCommentCount = MutableStateFlow(0)
    val daewangconCommentCount: StateFlow<Int> = _daewangconCommentCount.asStateFlow()

    private val _isGuestbookSending = MutableStateFlow(false)
    val isGuestbookSending: StateFlow<Boolean> = _isGuestbookSending.asStateFlow()

    private val _guestbookProgressDone = MutableStateFlow(0)
    val guestbookProgressDone: StateFlow<Int> = _guestbookProgressDone.asStateFlow()

    private val _guestbookProgressTotal = MutableStateFlow(0)
    val guestbookProgressTotal: StateFlow<Int> = _guestbookProgressTotal.asStateFlow()

    private val _guestbookSuccessCount = MutableStateFlow(0)
    val guestbookSuccessCount: StateFlow<Int> = _guestbookSuccessCount.asStateFlow()

    private val _guestbookFailCount = MutableStateFlow(0)
    val guestbookFailCount: StateFlow<Int> = _guestbookFailCount.asStateFlow()
    private val _guestbookProgress = MutableStateFlow(emptyGuestbookProgress())
    val guestbookProgress: StateFlow<GuestbookExecutionProgress> = _guestbookProgress.asStateFlow()
    private val _isCommentCleanerRunning = MutableStateFlow(false)
    val isCommentCleanerRunning: StateFlow<Boolean> = _isCommentCleanerRunning.asStateFlow()


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DcCleanerService.LocalBinder
            val connectedService = binder.getService()
            dcCleanerService = connectedService
            isServiceBinding = false
            isServiceBound = true
            if (isUiObservationEnabled) {
                syncServiceState(connectedService)
                startObservingServiceState(connectedService)
            }
            _isServiceConnected.value = true


            executePendingDeletion()
            executePendingDaewangcon()
            executePendingGuestbook()
            executePendingCommentCleaner()
            pendingResume?.let { (cleaner, task) ->
                resumeDeletionInternal(cleaner, task)
                pendingResume = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val wasDaewangconActive = _isDaewangconRunning.value || pendingDaewangcon != null
            stopObservingServiceState()
            dcCleanerService = null
            isServiceBinding = false
            isServiceBound = false
            _isServiceConnected.value = false
            _isDeleting.value = false
            _isDaewangconRunning.value = false
            _isDaewangconCompleted.value = false
            pendingDaewangcon = null
            pendingGuestbook = null
            _isGuestbookSending.value = false
            pendingCommentCleaner = null
            _isCommentCleanerRunning.value = false
            if (wasDaewangconActive) {
                _daewangconErrorMessage.value = "서비스 연결이 끊겨 대왕콘 작업이 중단되었습니다."
            }
        }
    }

    private fun syncServiceState(service: DcCleanerService) {
        _isDeleting.value = service.isDeleting.value
        _progress.value = service.progress.value
        _currentGallery.value = service.currentGallery.value
        _currentGalleryEstimatedTimeLeft.value = service.currentGalleryEstimatedTimeLeft.value
        _nextCaptchaEstimatedTimeLeft.value = service.nextCaptchaEstimatedTimeLeft.value
        _isTwoCaptchaConfigured.value = service.isTwoCaptchaConfigured.value
        _currentTaskLoginId.value = service.currentTaskLoginId.value
        _currentDeleteType.value = service.currentDeleteType.value
        _deletedCount.value = service.deletedCount.value
        _totalCount.value = service.totalCount.value
        _deleteLog.value = service.deleteLog.value
        _isCompleted.value = service.isCompleted.value
        _errorMessage.value = service.errorMessage.value
        _showCaptchaDialog.value = service.showCaptchaDialog.value
        _captchaFlag.value = service.captchaFlag.value
        _isDaewangconRunning.value = service.isDaewangconRunning.value
        _isDaewangconCompleted.value = service.isDaewangconCompleted.value
        _daewangconErrorMessage.value = service.daewangconErrorMessage.value
        _daewangconProgress.value = service.daewangconProgress.value
        _daewangconLog.value = service.daewangconLog.value
        _daewangconPostCount.value = service.daewangconPostCount.value
        _daewangconCommentCount.value = service.daewangconCommentCount.value
        updateGuestbookProgress(service.guestbookProgress.value)
        _isGuestbookSending.value = service.isGuestbookSending.value
        _isCommentCleanerRunning.value = service.isCommentCleanerRunning.value
    }

    private fun startObservingServiceState(service: DcCleanerService) {
        serviceObserverJob = scope.replaceConnectionObserverJob(serviceObserverJob) {
            launch { service.isDeleting.collect { _isDeleting.value = it } }
            launch { service.progress.collect { _progress.value = it } }
            launch { service.currentGallery.collect { _currentGallery.value = it } }
            launch { service.currentGalleryEstimatedTimeLeft.collect { _currentGalleryEstimatedTimeLeft.value = it } }
            launch { service.nextCaptchaEstimatedTimeLeft.collect { _nextCaptchaEstimatedTimeLeft.value = it } }
            launch { service.isTwoCaptchaConfigured.collect { _isTwoCaptchaConfigured.value = it } }
            launch { service.currentTaskLoginId.collect { _currentTaskLoginId.value = it } }
            launch { service.currentDeleteType.collect { _currentDeleteType.value = it } }
            launch { service.deletedCount.collect { _deletedCount.value = it } }
            launch { service.totalCount.collect { _totalCount.value = it } }
            launch { service.deleteLog.collect { _deleteLog.value = it } }
            launch { service.isCompleted.collect { _isCompleted.value = it } }
            launch { service.errorMessage.collect { _errorMessage.value = it } }
            launch { service.showCaptchaDialog.collect { _showCaptchaDialog.value = it } }
            launch { service.captchaFlag.collect { _captchaFlag.value = it } }

            // 대왕콘 관련 StateFlow 연결
            launch { service.isDaewangconRunning.collect { _isDaewangconRunning.value = it } }
            launch { service.isDaewangconCompleted.collect { _isDaewangconCompleted.value = it } }
            launch { service.daewangconErrorMessage.collect { _daewangconErrorMessage.value = it } }
            launch { service.daewangconProgress.collect { _daewangconProgress.value = it } }
            launch { service.daewangconLog.collect { _daewangconLog.value = it } }
            launch { service.daewangconPostCount.collect { _daewangconPostCount.value = it } }
            launch {
                service.daewangconCommentCount.collect {
                    _daewangconCommentCount.value = it
                }
            }
            launch { service.isGuestbookSending.collect { _isGuestbookSending.value = it } }
            launch { service.isCommentCleanerRunning.collect { _isCommentCleanerRunning.value = it } }
            launch { service.guestbookProgress.collect(::updateGuestbookProgress) }
        }
    }

    private fun updateGuestbookProgress(progress: GuestbookExecutionProgress) {
        _guestbookProgress.value = progress
        _guestbookProgressDone.value = progress.done
        _guestbookProgressTotal.value = progress.total
        _guestbookSuccessCount.value = progress.successCount
        _guestbookFailCount.value = progress.failCount
    }

    private fun stopObservingServiceState() {
        serviceObserverJob?.cancel()
        serviceObserverJob = null
    }

    fun setUiObservationEnabled(enabled: Boolean) {
        if (isUiObservationEnabled == enabled) return
        isUiObservationEnabled = enabled
        if (!enabled) {
            stopObservingServiceState()
            return
        }

        dcCleanerService?.let { service ->
            syncServiceState(service)
            startObservingServiceState(service)
        }
    }

    private fun executePendingDeletion() {
        pendingDeletion?.let { pending ->
            startDeletionInternal(
                pending.cleaner,
                pending.selectedGalleries,
                pending.deleteType,
                pending.galleryMap,
                pending.twoCaptchaApiKey,
                pending.recommendFilterEnabled,
                pending.commentFilterEnabled,
                pending.viewFilterEnabled,
                pending.postContentFilterEnabled,
                pending.commentContentFilterEnabled,
                pending.dateFilterEnabled,
                pending.deleteNewestFirst,
                pending.minRecommendToKeep,
                pending.minCommentToKeep,
                pending.minViewToKeep,
                pending.myPostFilterEnabled,
                pending.dcconOnlyFilterEnabled,
                pending.postContentRegex,
                pending.commentRegexFilter,
                pending.minPostAgeDaysToDelete,
                pending.recordGuestbookLog,
                pending.deleteQuestionPosts
            )
            pendingDeletion = null
        }
    }


    fun bindService(): Boolean {
        if (isServiceBound || isServiceBinding) return true
        val intent = Intent(context, DcCleanerService::class.java)
        return context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE).also {
            isServiceBinding = it
        }
    }

    fun unbindService() {
        if (isServiceBound || isServiceBinding) {
            runCatching { context.unbindService(serviceConnection) }
        }
        stopObservingServiceState()
        isUiObservationEnabled = false
        isServiceBound = false
        isServiceBinding = false
        pendingDaewangcon = null
        pendingGuestbook = null
        pendingCommentCleaner = null
        _isServiceConnected.value = false
        scope.cancel()
    }

    fun startDeletion(
        cleaner: Cleaner,
        selectedGalleries: List<String>,
        deleteType: String,
        galleryMap: Map<String, String>,
        twoCaptchaApiKey: String = "",
        recommendFilterEnabled: Boolean = false,
        commentFilterEnabled: Boolean = false,
        viewFilterEnabled: Boolean = false,
        postContentFilterEnabled: Boolean = false,
        commentContentFilterEnabled: Boolean = false,
        dateFilterEnabled: Boolean = false,
        deleteNewestFirst: Boolean = false,
        minRecommendToKeep: Int = -1,
        minCommentToKeep: Int = -1,
        minViewToKeep: Int = -1,
        myPostFilterEnabled: Boolean = false,
        dcconOnlyFilterEnabled: Boolean = false,
        postContentRegex: String = "",
        commentRegexFilter: String = "",
        minPostAgeDaysToDelete: Int = -1,
        recordGuestbookLog: Boolean = true,
        deleteQuestionPosts: Boolean = false
    ): Boolean {
        if (!DeleteTaskStartValidator.hasCompleteGalleryMap(selectedGalleries, galleryMap)) {
            _errorMessage.value = "갤러리 목록을 불러온 뒤 다시 시도해 주세요."
            return false
        }
        _currentTaskLoginId.value = cleaner.getUserId()
        _currentDeleteType.value = deleteType
        startForegroundService()

        if (isServiceBound) {
            startDeletionInternal(
                cleaner,
                selectedGalleries,
                deleteType,
                galleryMap,
                twoCaptchaApiKey,
                recommendFilterEnabled,
                commentFilterEnabled,
                viewFilterEnabled,
                postContentFilterEnabled,
                commentContentFilterEnabled,
                dateFilterEnabled,
                deleteNewestFirst,
                minRecommendToKeep,
                minCommentToKeep,
                minViewToKeep,
                myPostFilterEnabled,
                dcconOnlyFilterEnabled,
                postContentRegex,
                commentRegexFilter,
                minPostAgeDaysToDelete,
                recordGuestbookLog,
                deleteQuestionPosts
            )
        } else {
            pendingDeletion = PendingDeletion(
                cleaner,
                selectedGalleries,
                deleteType,
                galleryMap,
                twoCaptchaApiKey,
                recommendFilterEnabled,
                commentFilterEnabled,
                viewFilterEnabled,
                postContentFilterEnabled,
                commentContentFilterEnabled,
                dateFilterEnabled,
                deleteNewestFirst,
                minRecommendToKeep,
                minCommentToKeep,
                minViewToKeep,
                myPostFilterEnabled,
                dcconOnlyFilterEnabled,
                postContentRegex,
                commentRegexFilter,
                minPostAgeDaysToDelete,
                recordGuestbookLog,
                deleteQuestionPosts
            )
        }
        return true
    }

    fun stopDeletion() {
        val stopIntent = Intent(context, DcCleanerService::class.java).apply {
            action = DcCleanerService.ACTION_STOP_DELETE
        }
        context.startService(stopIntent)
    }

    fun resumeDeletion(cleaner: Cleaner, task: DeleteTaskProgress): Boolean {
        if (!DeleteTaskStartValidator.hasCompleteGalleryMap(
                task.selectedGalleries,
                task.galleryMap
            )
        ) {
            _errorMessage.value = "저장된 작업의 갤러리 정보를 복원하지 못했습니다."
            return false
        }
        _currentTaskLoginId.value = task.loginId
        _currentDeleteType.value = task.deleteType
        startForegroundService()
        if (isServiceBound) {
            resumeDeletionInternal(cleaner, task)
        } else {
            pendingResume = cleaner to task
        }
        return true
    }

    private fun resumeDeletionInternal(cleaner: Cleaner, task: DeleteTaskProgress) {
        dcCleanerService?.let { service ->
            cleaner.restore2CaptchaKey(task.twoCaptchaApiKey)
            updateDeletionState(cleaner.has2CaptchaKey())
            service.setCleaner(cleaner)
            service.resumeDeletion(task)
        }
    }

    fun clearError() {
        _errorMessage.value = null
        dcCleanerService?.clearError()
    }

    fun clearLogs() {
        dcCleanerService?.clearLogs()
    }

    fun dismissDeletionNotification() {
        dcCleanerService?.stopDeletion()
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(DcCleanerService.NOTIFICATION_ID)
        resetProgress()
    }

    fun resolveCaptcha() {
        dcCleanerService?.resolveCaptcha()
    }

    fun getCurrentTaskLoginId(): String = _currentTaskLoginId.value

    fun startDaewangcon(
        cleaner: Cleaner,
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        _isDaewangconRunning.value = true
        _isDaewangconCompleted.value = false
        _daewangconErrorMessage.value = null
        pendingDaewangcon = PendingDaewangcon(
            cleaner, galleryId, postNo, postSubject, postContent, commentContent
        )
        if (dcCleanerService != null) {
            executePendingDaewangcon()
        } else if (!bindService()) {
            pendingDaewangcon = null
            _isDaewangconRunning.value = false
            _daewangconErrorMessage.value = "대왕콘 작업 서비스에 연결하지 못했습니다."
        }
    }

    fun stopDaewangcon() {
        pendingDaewangcon = null
        _isDaewangconRunning.value = false
        val service = dcCleanerService
        if (service != null) {
            service.clearPreparedDaewangcon()
            service.stopDaewangcon()
        } else {
            val stopIntent = Intent(context, DcCleanerService::class.java).apply {
                action = DcCleanerService.ACTION_STOP_DAEWANGCON
            }
            context.startService(stopIntent)
        }
    }

    fun dismissDaewangconNotification() {
        pendingDaewangcon = null
        _isDaewangconRunning.value = false
        _isDaewangconCompleted.value = false
        _daewangconErrorMessage.value = null
        _daewangconProgress.value = 0f
        val service = dcCleanerService
        if (service != null) {
            service.clearPreparedDaewangcon()
            service.dismissDaewangconNotification()
        } else {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(DcCleanerService.NOTIFICATION_ID)
        }
    }

    fun startGuestbook(cleaner: Cleaner, userIds: List<String>, message: String): Boolean {
        if (userIds.isEmpty()) return false
        if (_isGuestbookSending.value) return false
        _isGuestbookSending.value = true
        updateGuestbookProgress(
            GuestbookExecutionProgress(
                done = 0,
                total = userIds.size,
                successCount = 0,
                failCount = 0
            )
        )
        pendingGuestbook = PendingGuestbook(cleaner, userIds, message)
        if (dcCleanerService != null) {
            executePendingGuestbook()
        } else if (!bindService()) {
            pendingGuestbook = null
            _isGuestbookSending.value = false
            return false
        }
        return true
    }

    fun startCommentCleaner(
        cleaner: Cleaner,
        keywords: List<String>,
        intervalSeconds: Int,
        monitorMinutes: Int
    ): Boolean {
        if (keywords.isEmpty() || _isCommentCleanerRunning.value) return false
        _isCommentCleanerRunning.value = true
        pendingCommentCleaner = PendingCommentCleaner(
            cleaner,
            keywords,
            intervalSeconds.coerceAtLeast(5),
            monitorMinutes.coerceAtLeast(1)
        )
        if (dcCleanerService != null) {
            executePendingCommentCleaner()
        } else if (!bindService()) {
            pendingCommentCleaner = null
            _isCommentCleanerRunning.value = false
            return false
        }
        return true
    }

    fun stopCommentCleaner() {
        pendingCommentCleaner = null
        _isCommentCleanerRunning.value = false
        dcCleanerService?.stopCommentCleaner() ?: context.startService(
            Intent(context, DcCleanerService::class.java).apply {
                action = DcCleanerService.ACTION_STOP_COMMENT_CLEANER
            }
        )
    }


    private fun startForegroundService(
        action: String = DcCleanerService.ACTION_START_DELETE
    ) {
        val startIntent = Intent(context, DcCleanerService::class.java).apply {
            this.action = action
        }
        context.startForegroundService(startIntent)
    }

    private fun executePendingDaewangcon() {
        val pending = pendingDaewangcon ?: return
        val service = dcCleanerService ?: return
        try {
            service.prepareDaewangcon(
                pending.cleaner,
                pending.galleryId,
                pending.postNo,
                pending.postSubject,
                pending.postContent,
                pending.commentContent
            )
            startForegroundService(DcCleanerService.ACTION_START_DAEWANGCON)
            pendingDaewangcon = null
        } catch (e: RuntimeException) {
            service.clearPreparedDaewangcon()
            pendingDaewangcon = null
            _isDaewangconRunning.value = false
            _daewangconErrorMessage.value =
                "대왕콘 작업 서비스를 시작하지 못했습니다: ${e.message ?: "알 수 없는 오류"}"
        }
    }

    private fun executePendingGuestbook() {
        val pending = pendingGuestbook ?: return
        val service = dcCleanerService ?: return
        try {
            service.prepareGuestbook(pending.cleaner, pending.userIds, pending.message)
            startForegroundService(DcCleanerService.ACTION_START_GUESTBOOK)
            pendingGuestbook = null
        } catch (e: RuntimeException) {
            pendingGuestbook = null
            _isGuestbookSending.value = false
        }
    }

    private fun executePendingCommentCleaner() {
        val pending = pendingCommentCleaner ?: return
        val service = dcCleanerService ?: return
        try {
            service.prepareCommentCleaner(
                pending.cleaner,
                pending.keywords,
                pending.intervalSeconds,
                pending.monitorMinutes
            )
            startForegroundService(DcCleanerService.ACTION_START_COMMENT_CLEANER)
            pendingCommentCleaner = null
        } catch (_: RuntimeException) {
            pendingCommentCleaner = null
            _isCommentCleanerRunning.value = false
        }
    }

    private fun startDeletionInternal(
        cleaner: Cleaner,
        selectedGalleries: List<String>,
        deleteType: String,
        galleryMap: Map<String, String>,
        twoCaptchaApiKey: String = "",
        recommendFilterEnabled: Boolean = false,
        commentFilterEnabled: Boolean = false,
        viewFilterEnabled: Boolean = false,
        postContentFilterEnabled: Boolean = false,
        commentContentFilterEnabled: Boolean = false,
        dateFilterEnabled: Boolean = false,
        deleteNewestFirst: Boolean = false,
        minRecommendToKeep: Int = -1,
        minCommentToKeep: Int = -1,
        minViewToKeep: Int = -1,
        myPostFilterEnabled: Boolean = false,
        dcconOnlyFilterEnabled: Boolean = false,
        postContentRegex: String = "",
        commentRegexFilter: String = "",
        minPostAgeDaysToDelete: Int = -1,
        recordGuestbookLog: Boolean = true,
        deleteQuestionPosts: Boolean = false
    ) {
        dcCleanerService?.let { service ->
            cleaner.restore2CaptchaKey(twoCaptchaApiKey)
            updateDeletionState(cleaner.has2CaptchaKey())
            service.setCleaner(cleaner)
            service.startDeletion(
                selectedGalleries,
                deleteType,
                galleryMap,
                twoCaptchaApiKey,
                recommendFilterEnabled,
                commentFilterEnabled,
                viewFilterEnabled,
                postContentFilterEnabled,
                commentContentFilterEnabled,
                dateFilterEnabled,
                deleteNewestFirst,
                minRecommendToKeep,
                minCommentToKeep,
                minViewToKeep,
                myPostFilterEnabled,
                dcconOnlyFilterEnabled,
                postContentRegex,
                commentRegexFilter,
                minPostAgeDaysToDelete,
                recordGuestbookLog,
                deleteQuestionPosts
            )
        }
    }

    private fun updateDeletionState(isTwoCaptchaConfigured: Boolean) {
        _isDeleting.value = true
        _isCompleted.value = false
        _errorMessage.value = null
        resetProgress(isTwoCaptchaConfigured)
    }

    private fun resetProgress(isTwoCaptchaConfigured: Boolean = false) {
        _progress.value = 0f
        _currentGallery.value = ""
        _currentGalleryEstimatedTimeLeft.value = 0L
        _nextCaptchaEstimatedTimeLeft.value = 0L
        _isTwoCaptchaConfigured.value = isTwoCaptchaConfigured
        _deletedCount.value = 0
        _totalCount.value = 0
        _deleteLog.value = emptyList()
        _showCaptchaDialog.value = false
        _captchaFlag.value = false
    }
}

internal fun CoroutineScope.replaceConnectionObserverJob(
    previousJob: Job?,
    observe: suspend CoroutineScope.() -> Unit
): Job {
    previousJob?.cancel()
    return launch(block = observe)
}


private data class PendingDeletion(
    val cleaner: Cleaner,
    val selectedGalleries: List<String>,
    val deleteType: String,
    val galleryMap: Map<String, String>,
    val twoCaptchaApiKey: String = "",
    val recommendFilterEnabled: Boolean = false,
    val commentFilterEnabled: Boolean = false,
    val viewFilterEnabled: Boolean = false,
    val postContentFilterEnabled: Boolean = false,
    val commentContentFilterEnabled: Boolean = false,
    val dateFilterEnabled: Boolean = false,
    val deleteNewestFirst: Boolean = false,
    val minRecommendToKeep: Int = -1,
    val minCommentToKeep: Int = -1,
    val minViewToKeep: Int = -1,
    val myPostFilterEnabled: Boolean = false,
    val dcconOnlyFilterEnabled: Boolean = false,
    val postContentRegex: String = "",
    val commentRegexFilter: String = "",
    val minPostAgeDaysToDelete: Int = -1,
    val recordGuestbookLog: Boolean = true,
    val deleteQuestionPosts: Boolean = false
)

private data class PendingDaewangcon(
    val cleaner: Cleaner,
    val galleryId: String,
    val postNo: String,
    val postSubject: String,
    val postContent: String,
    val commentContent: String
)

private data class PendingGuestbook(
    val cleaner: Cleaner,
    val userIds: List<String>,
    val message: String
)

private data class PendingCommentCleaner(
    val cleaner: Cleaner,
    val keywords: List<String>,
    val intervalSeconds: Int,
    val monitorMinutes: Int
)

private fun emptyGuestbookProgress() = GuestbookExecutionProgress(
    done = 0,
    total = 0,
    successCount = 0,
    failCount = 0
)
