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
import com.dccleaner.app.network.MonitoredWrittenPost
import com.dccleaner.app.network.commentContainsBlockedKeyword
import com.dccleaner.app.runtime.DccleanerExecutionEngine
import com.dccleaner.app.runtime.GuestbookExecutionProgress
import com.dccleaner.app.runtime.GuestbookExecutionRunner
import com.dccleaner.app.runtime.RuntimeLogSink
import com.dccleaner.app.runtime.RuntimeNotifier
import com.dccleaner.app.storage.DeleteTaskStore
import com.dccleaner.app.storage.GuestbookSentUserCache
import com.dccleaner.app.util.LogManager
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

        private const val SERVICE_PREFS_NAME = "dc_cleaner_service_state"
        private const val KEY_DAEWANGCON_ACTIVE = "daewangcon_active"
        private const val GUESTBOOK_NOTIFICATION_UPDATE_INTERVAL_MILLIS = 10_000L
        private const val GUESTBOOK_UI_UPDATE_INTERVAL_MILLIS = 1_000L

        const val ACTION_START_DELETE = "START_DELETE"
        const val ACTION_STOP_DELETE = "STOP_DELETE"
        const val ACTION_START_DAEWANGCON = "START_DAEWANGCON"
        const val ACTION_STOP_DAEWANGCON = "STOP_DAEWANGCON"
        const val ACTION_START_GUESTBOOK = "START_GUESTBOOK"
        const val ACTION_START_COMMENT_CLEANER = "START_COMMENT_CLEANER"
        const val ACTION_STOP_COMMENT_CLEANER = "STOP_COMMENT_CLEANER"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var logManager: LogManager
    private lateinit var deleteTaskStore: DeleteTaskStore
    private lateinit var guestbookSentUserCache: GuestbookSentUserCache
    private lateinit var notifier: DcCleanerNotifier
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var engine: DccleanerExecutionEngine
    private var notificationUpdateJob: Job? = null
    private var stateMonitorJob: Job? = null
    private var guestbookJob: Job? = null
    private var commentCleanerJob: Job? = null
    private var preparedDaewangcon: PreparedDaewangcon? = null
    private var preparedGuestbook: PreparedGuestbook? = null
    private var preparedCommentCleaner: PreparedCommentCleaner? = null
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
    private val _isCommentCleanerRunning = MutableStateFlow(false)
    val isCommentCleanerRunning: StateFlow<Boolean> = _isCommentCleanerRunning.asStateFlow()

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
        get() = engine.isDaewangconRunning
    val isDaewangconCompleted: StateFlow<Boolean>
        get() = engine.isDaewangconCompleted
    val daewangconErrorMessage: StateFlow<String?>
        get() = engine.daewangconErrorMessage
    val daewangconProgress: StateFlow<Float>
        get() = engine.daewangconProgress
    val daewangconLog: StateFlow<List<String>>
        get() = engine.daewangconLog
    val daewangconPostCount: StateFlow<Int>
        get() = engine.daewangconPostCount
    val daewangconCommentCount: StateFlow<Int>
        get() = engine.daewangconCommentCount

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
            ACTION_START_COMMENT_CLEANER -> notifier.createNotification("댓글 자동 정리 준비 중...")
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
            ACTION_START_COMMENT_CLEANER -> startPreparedCommentCleaner()
            ACTION_STOP_COMMENT_CLEANER -> stopCommentCleaner()
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
        commentCleanerJob?.cancel()
        engine.close()
        wakeLockManager.release()
        serviceScope.cancel()
    }

    fun setCleaner(cleaner: Cleaner) = engine.setCleaner(cleaner)

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

    fun prepareCommentCleaner(
        cleaner: Cleaner,
        keywords: List<String>,
        intervalSeconds: Int,
        monitorMinutes: Int
    ) {
        preparedCommentCleaner = PreparedCommentCleaner(
            cleaner,
            keywords.filter(String::isNotBlank).distinct(),
            intervalSeconds.coerceAtLeast(5),
            monitorMinutes.coerceAtLeast(1)
        )
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
        acquireWakeLockIfNeeded()
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
            recordGuestbookLog = recordGuestbookLog
        )
    }

    fun resumeDeletion(task: DeleteTaskProgress) {
        acquireWakeLockIfNeeded()
        engine.resumeDeletion(task)
    }

    fun stopDeletion(cancelNotification: Boolean = true, preserveTask: Boolean = false) {
        engine.stopDeletion(preserveTask = preserveTask)
        notifier.cancelCaptchaNotification()
        if (isDaewangconRunning.value || isGuestbookSending.value || isCommentCleanerRunning.value) {
            when {
                isGuestbookSending.value -> notifier.updateGuestbookNotification(getGuestbookNotificationText())
                isDaewangconRunning.value -> notifier.updateDaewangconNotification()
                else -> notifier.updateNotification("댓글 자동 정리 실행 중")
            }
            return
        }
        cancelNotificationUpdate()
        wakeLockManager.release()
        if (cancelNotification) {
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
        } else {
            stopForegroundCompat(removeNotification = false)
        }
        stopSelf()
    }

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
        engine.startDaewangcon(galleryId, postNo, postSubject, postContent, commentContent)
    }

    fun stopDaewangcon() {
        preparedDaewangcon = null
        engine.stopDaewangcon()
        markDaewangconActive(false)
        if (!isDeleting.value && !isGuestbookSending.value && !isCommentCleanerRunning.value) {
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
            stopSelf()
        }
    }

    fun dismissDaewangconNotification() {
        daewangconNotificationDismissed = true
        engine.acknowledgeDaewangconResult()
        notifier.cancelNotification()
        if (!isDeleting.value && !isDaewangconRunning.value && !isGuestbookSending.value &&
            !isCommentCleanerRunning.value
        ) stopSelf()
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
                publishGuestbookProgress(latestProgress)
                _isGuestbookSending.value = false
                if (!isDeleting.value && !isDaewangconRunning.value && !isCommentCleanerRunning.value) {
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
            engine.interruptDaewangcon("대왕콘 작업 시작 정보를 불러오지 못했습니다.")
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
            engine.interruptDaewangcon("대왕콘 작업을 시작하지 못했습니다: ${e.message ?: "알 수 없는 오류"}")
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

    private fun startPreparedCommentCleaner() {
        val prepared = preparedCommentCleaner
        preparedCommentCleaner = null
        if (prepared == null || prepared.keywords.isEmpty()) {
            stopCommentCleaner()
            return
        }
        startCommentCleaner(prepared)
    }

    private fun startCommentCleaner(config: PreparedCommentCleaner) {
        commentCleanerJob?.cancel()
        acquireWakeLockIfNeeded()
        _isCommentCleanerRunning.value = true
        notifier.updateNotification("댓글 자동 정리 실행 중")
        commentCleanerJob = serviceScope.launch {
            val watchedPosts = linkedMapOf<String, WatchedPost>()
            var latestGallogNo: Long? = null
            try {
                while (isActive) {
                    val firstPage = config.cleaner.getRecentWrittenPosts()
                    if (firstPage == null) {
                        delay(config.intervalSeconds * 1_000L)
                        continue
                    }
                    if (latestGallogNo == null) {
                        latestGallogNo = firstPage.maxOfOrNull(MonitoredWrittenPost::gallogNo) ?: 0L
                    } else {
                        val previousGallogNo = latestGallogNo
                        var page = 1
                        var posts: List<MonitoredWrittenPost> = firstPage
                        while (posts.isNotEmpty()) {
                            val reachedKnownPost = posts.any { it.gallogNo <= previousGallogNo }
                            posts.filter { it.gallogNo > previousGallogNo }.forEach { post ->
                                watchedPosts[post.key] = WatchedPost(post, SystemClock.elapsedRealtime())
                            }
                            if (reachedKnownPost) break
                            delay(Cleaner.POST_REQUEST_DELAY)
                            posts = config.cleaner.getRecentWrittenPosts(++page) ?: break
                        }
                        latestGallogNo = maxOf(
                            previousGallogNo,
                            firstPage.maxOfOrNull(MonitoredWrittenPost::gallogNo) ?: previousGallogNo
                        )
                    }

                    val expiresAfter = config.monitorMinutes * 60_000L
                    val now = SystemClock.elapsedRealtime()
                    watchedPosts.entries.removeAll { now - it.value.detectedAt >= expiresAfter }
                    watchedPosts.values.forEachIndexed { index, watched ->
                        if (index > 0) delay(Cleaner.POST_REQUEST_DELAY)
                        config.cleaner.getMonitoredPostComments(watched.post)
                            ?.filter { comment ->
                                comment.id !in watched.deletedCommentIds &&
                                    commentContainsBlockedKeyword(comment.content, config.keywords)
                            }
                            ?.forEach { comment ->
                                if (config.cleaner.deleteMonitoredPostComment(watched.post, comment.id)) {
                                    watched.deletedCommentIds += comment.id
                                }
                            }
                    }
                    delay(config.intervalSeconds * 1_000L)
                }
            } finally {
                _isCommentCleanerRunning.value = false
                if (!isDeleting.value && !isDaewangconRunning.value && !isGuestbookSending.value) {
                    cancelNotificationUpdate()
                    wakeLockManager.release()
                    stopForegroundCompat(removeNotification = true)
                    notifier.cancelNotification()
                    stopSelf()
                }
            }
        }
    }

    fun stopCommentCleaner() {
        preparedCommentCleaner = null
        commentCleanerJob?.cancel()
        commentCleanerJob = null
        _isCommentCleanerRunning.value = false
        if (isDeleting.value) {
            notifier.updateNotification(getDeletionNotificationText())
        } else if (isDaewangconRunning.value) {
            notifier.updateDaewangconNotification()
        } else if (isGuestbookSending.value) {
            notifier.updateGuestbookNotification(getGuestbookNotificationText())
        } else {
            cancelNotificationUpdate()
            wakeLockManager.release()
            stopForegroundCompat(removeNotification = true)
            notifier.cancelNotification()
            stopSelf()
        }
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
                    isCommentCleanerRunning.value -> notifier.updateNotification("댓글 자동 정리 실행 중")
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
                val commentCleanerNow = isCommentCleanerRunning.value
                if (showCaptchaDialog.value) {
                    wakeLockManager.release()
                    notifier.showCaptchaNotification(null)
                }
                if (wasDeleting && !deletingNow && !daewangconNow && !guestbookNow && !commentCleanerNow) {
                    cancelNotificationUpdate()
                    wakeLockManager.release()
                    if (deletionForegroundStopMode(isCompleted.value) ==
                        DeletionForegroundStopMode.DetachAfterCompletionDelay
                    ) {
                        delay(COMPLETION_NOTIFICATION_DETACH_DELAY_MILLIS)
                        if (!isDeleting.value && !isDaewangconRunning.value && !isGuestbookSending.value &&
                            !isCommentCleanerRunning.value
                        ) {
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
                    if (!guestbookNow && !commentCleanerNow &&
                        daewangconFinishMode(deletingNow) == DaewangconFinishMode.StopForegroundAndService
                    ) {
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
                if (wasGuestbookSending && !guestbookNow && !deletingNow && !daewangconNow && !commentCleanerNow) {
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
            isCommentCleanerRunning.value -> notifier.updateNotification("댓글 자동 정리 실행 중")
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
        engine.interruptDaewangcon(DAEWANGCON_RECOVERY_FAILURE_MESSAGE)
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
            engine.interruptDaewangcon(DAEWANGCON_TIMEOUT_MESSAGE)
            markDaewangconActive(false)
            if (!daewangconNotificationDismissed) notifier.showDaewangconFailedNotification()
        }
        if (isGuestbookSending.value) {
            guestbookJob?.cancel()
            _isGuestbookSending.value = false
        }
        if (isCommentCleanerRunning.value) stopCommentCleaner()
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

private data class PreparedCommentCleaner(
    val cleaner: Cleaner,
    val keywords: List<String>,
    val intervalSeconds: Int,
    val monitorMinutes: Int
)

private data class WatchedPost(
    val post: MonitoredWrittenPost,
    val detectedAt: Long,
    val deletedCommentIds: MutableSet<String> = mutableSetOf()
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
