package com.dccleaner.app.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.dccleaner.app.model.DeletePhase
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.runtime.CommentMonitorConfig
import com.dccleaner.app.runtime.runCommentMonitor
import com.dccleaner.app.runtime.DccleanerExecutionEngine
import com.dccleaner.app.runtime.GuestbookExecutionProgress
import com.dccleaner.app.runtime.GuestbookExecutionRunner
import com.dccleaner.app.runtime.RuntimeLogSink
import com.dccleaner.app.runtime.RuntimeNotifier
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.storage.GuestbookSentUserCache
import com.dccleaner.app.util.LogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DcCleanerService : Service() {
    companion object {
        const val CHANNEL_ID = "DCCLEANER_CHANNEL"
        const val CAPTCHA_CHANNEL_ID = "DCCLEANER_CAPTCHA_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val CAPTCHA_NOTIFICATION_ID = 2

        private const val GUESTBOOK_NOTIFICATION_UPDATE_INTERVAL_MILLIS = 10_000L
        private const val GUESTBOOK_UI_UPDATE_INTERVAL_MILLIS = 1_000L

        const val ACTION_START_DELETE = "START_DELETE"
        const val ACTION_STOP_DELETE = "STOP_DELETE"
        const val ACTION_START_GUESTBOOK = "START_GUESTBOOK"
        const val ACTION_START_COMMENT_CLEANER = "START_COMMENT_CLEANER"
        const val ACTION_STOP_COMMENT_CLEANER = "STOP_COMMENT_CLEANER"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var logManager: LogManager
    private lateinit var deleteTaskStore: DeleteTaskStore
    private lateinit var guestbookSentUserCache: GuestbookSentUserCache
    private lateinit var notifier: DcCleanerNotifier
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var engine: DccleanerExecutionEngine
    private val engineCommandMutex = Mutex()
    private var pendingEngineCommands = 0
    private var foregroundStarted = false
    private var destroyed = false
    private var shutdownState = DeleteTaskState.INTERRUPTED
    private var shutdownMessage = "서비스가 종료되어 작업이 중단되었습니다."
    private var notificationUpdateJob: Job? = null
    private var stateMonitorJob: Job? = null
    private var guestbookJob: Job? = null
    private var commentCleanerJob: Job? = null
    private var preparedGuestbook: PreparedGuestbook? = null
    private var preparedCommentCleaner: CommentMonitorConfig? = null

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

    val isDeleting: StateFlow<Boolean>
        get() = engine.isDeleting
    val isCompleted: StateFlow<Boolean>
        get() = engine.isCompleted
    val progress: StateFlow<Float>
        get() = engine.progress
    val currentGallery: StateFlow<String>
        get() = engine.currentGallery
    val currentGalleryEstimatedTimeLeft: StateFlow<Long>
        get() = engine.currentGalleryEstimatedTimeLeft
    val currentDeletionSpeedPerHour: StateFlow<Int>
        get() = engine.currentDeletionSpeedPerHour
    val nextCaptchaEstimatedTimeLeft: StateFlow<Long>
        get() = engine.nextCaptchaEstimatedTimeLeft
    val isTwoCaptchaConfigured: StateFlow<Boolean>
        get() = engine.isTwoCaptchaConfigured
    val currentTaskLoginId: StateFlow<String>
        get() = engine.currentTaskLoginId
    val currentDeleteType: StateFlow<String>
        get() = engine.currentDeleteType
    val itemCounts get() = engine.itemCounts

    val deletedCount: StateFlow<Int>
        get() = engine.deletedCount
    val totalCount: StateFlow<Int>
        get() = engine.totalCount
    val deleteLog: StateFlow<List<String>>
        get() = engine.deleteLog
    val errorMessage: StateFlow<String?>
        get() = engine.errorMessage
    val showCaptchaDialog: StateFlow<Boolean>
        get() = engine.showCaptchaDialog
    val captchaFlag: StateFlow<Boolean>
        get() = engine.captchaFlag

    inner class LocalBinder : Binder() {
        fun getService(): DcCleanerService = this@DcCleanerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logManager = LogManager(applicationContext)
        deleteTaskStore = DeleteTaskStore(applicationContext)
        guestbookSentUserCache = GuestbookSentUserCache(applicationContext)
        notifier = DcCleanerNotifier(applicationContext)
        wakeLockManager = WakeLockManager(applicationContext)
        notifier.createNotificationChannel()
        engine = DccleanerExecutionEngine(
            deleteTaskStore = deleteTaskStore,
            logSink = RuntimeLogSink { tag, message -> logManager.addLog(tag, message) },
            notifier = AndroidEngineNotifier()
        )
        startStateMonitor()
        serviceScope.launch { logManager.addLog("Service", "DcCleanerService created") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = when (intent?.action) {
            ACTION_START_GUESTBOOK -> notifier.createGuestbookNotification()
            ACTION_START_COMMENT_CLEANER -> notifier.createNotification("댓글 자동 정리 준비 중...")
            else -> notifier.createNotification("삭제 작업 준비 중...")
        }
        startForeground(NOTIFICATION_ID, initialNotification)
        foregroundStarted = true
        startPeriodicNotificationUpdate()

        if (intent == null) {
            runEngineCommand { markInterruptedTasksFromProcessRestart() }
            stopDeletion(preserveTask = true)
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP_DELETE -> stopDeletion()
            ACTION_START_GUESTBOOK -> startPreparedGuestbook()
            ACTION_START_COMMENT_CLEANER -> startPreparedCommentCleaner()
            ACTION_STOP_COMMENT_CLEANER -> stopCommentCleaner()
        }
        refreshServiceLifetime()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isDeleting.value) {
            runEngineCommand {
                engine.pauseDeletion(
                    DeleteTaskState.INTERRUPTED,
                    TASK_REMOVED_INTERRUPTION_MESSAGE
                )
            }
            notifier.cancelCaptchaNotification()
        }
    }

    override fun onDestroy() {
        destroyed = true
        cancelNotificationUpdate()
        stateMonitorJob?.cancel()
        guestbookJob?.cancel()
        commentCleanerJob?.cancel()
        wakeLockManager.release()
        serviceScope.cancel()
        // Finish the current disk operation before closing the engine; never block Main.
        serviceScope.launch(NonCancellable + Dispatchers.IO) {
            try {
                engineCommandMutex.withLock {
                    if (isDeleting.value) {
                        engine.pauseDeletion(shutdownState, shutdownMessage, notify = false)
                    }
                }
            } catch (error: Exception) {
                logManager.addLog("Service", "종료 상태 저장 실패: ${error.message}")
            } finally {
                engine.close()
            }
        }
        super.onDestroy()
    }

    private fun runEngineCommand(command: () -> Unit) {
        if (destroyed) return
        pendingEngineCommands++
        serviceScope.launch {
            try {
                engineCommandMutex.withLock {
                    withContext(Dispatchers.IO) { command() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logManager.addLog("Service", "작업 명령 실패: ${error.message}")
                notifier.showInterruptionNotification(engine.getCurrentTaskId(), "작업 중단",
                    "작업을 시작하거나 저장하지 못했습니다. 앱에서 작업 기록을 확인해 주세요.")
            } finally {
                pendingEngineCommands--
                refreshServiceLifetime()
            }
        }
    }

    suspend fun stopForLogout() {
        preparedGuestbook = null
        preparedCommentCleaner = null
        pendingEngineCommands++
        try {
            guestbookJob?.cancel()
            commentCleanerJob?.cancel()
            engineCommandMutex.withLock {
                withContext(Dispatchers.IO) { engine.stopForLogout() }
            }
            guestbookJob?.join()
            commentCleanerJob?.join()
            notifier.cancelCaptchaNotification()
        } finally {
            pendingEngineCommands--
            refreshServiceLifetime()
        }
    }

    suspend fun stopForHandOff(): Boolean {
        pendingEngineCommands++
        try {
            // 멈추지 못했으면(이미 끝나던 참 등) 캡챠를 여전히 기다릴 수 있으므로 알림을 남긴다.
            return engineCommandMutex.withLock { withContext(Dispatchers.IO) { engine.stopForHandOff() } }
                .also { if (!isDeleting.value) notifier.cancelCaptchaNotification() }
        } finally {
            pendingEngineCommands--
            refreshServiceLifetime()
        }
    }


    fun setCleaner(cleaner: Cleaner) = runEngineCommand { engine.setCleaner(cleaner) }

    fun prepareGuestbook(cleaner: Cleaner, userIds: List<String>, message: String) {
        preparedGuestbook = PreparedGuestbook(cleaner, userIds, message)
    }

    fun prepareCommentCleaner(
        cleaner: Cleaner,
        keywords: List<String>,
        intervalSeconds: Int,
        monitorMinutes: Int
    ) {
        preparedCommentCleaner = CommentMonitorConfig.of(cleaner, keywords, intervalSeconds, monitorMinutes)
    }

    fun isDeleting(): Boolean = isDeleting.value
    fun getCurrentTaskLoginId(): String = engine.getCurrentTaskLoginId()
    fun clearError() = engine.clearError()
    fun clearLogs() = engine.clearLogs()
    fun acknowledgeDeletionResult() = engine.acknowledgeDeletionResult()
    fun resolveCaptcha() {
        runEngineCommand { engine.resolveCaptcha() }
        notifier.cancelCaptchaNotification()
    }

    fun useTwoCaptchaKey(key: String) {
        runEngineCommand {
            if (engine.useTwoCaptchaKey(key)) notifier.cancelCaptchaNotification()
        }
    }

    fun startDeletion(
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
        deleteQuestionPosts: Boolean = false,
        followUp: DeletePhase? = null
    ) {
        runEngineCommand {
            engine.startDeletion(
                selectedGalleries = selectedGalleries,
                deleteType = deleteType,
                galleryMap = galleryMap,
                twoCaptchaApiKey = twoCaptchaApiKey,
                recommendFilterEnabled = recommendFilterEnabled,
                commentFilterEnabled = commentFilterEnabled,
                viewFilterEnabled = viewFilterEnabled,
                postContentFilterEnabled = postContentFilterEnabled,
                commentContentFilterEnabled = commentContentFilterEnabled,
                dateFilterEnabled = dateFilterEnabled,
                deleteNewestFirst = deleteNewestFirst,
                deleteQuestionPosts = deleteQuestionPosts,
                minRecommendToKeep = minRecommendToKeep,
                minCommentToKeep = minCommentToKeep,
                minViewToKeep = minViewToKeep,
                myPostFilterEnabled = myPostFilterEnabled,
                dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
                postContentRegex = postContentRegex,
                commentRegexFilter = commentRegexFilter,
                minPostAgeDaysToDelete = minPostAgeDaysToDelete,
                recordGuestbookLog = recordGuestbookLog,
                followUp = followUp
            )
        }
    }

    fun resumeDeletion(task: DeleteTaskProgress) {
        runEngineCommand { engine.resumeDeletion(task) }
    }

    fun stopDeletion(preserveTask: Boolean = false) {
        runEngineCommand { engine.stopDeletion(preserveTask = preserveTask) }
        notifier.cancelCaptchaNotification()
    }

    fun startGuestbook(userIds: List<String>, message: String, cleaner: Cleaner) {
        if (userIds.isEmpty()) return
        if (_isGuestbookSending.value) return
        acquireWakeLockIfNeeded()
        _isGuestbookSending.value = true
        publishGuestbookProgress(emptyGuestbookProgress(userIds.size))
        notifier.updateGuestbookNotification(getGuestbookNotificationText())
        guestbookJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            var latestProgress = emptyGuestbookProgress(userIds.size)
            var lastNotificationUpdateAt = SystemClock.elapsedRealtime()
            val senderId = cleaner.getUserId()
            val cacheBatch = ArrayList<String>(GuestbookSentUserCache.WRITE_BATCH_SIZE)
            val progressUpdateLimiter = GuestbookProgressUpdateLimiter(
                intervalMillis = GUESTBOOK_UI_UPDATE_INTERVAL_MILLIS,
                initialTimeMillis = lastNotificationUpdateAt
            )
            try {
                GuestbookExecutionRunner.run(
                    userIds = userIds,
                    message = message,
                    send = cleaner::writeGuestbook,
                    onResult = { userId, success ->
                        if (success && senderId.isNotBlank()) {
                            cacheBatch.add(userId)
                            if (cacheBatch.size >= GuestbookSentUserCache.WRITE_BATCH_SIZE &&
                                guestbookSentUserCache.append(senderId, cacheBatch)
                            ) {
                                cacheBatch.clear()
                            }
                        }
                    }
                ) { progress ->
                    latestProgress = progress
                    val now = SystemClock.elapsedRealtime()
                    if (progressUpdateLimiter.shouldPublish(progress, now)) {
                        publishGuestbookProgress(progress)
                    }
                    if (progress.done > 0) {
                        if (now - lastNotificationUpdateAt >= GUESTBOOK_NOTIFICATION_UPDATE_INTERVAL_MILLIS) {
                            notifier.updateGuestbookNotification(getGuestbookNotificationText())
                            lastNotificationUpdateAt = now
                        }
                    }
                }
                showGuestbookFinishedNotificationIfIdle()
            } finally {
                if (cacheBatch.isNotEmpty() && senderId.isNotBlank()) {
                    withContext(NonCancellable) {
                        guestbookSentUserCache.append(senderId, cacheBatch)
                    }
                }
                if (guestbookJob === currentCoroutineContext()[Job]) {
                    publishGuestbookProgress(latestProgress)
                    _isGuestbookSending.value = false
                    guestbookJob = null
                    refreshServiceLifetime()
                }
            }
        }
        guestbookJob?.start()
    }

    private fun publishGuestbookProgress(progress: GuestbookExecutionProgress) {
        _guestbookProgress.value = progress
        _guestbookProgressDone.value = progress.done
        _guestbookProgressTotal.value = progress.total
        _guestbookSuccessCount.value = progress.successCount
        _guestbookFailCount.value = progress.failCount
    }

    private fun startPreparedGuestbook() {
        val prepared = preparedGuestbook
        preparedGuestbook = null
        if (prepared == null) {
            refreshServiceLifetime()
            return
        }
        startGuestbook(prepared.userIds, prepared.message, prepared.cleaner)
    }

    private fun startPreparedCommentCleaner() {
        val prepared = preparedCommentCleaner
        preparedCommentCleaner = null
        if (prepared == null || !prepared.hasWork) {
            stopCommentCleaner()
            return
        }
        startCommentCleaner(prepared)
    }

    private fun startCommentCleaner(config: CommentMonitorConfig) {
        val previousJob = commentCleanerJob
        previousJob?.cancel()
        acquireWakeLockIfNeeded()
        _isCommentCleanerRunning.value = true
        notifier.updateNotification(COMMENT_MONITOR_NOTIFICATION)
        commentCleanerJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                previousJob?.join()
                runCommentMonitor(config, SystemClock::elapsedRealtime)
            } finally {
                if (commentCleanerJob === currentCoroutineContext()[Job]) {
                    commentCleanerJob = null
                    _isCommentCleanerRunning.value = false
                    refreshServiceLifetime()
                }
            }
        }
        commentCleanerJob?.start()
    }

    fun stopCommentCleaner() {
        preparedCommentCleaner = null
        commentCleanerJob?.cancel()
        // Keep the cancelling job until its finally runs so a restart can join it.
        _isCommentCleanerRunning.value = false
        refreshServiceLifetime()
    }

    private fun startPeriodicNotificationUpdate() {
        cancelNotificationUpdate()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(30_000)
                when {
                    isGuestbookSending.value -> notifier.updateGuestbookNotification(getGuestbookNotificationText())
                    isDeleting.value -> notifier.updateNotification(getDeletionNotificationText())
                    isCommentCleanerRunning.value -> notifier.updateNotification(COMMENT_MONITOR_NOTIFICATION)
                }
            }
        }
    }

    private fun cancelNotificationUpdate() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }

    private fun startStateMonitor() {
        stateMonitorJob = serviceScope.launch {
            combine(isDeleting, isGuestbookSending,
                isCommentCleanerRunning, showCaptchaDialog) { _, _, _, _ -> Unit }
                .collect { refreshServiceLifetime() }
        }
    }

    private fun refreshServiceLifetime() {
        if (destroyed || pendingEngineCommands > 0 ||
            preparedGuestbook != null || preparedCommentCleaner != null) return
        val otherWork = isGuestbookSending.value || isCommentCleanerRunning.value
        if (isDeleting.value || otherWork) {
            if (otherWork || !showCaptchaDialog.value) acquireWakeLockIfNeeded()
            else wakeLockManager.release()
            return
        }
        wakeLockManager.release()
        if (!foregroundStarted) return
        foregroundStarted = false
        cancelNotificationUpdate()
        stopForegroundCompat(removeNotification = true)
        stopSelf()
    }

    private fun getDeletionNotificationText(): String {
        val totalGalleries = totalCount.value
        val completedGalleries = deletedCount.value
        return if (totalGalleries > 0) {
            "총 ${totalGalleries}개 갤러리 중 ${completedGalleries}개 삭제 완료"
        } else {
            "갤러리 수집중..."
        }
    }

    private fun getGuestbookNotificationText(): String =
        if (guestbookProgressTotal.value > 0) {
            "방명록 ${guestbookProgressDone.value} / ${guestbookProgressTotal.value} 전송 완료"
        } else {
            "방명록 전송 준비 중..."
        }

    private fun showGuestbookFinishedNotificationIfIdle() {
        when {
            isDeleting.value -> notifier.updateNotification(getDeletionNotificationText())
            isCommentCleanerRunning.value -> notifier.updateNotification(COMMENT_MONITOR_NOTIFICATION)
            else -> notifier.showGuestbookCompletedNotification(
                _guestbookSuccessCount.value,
                _guestbookFailCount.value
            )
        }
    }

    private fun markInterruptedTasksFromProcessRestart() {
        deleteTaskStore.getAll()
            .filter {
                it.state == DeleteTaskState.RUNNING ||
                    it.state == DeleteTaskState.CAPTCHA_REQUIRED
            }
            .forEach { task ->
                deleteTaskStore.updateState(
                    task.id,
                    DeleteTaskState.INTERRUPTED,
                    "앱 프로세스가 종료되어 작업이 중단되었습니다. 로그인 후 이어서 진행해 주세요."
                )
                notifier.showInterruptionNotification(
                    task.id,
                    "삭제 작업이 중단되었습니다",
                    "로그인 후 저장된 지점부터 이어서 진행할 수 있습니다."
                )
            }
    }

    private fun acquireWakeLockIfNeeded() {
        if (!wakeLockManager.isHeld) wakeLockManager.acquire()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        shutdownState = DeleteTaskState.SERVICE_TIMEOUT
        shutdownMessage = "Android 백그라운드 실행 시간 제한에 도달했습니다. 앱을 열어 이어서 진행해 주세요."
        runEngineCommand {
            if (isDeleting.value) engine.pauseDeletion(shutdownState, shutdownMessage)
        }
        guestbookJob?.cancel()
        commentCleanerJob?.cancel()
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        wakeLockManager.release()
        foregroundStarted = false
        stopForegroundCompat(removeNotification = true)
        stopSelf(startId)
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        } else {
            stopForeground(removeNotification)
        }
    }

    private inner class AndroidEngineNotifier : RuntimeNotifier {
        override fun notify(title: String, message: String) {
            val taskId = engine.getCurrentTaskId()
            serviceScope.launch {
                when {
                    title.contains("캡챠") -> notifier.showCaptchaNotification(null)
                    title.contains("삭제 완료") -> notifier.showCompletedNotification(message)
                    else -> notifier.showInterruptionNotification(
                        taskId,
                        title,
                        message
                    )
                }
            }
        }
    }
}

private const val COMMENT_MONITOR_NOTIFICATION = "댓글 자동 정리 중"

private data class PreparedGuestbook(
    val cleaner: Cleaner,
    val userIds: List<String>,
    val message: String
)

private fun emptyGuestbookProgress(total: Int = 0) = GuestbookExecutionProgress(
    done = 0,
    total = total,
    successCount = 0,
    failCount = 0
)

internal class GuestbookProgressUpdateLimiter(
    private val intervalMillis: Long,
    initialTimeMillis: Long
) {
    private var lastPublishedAt = initialTimeMillis

    fun shouldPublish(progress: GuestbookExecutionProgress, nowMillis: Long): Boolean {
        val shouldPublish =
            progress.done == progress.total || nowMillis - lastPublishedAt >= intervalMillis
        if (shouldPublish) lastPublishedAt = nowMillis
        return shouldPublish
    }
}
