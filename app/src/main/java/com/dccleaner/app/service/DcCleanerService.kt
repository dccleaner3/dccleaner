package com.dccleaner.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.runtime.DaewangconRunner
import com.dccleaner.app.runtime.DccleanerExecutionEngine
import com.dccleaner.app.runtime.GuestbookExecutionProgress
import com.dccleaner.app.runtime.GuestbookExecutionRunner
import com.dccleaner.app.runtime.RuntimeLogSink
import com.dccleaner.app.runtime.RuntimeNotifier
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DcCleanerService : Service() {
    companion object {
        const val CHANNEL_ID = "DCCLEANER_CHANNEL"
        const val CAPTCHA_CHANNEL_ID = "DCCLEANER_CAPTCHA_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val CAPTCHA_NOTIFICATION_ID = 2

        private const val SERVICE_PREFS_NAME = "dc_cleaner_service_state"
        private const val KEY_DAEWANGCON_ACTIVE = "daewangcon_active"
        private const val GUESTBOOK_NOTIFICATION_UPDATE_INTERVAL_MILLIS = 10_000L
        private const val GUESTBOOK_UI_UPDATE_INTERVAL_MILLIS = 1_000L

        const val ACTION_START_DELETE = "START_DELETE"
        const val ACTION_STOP_DELETE = "STOP_DELETE"
        const val ACTION_START_DAEWANGCON = "START_DAEWANGCON"
        const val ACTION_STOP_DAEWANGCON = "STOP_DAEWANGCON"
        const val ACTION_START_GUESTBOOK = "START_GUESTBOOK"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var logManager: LogManager
    private lateinit var deleteTaskStore: DeleteTaskStore
    private lateinit var notifier: DcCleanerNotifier
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var engine: DccleanerExecutionEngine
    private lateinit var daewangconRunner: DaewangconRunner
    private var notificationUpdateJob: Job? = null
    private var stateMonitorJob: Job? = null
    private var guestbookJob: Job? = null
    private var preparedDaewangcon: PreparedDaewangcon? = null
    private var preparedGuestbook: PreparedGuestbook? = null
    private var daewangconNotificationDismissed = false

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

    private val servicePreferences by lazy {
        getSharedPreferences(SERVICE_PREFS_NAME, MODE_PRIVATE)
    }

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
    val nextCaptchaEstimatedTimeLeft: StateFlow<Long>
        get() = engine.nextCaptchaEstimatedTimeLeft
    val isTwoCaptchaConfigured: StateFlow<Boolean>
        get() = engine.isTwoCaptchaConfigured
    val currentTaskLoginId: StateFlow<String>
        get() = engine.currentTaskLoginId
    val currentDeleteType: StateFlow<String>
        get() = engine.currentDeleteType
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
    val isDaewangconRunning: StateFlow<Boolean>
        get() = daewangconRunner.isRunning
    val isDaewangconCompleted: StateFlow<Boolean>
        get() = daewangconRunner.isCompleted
    val daewangconErrorMessage: StateFlow<String?>
        get() = daewangconRunner.errorMessage
    val daewangconProgress: StateFlow<Float>
        get() = daewangconRunner.progress
    val daewangconLog: StateFlow<List<String>>
        get() = daewangconRunner.logs
    val daewangconPostCount: StateFlow<Int>
        get() = daewangconRunner.postCount
    val daewangconCommentCount: StateFlow<Int>
        get() = daewangconRunner.commentCount

    inner class LocalBinder : Binder() {
        fun getService(): DcCleanerService = this@DcCleanerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logManager = LogManager(applicationContext)
        deleteTaskStore = DeleteTaskStore(applicationContext)
        notifier = DcCleanerNotifier(applicationContext)
        wakeLockManager = WakeLockManager(applicationContext)
        notifier.createNotificationChannel()
        val runtimeLogSink = RuntimeLogSink { tag, message -> logManager.addLog(tag, message) }
        engine = DccleanerExecutionEngine(
            deleteTaskStore = deleteTaskStore,
            logSink = runtimeLogSink,
            notifier = AndroidEngineNotifier(),
            scope = serviceScope
        )
        daewangconRunner = DaewangconRunner(
            logSink = runtimeLogSink,
            notifier = AndroidEngineNotifier(),
            scope = serviceScope
        )
        recoverInterruptedDaewangconIfNeeded()
        startStateMonitor()
        serviceScope.launch { logManager.addLog("Service", "DcCleanerService created") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = when (intent?.action) {
            ACTION_START_DAEWANGCON -> notifier.createDaewangconNotification()
            ACTION_START_GUESTBOOK -> notifier.createGuestbookNotification()
            else -> notifier.createNotification("삭제 작업 준비 중...")
        }
        startForeground(NOTIFICATION_ID, initialNotification)
        startPeriodicNotificationUpdate()

        if (intent == null) {
            markInterruptedTasksFromProcessRestart()
            stopDeletion(cancelNotification = true, preserveTask = true)
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP_DELETE -> stopDeletion()
            ACTION_START_DAEWANGCON -> startPreparedDaewangcon()
            ACTION_STOP_DAEWANGCON -> stopDaewangcon()
            ACTION_START_GUESTBOOK -> startPreparedGuestbook()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isDeleting.value) {
            val taskId = engine.getCurrentTaskId()
            engine.pauseDeletion(
                DeleteTaskState.INTERRUPTED,
                TASK_REMOVED_INTERRUPTION_MESSAGE,
                notify = false
            )
            taskId?.let {
                notifier.showInterruptionNotification(
                    it,
                    "삭제 작업이 중단되었습니다",
                    TASK_REMOVED_INTERRUPTION_MESSAGE
                )
            }
            notifier.cancelCaptchaNotification()
            stopDeletion(cancelNotification = true, preserveTask = true)
        }
    }

    override fun onDestroy() {
        if (isDeleting.value) {
            engine.pauseDeletion(
                DeleteTaskState.INTERRUPTED,
                "서비스가 종료되어 작업이 중단되었습니다.",
                notify = false
            )
        }
        super.onDestroy()
        cancelNotificationUpdate()
        stateMonitorJob?.cancel()
        guestbookJob?.cancel()
        daewangconRunner.close()
        engine.close()
        wakeLockManager.release()
        serviceScope.cancel()
    }

    fun setCleaner(cleaner: Cleaner) {
        engine.setCleaner(cleaner)
        daewangconRunner.setCleaner(cleaner)
    }

    fun prepareDaewangcon(
        cleaner: Cleaner,
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        setCleaner(cleaner)
        preparedDaewangcon = PreparedDaewangcon(
            galleryId, postNo, postSubject, postContent, commentContent
        )
    }

    fun clearPreparedDaewangcon() {
        preparedDaewangcon = null
    }

    fun prepareGuestbook(cleaner: Cleaner, userIds: List<String>, message: String) {
        preparedGuestbook = PreparedGuestbook(cleaner, userIds, message)
    }

    fun isDeleting(): Boolean = isDeleting.value
    fun getCurrentTaskLoginId(): String = engine.getCurrentTaskLoginId()
    fun clearError() = engine.clearError()
    fun clearLogs() = engine.clearLogs()
    fun resolveCaptcha() {
        engine.resolveCaptcha()
        notifier.cancelCaptchaNotification()
        acquireWakeLockIfNeeded()
    }

    fun startDeletion(
        selectedGalleries: List<String>,
        deleteType: String,
        galleryMap: Map<String, String>,
        twoCaptchaApiKey: String = "",
        recommendFilterEnabled: Boolean = false,
        commentFilterEnabled: Boolean = false,
        postContentFilterEnabled: Boolean = false,
        commentContentFilterEnabled: Boolean = false,
        dateFilterEnabled: Boolean = false,
        deleteNewestFirst: Boolean = false,
        minRecommendToKeep: Int = -1,
        minCommentToKeep: Int = -1,
        myPostFilterEnabled: Boolean = false,
        dcconOnlyFilterEnabled: Boolean = false,
        postContentRegex: String = "",
        commentRegexFilter: String = "",
        minPostAgeDaysToDelete: Int = -1,
        recordGuestbookLog: Boolean = true
    ) {
        acquireWakeLockIfNeeded()
        engine.startDeletion(
            selectedGalleries = selectedGalleries,
            deleteType = deleteType,
            galleryMap = galleryMap,
            twoCaptchaApiKey = twoCaptchaApiKey,
            recommendFilterEnabled = recommendFilterEnabled,
            commentFilterEnabled = commentFilterEnabled,
            postContentFilterEnabled = postContentFilterEnabled,
            commentContentFilterEnabled = commentContentFilterEnabled,
            dateFilterEnabled = dateFilterEnabled,
            deleteNewestFirst = deleteNewestFirst,
            minRecommendToKeep = minRecommendToKeep,
            minCommentToKeep = minCommentToKeep,
            myPostFilterEnabled = myPostFilterEnabled,
            dcconOnlyFilterEnabled = dcconOnlyFilterEnabled,
            postContentRegex = postContentRegex,
            commentRegexFilter = commentRegexFilter,
            minPostAgeDaysToDelete = minPostAgeDaysToDelete,
            recordGuestbookLog = recordGuestbookLog
        )
    }

    fun resumeDeletion(task: DeleteTaskProgress) {
        acquireWakeLockIfNeeded()
        engine.resumeDeletion(task)
    }

    fun stopDeletion(cancelNotification: Boolean = true, preserveTask: Boolean = false) {
        engine.stopDeletion(preserveTask = preserveTask)
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        if (!isDaewangconRunning.value) wakeLockManager.release()
        if (cancelNotification) {
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
        } else {
            stopForegroundCompat(removeNotification = false)
        }
        if (!isDaewangconRunning.value) stopSelf()
    }

    @Suppress("UNUSED_PARAMETER")
    fun startDaewangcon(
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) {
        acquireWakeLockIfNeeded()
        daewangconNotificationDismissed = false
        markDaewangconActive(true)
        notifier.updateDaewangconNotification()
        daewangconRunner.start()
    }

    fun stopDaewangcon() {
        preparedDaewangcon = null
        daewangconRunner.stop()
        markDaewangconActive(false)
        if (!isDeleting.value) {
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
            stopSelf()
        }
    }

    fun dismissDaewangconNotification() {
        daewangconNotificationDismissed = true
        daewangconRunner.acknowledgeResult()
        notifier.cancelNotification()
        if (!isDeleting.value && !isDaewangconRunning.value && !isGuestbookSending.value) stopSelf()
    }

    fun startGuestbook(userIds: List<String>, message: String, cleaner: Cleaner) {
        if (userIds.isEmpty()) return
        if (_isGuestbookSending.value) return
        acquireWakeLockIfNeeded()
        _isGuestbookSending.value = true
        publishGuestbookProgress(emptyGuestbookProgress(userIds.size))
        notifier.updateGuestbookNotification(getGuestbookNotificationText())
        guestbookJob = serviceScope.launch {
            var latestProgress = emptyGuestbookProgress(userIds.size)
            var lastNotificationUpdateAt = SystemClock.elapsedRealtime()
            val progressUpdateLimiter = GuestbookProgressUpdateLimiter(
                intervalMillis = GUESTBOOK_UI_UPDATE_INTERVAL_MILLIS,
                initialTimeMillis = lastNotificationUpdateAt
            )
            try {
                GuestbookExecutionRunner.run(
                    userIds = userIds,
                    message = message,
                    send = cleaner::writeGuestbook
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
                publishGuestbookProgress(latestProgress)
                _isGuestbookSending.value = false
                if (!isDeleting.value && !isDaewangconRunning.value) {
                    cancelNotificationUpdate()
                    wakeLockManager.release()
                    stopForegroundCompat(removeNotification = false)
                    stopSelf()
                }
            }
        }
    }

    private fun publishGuestbookProgress(progress: GuestbookExecutionProgress) {
        _guestbookProgress.value = progress
        _guestbookProgressDone.value = progress.done
        _guestbookProgressTotal.value = progress.total
        _guestbookSuccessCount.value = progress.successCount
        _guestbookFailCount.value = progress.failCount
    }

    private fun startPreparedDaewangcon() {
        val prepared = preparedDaewangcon
        preparedDaewangcon = null
        if (prepared == null) {
            daewangconRunner.interrupt("대왕콘 작업 시작 정보를 불러오지 못했습니다.")
            markDaewangconActive(false)
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.showDaewangconFailedNotification()
            stopSelf()
            return
        }
        try {
            startDaewangcon(
                prepared.galleryId,
                prepared.postNo,
                prepared.postSubject,
                prepared.postContent,
                prepared.commentContent
            )
        } catch (e: RuntimeException) {
            daewangconRunner.interrupt("대왕콘 작업을 시작하지 못했습니다: ${e.message ?: "알 수 없는 오류"}")
            markDaewangconActive(false)
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.showDaewangconFailedNotification()
            stopSelf()
        }
    }

    private fun startPreparedGuestbook() {
        val prepared = preparedGuestbook
        preparedGuestbook = null
        if (prepared == null) {
            _isGuestbookSending.value = false
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
            stopSelf()
            return
        }
        startGuestbook(prepared.userIds, prepared.message, prepared.cleaner)
    }

    private fun startPeriodicNotificationUpdate() {
        cancelNotificationUpdate()
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(30_000)
                when {
                    isGuestbookSending.value -> notifier.updateGuestbookNotification(getGuestbookNotificationText())
                    isDaewangconRunning.value -> notifier.updateDaewangconNotification()
                    isDeleting.value -> notifier.updateNotification(getDeletionNotificationText())
                }
            }
        }
    }

    private fun cancelNotificationUpdate() {
        notificationUpdateJob?.cancel()
        notificationUpdateJob = null
    }

    private fun startStateMonitor() {
        stateMonitorJob?.cancel()
        stateMonitorJob = serviceScope.launch {
            var wasDeleting = false
            var wasDaewangconRunning = false
            var wasGuestbookSending = false
            while (isActive) {
                val deletingNow = isDeleting.value
                val daewangconNow = isDaewangconRunning.value
                val guestbookNow = isGuestbookSending.value
                if (showCaptchaDialog.value) {
                    wakeLockManager.release()
                    notifier.showCaptchaNotification(null)
                }
                if (wasDeleting && !deletingNow && !daewangconNow && !guestbookNow) {
                    cancelNotificationUpdate()
                    wakeLockManager.release()
                    if (deletionForegroundStopMode(isCompleted.value) ==
                        DeletionForegroundStopMode.DetachAfterCompletionDelay
                    ) {
                        delay(COMPLETION_NOTIFICATION_DETACH_DELAY_MILLIS)
                        if (!isDeleting.value && !isDaewangconRunning.value && !isGuestbookSending.value) {
                            stopForegroundCompat(removeNotification = false)
                            stopSelf()
                        }
                    } else {
                        stopForegroundCompat(removeNotification = true)
                        stopSelf()
                    }
                }
                if (wasDaewangconRunning && !daewangconNow) {
                    markDaewangconActive(false)
                    if (!guestbookNow && daewangconFinishMode(deletingNow) == DaewangconFinishMode.StopForegroundAndService) {
                        cancelNotificationUpdate()
                        wakeLockManager.release()
                        stopForegroundCompat(removeNotification = true)
                        if (isDaewangconCompleted.value && !daewangconNotificationDismissed) {
                            notifier.showDaewangconCompletedNotification()
                        } else if (daewangconErrorMessage.value != null && !daewangconNotificationDismissed) {
                            notifier.showDaewangconFailedNotification()
                        } else {
                            notifier.cancelNotification()
                        }
                        stopSelf()
                    }
                }
                if (wasGuestbookSending && !guestbookNow && !deletingNow && !daewangconNow) {
                    cancelNotificationUpdate()
                    wakeLockManager.release()
                }
                wasDeleting = deletingNow
                wasDaewangconRunning = daewangconNow
                wasGuestbookSending = guestbookNow
                delay(500)
            }
        }
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
            isDaewangconRunning.value -> notifier.updateDaewangconNotification()
            isDeleting.value -> notifier.updateNotification(getDeletionNotificationText())
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

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun markDaewangconActive(active: Boolean) {
        servicePreferences.edit().putBoolean(KEY_DAEWANGCON_ACTIVE, active).commit()
    }

    private fun recoverInterruptedDaewangconIfNeeded() {
        if (!servicePreferences.getBoolean(KEY_DAEWANGCON_ACTIVE, false)) return
        markDaewangconActive(false)
        daewangconRunner.interrupt(DAEWANGCON_RECOVERY_FAILURE_MESSAGE)
        notifier.showDaewangconFailedNotification()
    }

    private fun acquireWakeLockIfNeeded() {
        if (!wakeLockManager.isHeld) wakeLockManager.acquire()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        if (isDeleting.value) {
            engine.pauseDeletion(
                DeleteTaskState.SERVICE_TIMEOUT,
                "Android 백그라운드 실행 시간 제한에 도달했습니다. 앱을 열어 이어서 진행해 주세요."
            )
        }
        if (isDaewangconRunning.value) {
            daewangconRunner.interrupt(DAEWANGCON_TIMEOUT_MESSAGE)
            markDaewangconActive(false)
            if (!daewangconNotificationDismissed) notifier.showDaewangconFailedNotification()
        }
        if (isGuestbookSending.value) {
            guestbookJob?.cancel()
            _isGuestbookSending.value = false
        }
        cancelNotificationUpdate()
        notifier.cancelCaptchaNotification()
        wakeLockManager.release()
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
            when {
                title.contains("캡챠") -> notifier.showCaptchaNotification(null)
                title.contains("삭제 완료") -> notifier.showCompletedNotification(message)
                title.contains("대왕콘") -> Unit
                else -> notifier.showInterruptionNotification(
                    title.hashCode().toString(),
                    title,
                    message
                )
            }
        }
    }
}

private data class PreparedDaewangcon(
    val galleryId: String,
    val postNo: String,
    val postSubject: String,
    val postContent: String,
    val commentContent: String
)

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
