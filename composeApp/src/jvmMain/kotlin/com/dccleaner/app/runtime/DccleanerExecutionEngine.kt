package com.dccleaner.app.runtime

import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DELETE_PROGRESS_LOG_MARKER
import com.dccleaner.app.model.DeletePhase
import com.dccleaner.app.model.DeleteQueueCheckpoint
import com.dccleaner.app.model.DeleteResult
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.model.DeleteTimeEstimator
import com.dccleaner.app.model.PostListResult
import com.dccleaner.app.model.deleteGalleryProgressMessage
import com.dccleaner.app.model.replaceDeleteProgressLog
import com.dccleaner.app.network.Cleaner
import com.dccleaner.app.network.CleanerPort
import com.dccleaner.app.platform.generateDeleteTaskId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DccleanerExecutionEngine(
    private val deleteTaskStore: DeleteTaskStorePort,
    private val logSink: RuntimeLogSink = NoOpRuntimeLogSink,
    private val notifier: RuntimeNotifier = NoOpRuntimeNotifier,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val timing: DccleanerExecutionTiming = DccleanerExecutionTiming()
) {
    companion object {
        private const val CAPTCHA_DELETE_INTERVAL = 200
        private const val CHECKPOINT_OPERATION_INTERVAL = 20
        private const val CHECKPOINT_TIME_INTERVAL_MS = 10_000L
    }

    private var cleaner: CleanerPort? = null
    private val deleteJobLock = Any()
    private var deleteJob: Job? = null
    private var speedUpdateJob: Job? = null
    private var pendingDeleteTask: DeleteTaskProgress? = null
    private var currentTask: DeleteTaskProgress? = null
    private var operationsSinceCheckpoint = 0
    private var lastCheckpointAt = 0L
    private var minRecommendToKeep: Int = -1
    private var minCommentToKeep: Int = -1
    private var minViewToKeep: Int = -1
    private var postContentRegex: String = ""
    private var myPostFilterEnabled: Boolean = false
    private var dcconOnlyFilterEnabled: Boolean = false
    private var commentRegexFilter: String = ""
    private var minPostAgeDaysToDelete: Int = -1
    private var captchaCycleDeletedCount = 0
    private var captchaTotalDeletionMillis = 0L
    private var captchaDeleteAttemptStartedAt = 0L
    private var currentGalleryCaptchaSolved = 0
    @Volatile
    private var deletionSpeedStartedAt = 0L
    @Volatile
    private var deletionSpeedDeletedCount = 0
    @Volatile
    private var deletionSpeedSkippedCount = 0

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

    private val _currentDeletionSpeedPerHour = MutableStateFlow(0)
    val currentDeletionSpeedPerHour: StateFlow<Int> = _currentDeletionSpeedPerHour.asStateFlow()

    private val _nextCaptchaEstimatedTimeLeft = MutableStateFlow(0L)
    val nextCaptchaEstimatedTimeLeft: StateFlow<Long> = _nextCaptchaEstimatedTimeLeft.asStateFlow()

    private val _isTwoCaptchaConfigured = MutableStateFlow(false)
    /** 키를 등록하거나 지울 때마다 오른다. 같은 키를 다시 등록해도(예: 2Captcha 잔액 충전 뒤) 새 등록으로 알아본다. */
    @Volatile private var twoCaptchaKeyGeneration = 0
    val isTwoCaptchaConfigured: StateFlow<Boolean> = _isTwoCaptchaConfigured.asStateFlow()

    private val _currentTaskLoginId = MutableStateFlow("")
    val currentTaskLoginId: StateFlow<String> = _currentTaskLoginId.asStateFlow()

    private val _currentDeleteType = MutableStateFlow("")
    val currentDeleteType: StateFlow<String> = _currentDeleteType.asStateFlow()

    private val _itemCounts = MutableStateFlow(com.dccleaner.app.model.DeleteItemCounts())
    val itemCounts = _itemCounts.asStateFlow()

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

    fun setCleaner(cleaner: CleanerPort) {
        this.cleaner = cleaner
        _isTwoCaptchaConfigured.value = cleaner.has2CaptchaKey()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearLogs() {
        _deleteLog.update { emptyList() }
    }

    fun getCurrentTaskLoginId(): String = _currentTaskLoginId.value

    fun getCurrentTaskId(): String? = currentTask?.id

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
        deleteQuestionPosts: Boolean = false,
        minRecommendToKeep: Int = -1,
        minCommentToKeep: Int = -1,
        minViewToKeep: Int = -1,
        myPostFilterEnabled: Boolean = false,
        dcconOnlyFilterEnabled: Boolean = false,
        postContentRegex: String = "",
        commentRegexFilter: String = "",
        minPostAgeDaysToDelete: Int = -1,
        recordGuestbookLog: Boolean = true,
        followUp: DeletePhase? = null
    ) {
        val task = DeleteTaskProgress(
            loginId = cleaner?.getUserId().orEmpty(),
            deleteType = deleteType,
            selectedGalleries = selectedGalleries,
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
        // 종류별로 걸러지는 옵션(질문 글 등)이 첫 단계 종류로 지워지지 않게 정규화 전 값에서 두 번째 단계를 만든다.
        val followUpTask = followUp?.let { p ->
            task.copy(
                id = generateDeleteTaskId(),
                deleteType = p.deleteType,
                selectedGalleries = p.selectedGalleries,
                galleryMap = p.galleryMap,
                dateFilterEnabled = p.dateFilterEnabled,
                deleteNewestFirst = p.deleteNewestFirst,
                minPostAgeDaysToDelete = p.minPostAgeDaysToDelete
            ).normalizedForExecution()
        }
        startOrQueueDeletion(task.copy(followUp = followUpTask).normalizedForExecution())
    }

    fun resumeDeletion(task: DeleteTaskProgress) {
        val normalizedTask = task.normalizedForExecution().copy(
            state = DeleteTaskState.RUNNING,
            statusMessage = "삭제 작업 재개 중",
            captchaRequired = false
        )
        startOrQueueDeletion(normalizedTask)
    }

    fun stopDeletion(preserveTask: Boolean = false) {
        val jobToStop = synchronized(deleteJobLock) { deleteJob }
        if (!preserveTask && jobToStop?.isActive == true) {
            discardCurrentTaskByUser()
        }
        synchronized(deleteJobLock) {
            pendingDeleteTask = null
            if (deleteJob === jobToStop) {
                jobToStop?.cancel()
                _isDeleting.value = false
                _captchaFlag.value = false
                _showCaptchaDialog.value = false
            } else if (deleteJob == null) {
                _isDeleting.value = false
                _captchaFlag.value = false
                _showCaptchaDialog.value = false
            }
        }
    }

    @Synchronized
    private fun discardCurrentTaskByUser() {
        val task = currentTask ?: return
        currentTask = null
        if (!deleteTaskStore.remove(task.id)) {
            deleteTaskStore.updateState(
                task.id,
                DeleteTaskState.PAUSED_BY_USER,
                "사용자가 작업을 중단했습니다."
            )
        }
    }

    fun pauseDeletion(
        state: DeleteTaskState,
        message: String,
        captchaRequired: Boolean = false,
        notify: Boolean = true
    ) {
        pauseCurrentTask(
            state = state,
            message = message,
            captchaRequired = captchaRequired,
            notify = notify
        )
        stopDeletion(preserveTask = true)
    }

    fun resolveCaptcha() {
        _captchaFlag.value = false
        _showCaptchaDialog.value = false
        resetCaptchaEstimateCycle()
        cleaner?.resetCaptchaState()
        updateTask(force = true) {
            it.copy(
                state = DeleteTaskState.RUNNING,
                statusMessage = "캡챠 해결 완료, 삭제 재개 중",
                captchaRequired = false
            )
        }
        updateDeleteProgressStatus("✅ 캡챠 해결 완료 - 삭제 재개")
    }

    /**
     * 돌고 있는 작업에 2Captcha 키를 바로 적용한다. 빈 키는 키를 지운 것이라 작업도 직접 풀기로 돌린다.
     * 다음 단계로 넘어가는 순간(completeDeletion)과 엇갈리지 않게 작업 기록과 같은 잠금 안에서 바꾼다.
     */
    @Synchronized
    fun useTwoCaptchaKey(key: String): Boolean {
        // 멈춘 뒤 사용자가 지운 기록은 되살리지 않는다. 돌거나 남아 있는 작업만 새 키로 저장한다.
        val task = currentTask
        if (task != null && deleteTaskStore.get(task.id) != null && !updateTask(force = true) { it.copy(twoCaptchaApiKey = key) }) return false
        // 앞 작업이 멈추길 기다리는 작업도 시작할 때 옛 키로 되돌리지 않게 새 키를 넣어 둔다.
        synchronized(deleteJobLock) { pendingDeleteTask = pendingDeleteTask?.copy(twoCaptchaApiKey = key) }
        val wasConfigured = _isTwoCaptchaConfigured.value
        cleaner?.restore2CaptchaKey(key)
        twoCaptchaKeyGeneration++
        _isTwoCaptchaConfigured.value = cleaner?.has2CaptchaKey() == true
        if (_isDeleting.value) when {
            _isTwoCaptchaConfigured.value -> addLogMessage("🔑 2Captcha 키를 적용했어요 · 이제 캡챠를 자동으로 풀어요")
            wasConfigured -> addLogMessage("🔑 2Captcha 키를 지웠어요 · 캡챠가 나오면 직접 풀어야 해요")
        }
        if (!_isTwoCaptchaConfigured.value) resetCaptchaEstimateCycle()
        if (_isTwoCaptchaConfigured.value && _captchaFlag.value) resolveCaptcha()
        return _isTwoCaptchaConfigured.value
    }

    /**
     * 초고속 삭제로 바꾸려고 직접 삭제를 끝낸다. 사용자가 중단한 것처럼 이어하기 기록도 지운다.
     * 보내던 삭제 요청이 끝나거나 끊길 때까지 기다려, 뒤이어 시작할 서버 작업과 겹치지 않는다.
     * 끝난 작업이 마무리 중(방명록 기록 등)이라 끝낼 게 없으면 false.
     */
    suspend fun stopForHandOff(): Boolean {
        if (currentTask == null) return false
        stopDeletion()
        synchronized(deleteJobLock) { deleteJob }?.join()
        // 작업이 막 멈춘 참이라 stopDeletion이 기록을 남겼어도 여기서 지운다.
        discardCurrentTaskByUser()
        return true
    }

    fun acknowledgeDeletionResult() {
        _isCompleted.value = false
    }

    /** Stop requests before the caller clears authentication; keep a resumable checkpoint. */
    suspend fun stopForLogout() {
        stopDeletion(preserveTask = true)
        val deletion = synchronized(deleteJobLock) { deleteJob }
        deletion?.join()
        speedUpdateJob?.cancel()
        val checkpointSaved = currentTask == null ||
            pauseCurrentTask(DeleteTaskState.INTERRUPTED, "로그아웃하여 작업을 중단했습니다.", notify = false)
        check(checkpointSaved) { "작업 기록을 저장하지 못했습니다. 저장 공간을 확인한 뒤 다시 로그아웃해 주세요." }
        cleaner = null
        currentTask = null
        _currentTaskLoginId.value = ""
        _currentDeleteType.value = ""
        _isCompleted.value = false
        _errorMessage.value = null
        clearLogs()
    }

    fun close() {
        stopDeletion(preserveTask = true)
        scope.cancel()
    }

    private fun applyTaskSettings(task: DeleteTaskProgress) {
        cleaner?.restore2CaptchaKey(task.twoCaptchaApiKey)
        minRecommendToKeep = task.minRecommendToKeep
        minCommentToKeep = task.minCommentToKeep
        minViewToKeep = task.minViewToKeep
        postContentRegex = task.postContentRegex
        myPostFilterEnabled = task.myPostFilterEnabled
        dcconOnlyFilterEnabled = task.dcconOnlyFilterEnabled
        commentRegexFilter = task.commentRegexFilter
        minPostAgeDaysToDelete = task.minPostAgeDaysToDelete
    }

    private fun startOrQueueDeletion(task: DeleteTaskProgress) {
        synchronized(deleteJobLock) {
            val currentJob = deleteJob
            if (currentJob != null && !currentJob.isCompleted) {
                if (currentJob.isActive) return
                pendingDeleteTask = task
                _isDeleting.value = true
                return
            }
            applyTaskSettings(task)
            beginDeletion(task)
        }
    }

    private fun beginDeletion(task: DeleteTaskProgress) {
        _currentTaskLoginId.value = task.loginId
        _currentDeleteType.value = task.deleteType
        currentTask = task
        operationsSinceCheckpoint = 0
        if (!deleteTaskStore.save(task)) {
            handleTaskPersistenceFailure("삭제 작업을 저장하지 못해 시작하지 않았습니다.")
            return
        }
        lastCheckpointAt = System.currentTimeMillis()
        resetDeletionState()
        _itemCounts.value = com.dccleaner.app.model.DeleteItemCounts(task.totalDeleted, task.totalSkipped)
        _isTwoCaptchaConfigured.value = cleaner?.has2CaptchaKey() == true
        if (!_isTwoCaptchaConfigured.value) resetCaptchaEstimateCycle()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            logSink.addLog("Delete", "선택된 갤러리: ${task.selectedGalleries.size}개, 타입: ${task.deleteType}")
            try {
                performDeletion(task)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logSink.addLog("Delete", "ERROR: ${e.message}")
                handleDeletionError(e)
            } finally {
                val finishingJob = currentCoroutineContext()[Job]
                synchronized(deleteJobLock) {
                    if (deleteJob === finishingJob) {
                        deleteJob = null
                        pendingDeleteTask?.let { nextTask ->
                            pendingDeleteTask = null
                            applyTaskSettings(nextTask)
                            beginDeletion(nextTask)
                        }
                    }
                }
            }
        }
        deleteJob = job
        job.start()
    }

    private fun resetDeletionState(keepLog: Boolean = false) {
        _isDeleting.value = true
        _isCompleted.value = false
        _errorMessage.value = null
        _progress.value = 0f
        _currentGallery.value = ""
        _currentGalleryEstimatedTimeLeft.value = 0L
        _currentDeletionSpeedPerHour.value = 0
        _nextCaptchaEstimatedTimeLeft.value = 0L
        captchaCycleDeletedCount = 0
        captchaTotalDeletionMillis = 0L
        captchaDeleteAttemptStartedAt = 0L
        currentGalleryCaptchaSolved = 0
        deletionSpeedStartedAt = System.currentTimeMillis()
        deletionSpeedDeletedCount = 0
        deletionSpeedSkippedCount = 0
        speedUpdateJob?.cancel()
        speedUpdateJob = scope.launch {
            while (isActive && _isDeleting.value) {
                delay(1_000)
                if (_isDeleting.value) updateDeletionSpeed()
            }
        }
        _deletedCount.value = 0
        _totalCount.value = 0
        if (!keepLog) _deleteLog.update { emptyList() }
        _showCaptchaDialog.value = false
        _captchaFlag.value = false
    }

    private fun handleDeletionError(exception: Exception) {
        _errorMessage.value = "삭제 중 오류 발생: ${exception.message}"
        addLogMessage("❌ 오류: ${exception.message}")
        pauseCurrentTask(
            DeleteTaskState.NETWORK_ERROR,
            "오류로 작업이 중단되었습니다: ${exception.message ?: "알 수 없는 오류"}"
        )
        _isDeleting.value = false
    }

    @Synchronized
    private fun updateTask(
        force: Boolean = false,
        transform: (DeleteTaskProgress) -> DeleteTaskProgress
    ): Boolean {
        val previous = currentTask ?: return false
        val next = transform(previous)
        val updated = next.copy(totalSkipped = previous.totalSkipped +
            if (next.currentGalleryIndex == previous.currentGalleryIndex)
                (next.currentGallerySkipped - previous.currentGallerySkipped).coerceAtLeast(0) else 0)
        currentTask = updated
        _itemCounts.value = com.dccleaner.app.model.DeleteItemCounts(updated.totalDeleted, updated.totalSkipped)
        operationsSinceCheckpoint++
        val now = System.currentTimeMillis()
        if (force || operationsSinceCheckpoint >= CHECKPOINT_OPERATION_INTERVAL ||
            now - lastCheckpointAt >= CHECKPOINT_TIME_INTERVAL_MS
        ) {
            if (!deleteTaskStore.save(updated)) {
                handleTaskPersistenceFailure()
                return false
            }
            operationsSinceCheckpoint = 0
            lastCheckpointAt = now
        }
        return true
    }

    @Synchronized
    private fun pauseCurrentTask(
        state: DeleteTaskState,
        message: String,
        captchaRequired: Boolean = false,
        notify: Boolean = true
    ): Boolean {
        val task = currentTask ?: return false
        val paused = task.copy(
            state = state,
            statusMessage = message,
            captchaRequired = captchaRequired
        )
        currentTask = paused
        if (!deleteTaskStore.save(paused)) {
            handleTaskPersistenceFailure()
            return false
        }
        if (notify) {
            val title = when (state) {
                DeleteTaskState.CAPTCHA_REQUIRED -> "캡챠 해결이 필요합니다"
                DeleteTaskState.SERVICE_TIMEOUT -> "백그라운드 작업 시간이 만료되었습니다"
                DeleteTaskState.NETWORK_ERROR -> "네트워크 오류로 삭제가 중단되었습니다"
                else -> "삭제 작업이 중단되었습니다"
            }
            notifier.notify(title, message)
        }
        return true
    }

    private fun handleTaskPersistenceFailure(
        message: String = "삭제 작업 상태를 저장하지 못해 안전을 위해 작업을 중단했습니다."
    ) {
        currentTask = currentTask?.copy(
            state = DeleteTaskState.INTERRUPTED,
            statusMessage = message,
            captchaRequired = false
        )
        _errorMessage.value = message
        _isDeleting.value = false
        _captchaFlag.value = false
        _showCaptchaDialog.value = false
        synchronized(deleteJobLock) {
            val jobToStop = deleteJob
            jobToStop?.cancel()
            pendingDeleteTask = null
        }
    }

    private fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _deleteLog.update { it + "[$timestamp] $message" }
        scope.launch { logSink.addLog("Delete", message) }
    }

    private fun addGalleryProgressLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _deleteLog.update { it + "[$timestamp] $DELETE_PROGRESS_LOG_MARKER$message" }
        scope.launch { logSink.addLog("Delete", message) }
    }

    private fun updateGalleryProgressLog(message: String, finished: Boolean = false) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val marker = if (finished) "" else DELETE_PROGRESS_LOG_MARKER
        val timestampedMessage = "[$timestamp] $marker$message"
        _deleteLog.update { it.replaceDeleteProgressLog(timestampedMessage) }
    }

    private fun updateDeleteProgressStatus(message: String, finished: Boolean = false) {
        updateGalleryProgressLog(message, finished)
        scope.launch { logSink.addLog("Delete", message) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun galleryProgressMessage(
        galleryName: String,
        itemLabel: String,
        deleted: Int,
        skipped: Int,
        total: Int?
    ): String = deleteGalleryProgressMessage(
        deleted = deleted,
        skipped = skipped,
        total = total
    )

    private fun startGalleryLog(galleryName: String, itemLabel: String, total: Int?) {
        addLogMessage("🚀 $galleryName $itemLabel 삭제 시작")
        addGalleryProgressLog(
            galleryProgressMessage(
                galleryName = galleryName,
                itemLabel = itemLabel,
                deleted = 0,
                skipped = 0,
                total = total
            )
        )
    }

    private fun completeGalleryLog(
        galleryName: String,
        itemLabel: String,
        deleted: Int,
        skipped: Int,
        total: Int?
    ) {
        val finalTotal = total ?: (deleted + skipped)
        updateGalleryProgressLog(
            galleryProgressMessage(galleryName, itemLabel, deleted, skipped, finalTotal),
            finished = true
        )
        val skippedText = if (skipped > 0) ", ${skipped}개 제외" else ""
        addLogMessage(
            "✅ $galleryName $itemLabel 삭제 완료 " +
                    "(총 ${deleted}개, 캡챠 해제 ${currentGalleryCaptchaSolved}개$skippedText)"
        )
    }

    private fun checkpointItemProgress(
        galleryDeleted: Int,
        gallerySkipped: Int,
        totalDeleted: Int,
        force: Boolean = false
    ): Boolean = updateTask(force = force) {
        it.copy(
            currentGalleryDeleted = galleryDeleted,
            currentGallerySkipped = gallerySkipped,
            currentGalleryCaptchaSolved = currentGalleryCaptchaSolved,
            totalDeleted = totalDeleted,
            queueCursor = DeleteQueueCheckpoint.advanceCursor(it.queueCursor, it.queueSize),
            state = DeleteTaskState.RUNNING,
            statusMessage = "${it.currentGalleryName} 처리 중"
        )
    }

    private fun resetCaptchaEstimateCycle() {
        captchaCycleDeletedCount = 0
        captchaTotalDeletionMillis = 0L
        captchaDeleteAttemptStartedAt = 0L
        _nextCaptchaEstimatedTimeLeft.value = 0L
    }

    private fun markCaptchaDeletionAttemptStarted() {
        if (_isTwoCaptchaConfigured.value) return
        captchaDeleteAttemptStartedAt = System.currentTimeMillis()
    }

    private fun recordSuccessfulDeletionForCaptchaEstimate() {
        if (_isTwoCaptchaConfigured.value) return
        val requestMillis = if (captchaDeleteAttemptStartedAt > 0L) {
            (System.currentTimeMillis() - captchaDeleteAttemptStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        captchaTotalDeletionMillis += requestMillis + timing.postRequestDelayMillis
        captchaDeleteAttemptStartedAt = 0L
        captchaCycleDeletedCount++
        val remaining = (CAPTCHA_DELETE_INTERVAL - captchaCycleDeletedCount).coerceAtLeast(0)
        val averageMillis = captchaTotalDeletionMillis / captchaCycleDeletedCount.coerceAtLeast(1)
        _nextCaptchaEstimatedTimeLeft.value = if (remaining == 0) -1L else averageMillis * remaining / 1000L
    }

    private fun updateCurrentGalleryEstimatedTimeLeft(
        deletedCount: Int,
        skippedCount: Int,
        total: Int
    ) {
        updateDeletionSpeed()
        _currentGalleryEstimatedTimeLeft.value = DeleteTimeEstimator.estimateRemainingSeconds(
            deletionsPerHour = _currentDeletionSpeedPerHour.value,
            deletedCount = deletedCount,
            skippedCount = skippedCount,
            totalCount = total
        )
    }

    private fun updateDeletionSpeed() {
        _currentDeletionSpeedPerHour.value = DeleteTimeEstimator.estimateHourlyDeletionRate(
            elapsedMillis = System.currentTimeMillis() - deletionSpeedStartedAt,
            initialDeletedCount = 0,
            deletedCount = deletionSpeedDeletedCount,
            skippedCount = deletionSpeedSkippedCount
        )
    }

    private fun isCaptchaError(message: String): Boolean =
        message.contains("captcha", ignoreCase = true) ||
                message.contains("g-recaptcha", ignoreCase = true) ||
                message.contains("recaptcha", ignoreCase = true)

    private suspend fun performDeletion(initialTask: DeleteTaskProgress) {
        val cleaner = this.cleaner ?: return
        val selectedGalleries = initialTask.selectedGalleries
        val deleteType = initialTask.deleteType
        val galleryMap = initialTask.galleryMap

        if (deleteType == "posting" &&
            initialTask.postContentFilterEnabled &&
            postContentRegex.isBlank()
        ) {
            pauseCurrentTask(
                DeleteTaskState.INTERRUPTED,
                "글 내용 필터가 비어 있어 삭제 작업을 시작하지 않았습니다."
            )
            _isDeleting.value = false
            return
        }

        if (deleteType == "comment" &&
            initialTask.commentContentFilterEnabled &&
            commentRegexFilter.isBlank()
        ) {
            pauseCurrentTask(
                DeleteTaskState.INTERRUPTED,
                "댓글 내용 필터가 비어 있어 삭제 작업을 시작하지 않았습니다."
            )
            _isDeleting.value = false
            return
        }

        val totalGalleries = selectedGalleries.size
        var completedGalleries = initialTask.completedGalleries
        var totalDeleted = initialTask.totalDeleted
        _totalCount.value = totalGalleries
        _deletedCount.value = completedGalleries
        _progress.value = if (totalGalleries > 0) completedGalleries.toFloat() / totalGalleries else 0f
        if (initialTask.deleteNewestFirst) {
            performNewestFirstDeletion(
                initialTask = initialTask,
                totalGalleries = totalGalleries,
                initialCompletedGalleries = completedGalleries,
                initialTotalDeleted = totalDeleted
            )
            return
        }

        for (index in initialTask.currentGalleryIndex until selectedGalleries.size) {
            currentCoroutineContext().ensureActive()
            val gno = selectedGalleries[index]
            val galleryName = galleryMap[gno] ?: "갤러리 #${index + 1}"
            val savedTask = currentTask ?: return
            val itemLabel = if (deleteType == "posting") "글" else "댓글"
            val resumingGallery = savedTask.currentGalleryIndex == index
            currentGalleryCaptchaSolved =
                if (resumingGallery) savedTask.currentGalleryCaptchaSolved else 0
            startGalleryLog(galleryName, itemLabel, total = null)
            val canRestoreQueue = DeleteQueueCheckpoint.canRestoreQueue(
                hasPersistedQueue = savedTask.hasPersistedQueue,
                queueGalleryIndex = savedTask.queueGalleryIndex,
                currentGalleryIndex = index,
                hasQueueFile = deleteTaskStore.hasQueue(savedTask.id)
            )
            _currentGalleryEstimatedTimeLeft.value = 0L
            if (!updateTask(force = true) {
                    it.copy(
                        currentGalleryIndex = index,
                        currentGalleryName = galleryName,
                        completedGalleries = completedGalleries,
                        currentGalleryDeleted = if (canRestoreQueue) it.currentGalleryDeleted else 0,
                        currentGallerySkipped = if (canRestoreQueue) it.currentGallerySkipped else 0,
                        currentGalleryCaptchaSolved = currentGalleryCaptchaSolved,
                        totalDeleted = totalDeleted,
                        state = DeleteTaskState.RUNNING,
                        statusMessage = if (canRestoreQueue) "$galleryName 저장 큐 복원 중" else "$galleryName 수집 중",
                        captchaRequired = false
                    )
                }) return

            val postCount: Int
            var galleryDeleted: Int
            var gallerySkipped: Int

            if (canRestoreQueue) {
                val taskWithQueue = currentTask ?: return
                val remainingQueue = deleteTaskStore.loadRemainingQueue(taskWithQueue.id, taskWithQueue.queueCursor)
                val queueIsConsistent = remainingQueue != null &&
                        taskWithQueue.queueCursor in 0..taskWithQueue.queueSize &&
                        remainingQueue.size + taskWithQueue.queueCursor == taskWithQueue.queueSize
                if (!queueIsConsistent) {
                    pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 저장 큐를 읽지 못했습니다. 작업 기록을 확인해 주세요.")
                    _isDeleting.value = false
                    return
                }
                cleaner.importCollectedPosts(remainingQueue)
                postCount = taskWithQueue.queueSize
                galleryDeleted = taskWithQueue.currentGalleryDeleted
                gallerySkipped = taskWithQueue.currentGallerySkipped
                updateGalleryProgressLog(
                    galleryProgressMessage(
                        galleryName,
                        itemLabel,
                        galleryDeleted,
                        gallerySkipped,
                        postCount
                    )
                )
            } else {
                val taskId = currentTask?.id ?: return
                val resumableCollection = savedTask.queueGalleryIndex == index &&
                        savedTask.collectionTotalPages > 0 &&
                        savedTask.collectionNextPage >= 0
                cleaner.clearPostData()
                deleteTaskStore.deleteQueue(taskId)
                val totalPages: Int
                var nextPage: Int
                if (resumableCollection) {
                    totalPages = savedTask.collectionTotalPages
                    nextPage = deleteTaskStore.findNextMissingCollectedPage(taskId, totalPages)
                    updateGalleryProgressLog(
                        "📋 $galleryName $itemLabel 목록 수집 중: ${totalPages - nextPage}/$totalPages 페이지"
                    )
                } else {
                    if (!deleteTaskStore.deleteCollectedPages(taskId)) {
                        pauseCurrentTask(
                            DeleteTaskState.INTERRUPTED,
                            "$galleryName 이전 페이지 수집 데이터를 정리하지 못했습니다. 저장 공간을 확인한 뒤 이어서 진행해 주세요."
                        )
                        _isDeleting.value = false
                        return
                    }
                    totalPages = cleaner.getPageCount(gno, deleteType)
                    if (totalPages <= 0) {
                        pauseCurrentTask(DeleteTaskState.NETWORK_ERROR, "$galleryName 페이지 수를 불러오지 못했습니다. 네트워크 상태를 확인해 주세요.")
                        _isDeleting.value = false
                        return
                    }
                    nextPage = totalPages
                    if (!updateTask(force = true) {
                            it.copy(
                                queueGalleryIndex = index,
                                queueSize = 0,
                                queueCursor = 0,
                                hasPersistedQueue = false,
                                collectionTotalPages = totalPages,
                                collectionNextPage = nextPage,
                                currentGalleryDeleted = 0,
                                currentGallerySkipped = 0
                            )
                        }) return
                }

                val canUseInMemoryCollectionFallback = !resumableCollection
                val inMemoryCollectedQueue = mutableListOf<CollectedPost>()
                while (nextPage >= 1 && currentCoroutineContext().isActive) {
                    val currentPage = totalPages - nextPage + 1
                    updateGalleryProgressLog(
                        "📋 $galleryName $itemLabel 목록 수집 중: $currentPage/$totalPages 페이지"
                    )
                    delay(timing.pageRequestDelayMillis)
                    when (val pageResult = cleaner.getPostList(gno, deleteType, nextPage)) {
                        is PostListResult.Success -> {
                            pageResult.requireRequestedGallery()
                            val pagePosts = cleaner.exportCollectedPosts(pageResult.posts)
                            if (canUseInMemoryCollectionFallback) {
                                inMemoryCollectedQueue.addAll(pagePosts)
                            }
                            if (!deleteTaskStore.saveCollectedPage(taskId, nextPage, pagePosts)) {
                                pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName ${nextPage}페이지 수집 결과를 저장하지 못했습니다.")
                                _isDeleting.value = false
                                return
                            }
                            nextPage--
                            if (!updateTask(force = true) {
                                    it.copy(
                                        collectionTotalPages = totalPages,
                                        collectionNextPage = nextPage,
                                        statusMessage = "$galleryName 수집 중 ($currentPage/$totalPages 페이지 저장)"
                                    )
                                }) return
                        }
                        is PostListResult.Blocked, PostListResult.Failed -> {
                            pauseCurrentTask(DeleteTaskState.NETWORK_ERROR, "$galleryName ${nextPage}페이지 수집이 중단되었습니다. 이어하기 시 이 페이지부터 재개합니다.")
                            _isDeleting.value = false
                            return
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                val collectedQueue = deleteTaskStore.loadCollectedPages(taskId, totalPages)
                    ?: if (canUseInMemoryCollectionFallback) {
                        logSink.addLog(
                            "Delete",
                            "$galleryName 페이지별 수집 데이터 병합 실패, 현재 실행 중 수집 데이터로 복구: ${inMemoryCollectedQueue.size}개"
                        )
                        inMemoryCollectedQueue
                    } else {
                        null
                    }
                if (collectedQueue == null) {
                    pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 페이지별 수집 데이터를 합치지 못했습니다.")
                    _isDeleting.value = false
                    return
                }
                if (!deleteTaskStore.saveQueue(taskId, collectedQueue)) {
                    pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 수집 결과를 저장하지 못해 작업을 중단했습니다.")
                    _isDeleting.value = false
                    return
                }
                postCount = collectedQueue.size
                galleryDeleted = 0
                gallerySkipped = 0
                cleaner.importCollectedPosts(collectedQueue)
                if (!updateTask(force = true) {
                        it.copy(
                            queueGalleryIndex = index,
                            queueSize = postCount,
                            queueCursor = 0,
                            hasPersistedQueue = true,
                            collectionTotalPages = 0,
                            collectionNextPage = -1,
                            statusMessage = "$galleryName 삭제 큐 저장 완료 ($postCount 개)"
                        )
                    }) return
                deleteTaskStore.deleteCollectedPages(taskId)
            }

            if (postCount > 0) {
                updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
            }

            if (cleaner.getPostListSize() == 0) {
                completeGalleryLog(
                    galleryName,
                    itemLabel,
                    galleryDeleted,
                    gallerySkipped,
                    postCount
                )
                completedGalleries++
                _progress.value = completedGalleries.toFloat() / totalGalleries.toFloat()
                _currentGallery.value = galleryName
                _deletedCount.value = completedGalleries
                if (!updateTask(force = true) {
                        it.copy(
                            currentGalleryIndex = index + 1,
                            completedGalleries = completedGalleries,
                            currentGalleryDeleted = 0,
                            currentGallerySkipped = 0,
                            currentGalleryCaptchaSolved = 0,
                            totalDeleted = totalDeleted,
                            queueGalleryIndex = -1,
                            queueSize = 0,
                            queueCursor = 0,
                            hasPersistedQueue = false,
                            collectionTotalPages = 0,
                            collectionNextPage = -1,
                            statusMessage = "$galleryName 처리 완료"
                        )
                    }) return
                currentTask?.id?.let(deleteTaskStore::deleteQueue)
                continue
            }

            updateGalleryProgressLog(
                galleryProgressMessage(
                    galleryName,
                    itemLabel,
                    galleryDeleted,
                    gallerySkipped,
                    postCount
                )
            )
            while (cleaner.getPostListSize() > 0 && currentCoroutineContext().isActive) {
                val postNo = cleaner.getFirstPost() ?: break
                if (minPostAgeDaysToDelete >= 0) {
                    val ageDays = cleaner.getPostAgeDays(postNo)
                    val shouldDeleteByDate = ageDays != null && ageDays >= minPostAgeDaysToDelete
                    if (!shouldDeleteByDate) {
                        gallerySkipped++
                        deletionSpeedSkippedCount++
                        logSink.addLog("Service", "날짜 필터 불일치 - 건너뛰기 - postNo: $postNo, ageDays: ${ageDays ?: "unknown"}, minDays: $minPostAgeDaysToDelete")
                        cleaner.removeFirstPost()
                        if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted)) return
                        updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                        updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                        updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                        continue
                    }
                }

                val regexFilterMismatch =
                    (deleteType == "posting" && initialTask.postContentFilterEnabled &&
                        !matchesTextRegex(cleaner.getPostText(postNo), postContentRegex)) ||
                        (deleteType == "comment" && initialTask.commentContentFilterEnabled &&
                            !matchesTextRegex(cleaner.getPostText(postNo), commentRegexFilter))
                if (regexFilterMismatch) {
                    gallerySkipped++
                    deletionSpeedSkippedCount++
                    logSink.addLog("Service", "정규식 필터 불일치 - 건너뛰기 - postNo: $postNo")
                    cleaner.removeFirstPost()
                    if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted)) return
                    updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                    updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                    updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                    continue
                }

                if (deleteType == "posting" &&
                    (minRecommendToKeep >= 0 || minCommentToKeep >= 0 || minViewToKeep >= 0)
                ) {
                    val postUrl = cleaner.getPostUrl(postNo)
                    val postDetails = if (postUrl == null) null else {
                        delay(timing.postRequestDelayMillis)
                        logSink.addLog("Service", "글 상세 정보 확인 중 - postNo: $postNo")
                        cleaner.getPostDetails(postUrl)
                    }
                    if (postDetails == null || !postDetails.hasCountsRequiredBy(
                            initialTask.recommendFilterEnabled,
                            initialTask.commentFilterEnabled,
                            initialTask.viewFilterEnabled
                        )
                    ) {
                        gallerySkipped++
                        deletionSpeedSkippedCount++
                        cleaner.removeFirstPost()
                        if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted)) return
                        updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                        updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                        updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                        continue
                    }
                    val shouldSkip =
                        (minRecommendToKeep >= 0 && postDetails.recommendCount?.let { it >= minRecommendToKeep } == true) ||
                                (minCommentToKeep >= 0 && postDetails.commentCount?.let { it >= minCommentToKeep } == true) ||
                                (minViewToKeep >= 0 && postDetails.viewCount?.let { it >= minViewToKeep } == true)
                    if (shouldSkip) {
                        gallerySkipped++
                        deletionSpeedSkippedCount++
                        cleaner.removeFirstPost()
                        if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted)) return
                        updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                        updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                        updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                        continue
                    }
                }

                if (deleteType == "comment" && (myPostFilterEnabled || dcconOnlyFilterEnabled)) {
                    val matches = matchesCommentTypeFilter(cleaner, postNo)
                    if (!matches) {
                        gallerySkipped++
                        deletionSpeedSkippedCount++
                        logSink.addLog("Service", "필터 불일치 - 건너뛰기 - postNo: $postNo")
                        cleaner.removeFirstPost()
                        if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted)) return
                        updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                        updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                        updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                        continue
                    }
                }

                logSink.addLog("Service", "글 삭제 시도 - postNo: $postNo (일반)")
                markCaptchaDeletionAttemptStarted()
                var deleteResult = cleaner.deletePost(
                    postNo,
                    deleteType,
                    solveCaptcha = false,
                    deleteQuestionPosts = initialTask.deleteQuestionPosts
                )
                if (deleteResult is DeleteResult.Error && isCaptchaError(deleteResult.message)) {
                    val captchaResult = handleCaptcha(
                        cleaner,
                        postNo,
                        deleteType,
                        galleryName,
                        postCount,
                        initialTask.deleteQuestionPosts
                    )
                    if (captchaResult == null) return
                    deleteResult = captchaResult.result
                    if (deleteResult is DeleteResult.Success) {
                        currentGalleryCaptchaSolved++
                        galleryDeleted++
                        totalDeleted++
                        deletionSpeedDeletedCount++
                        if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted, force = true)) return
                        cleaner.removeFirstPost()
                        recordSuccessfulDeletionForCaptchaEstimate()
                        updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                        updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                        updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                        delay(timing.captchaSettleDelayMillis)
                        continue
                    }
                }

                if (deleteResult is DeleteResult.Success) {
                    galleryDeleted++
                    totalDeleted++
                    deletionSpeedDeletedCount++
                    if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted, force = true)) return
                    cleaner.removeFirstPost()
                    recordSuccessfulDeletionForCaptchaEstimate()
                    updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                    updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                    updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                } else {
                    val failureReason = when (deleteResult) {
                        is DeleteResult.Failed -> "네트워크 오류"
                        is DeleteResult.Blocked -> "접속 차단"
                        is DeleteResult.Error -> "삭제 오류: ${deleteResult.message.take(100)}"
                        is DeleteResult.Excluded -> "삭제 확인 필요: ${deleteResult.message.take(100)}"
                        is DeleteResult.Success -> "알 수 없는 오류"
                    }
                    gallerySkipped++
                    deletionSpeedSkippedCount++
                    logSink.addLog(
                        "Delete",
                        if (deleteResult is DeleteResult.Excluded) {
                            "$galleryName: $postNo 삭제 제외됨 ($failureReason)"
                        } else {
                            "$galleryName: $postNo 삭제 실패로 건너뜀 ($failureReason)"
                        }
                    )
                    captchaDeleteAttemptStartedAt = 0L
                    if (!checkpointItemProgress(galleryDeleted, gallerySkipped, totalDeleted, force = true)) return
                    cleaner.removeFirstPost()
                    updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, postCount, galleryName)
                    updateGalleryProgressLog(galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount))
                    updateCurrentGalleryEstimatedTimeLeft(galleryDeleted, gallerySkipped, postCount)
                }

                delay(timing.postRequestDelayMillis)
            }
            currentCoroutineContext().ensureActive()

            completeGalleryLog(galleryName, itemLabel, galleryDeleted, gallerySkipped, postCount)
            completedGalleries++
            _progress.value = completedGalleries.toFloat() / totalGalleries.toFloat()
            _currentGallery.value = galleryName
            _deletedCount.value = completedGalleries
            _currentGalleryEstimatedTimeLeft.value = 0L
            if (!updateTask(force = true) {
                    it.copy(
                        currentGalleryIndex = index + 1,
                        completedGalleries = completedGalleries,
                        currentGalleryDeleted = 0,
                        currentGallerySkipped = 0,
                        currentGalleryCaptchaSolved = 0,
                        totalDeleted = totalDeleted,
                        queueGalleryIndex = -1,
                        queueSize = 0,
                        queueCursor = 0,
                        hasPersistedQueue = false,
                        collectionTotalPages = 0,
                        collectionNextPage = -1,
                        statusMessage = "$galleryName 처리 완료"
                    )
                }) return
            currentTask?.id?.let(deleteTaskStore::deleteQueue)
        }

        completeDeletion(initialTask, totalDeleted, completedGalleries, totalGalleries)
    }

    private suspend fun performNewestFirstDeletion(
        initialTask: DeleteTaskProgress,
        totalGalleries: Int,
        initialCompletedGalleries: Int,
        initialTotalDeleted: Int
    ) {
        val cleaner = cleaner ?: return
        var completedGalleries = initialCompletedGalleries
        var totalDeleted = initialTotalDeleted

        for (index in initialTask.currentGalleryIndex until initialTask.selectedGalleries.size) {
            currentCoroutineContext().ensureActive()
            val gno = initialTask.selectedGalleries[index]
            val galleryName = initialTask.galleryMap[gno] ?: "갤러리 #${index + 1}"
            val savedTask = currentTask ?: return
            val itemLabel = if (initialTask.deleteType == "posting") "글" else "댓글"
            val resumingGallery = savedTask.currentGalleryIndex == index
            var galleryDeleted = if (resumingGallery) savedTask.currentGalleryDeleted else 0
            var gallerySkipped = if (resumingGallery) savedTask.currentGallerySkipped else 0
            var page = if (resumingGallery) savedTask.newestFirstPage.coerceAtLeast(1) else 1
            // Gson restores a newly added missing Int field as 0 for old checkpoints.
            // A resumable gallery necessarily had at least one item, so only positive totals are reusable.
            var galleryTotal = savedTask.newestFirstTotalCount.takeIf { resumingGallery && it > 0 }
            val terminalPosts = mutableSetOf<String>()
            currentGalleryCaptchaSolved =
                if (resumingGallery) savedTask.currentGalleryCaptchaSolved else 0

            _currentGalleryEstimatedTimeLeft.value = if (galleryTotal == null) -1L else 0L
            _currentGallery.value = galleryName
            startGalleryLog(galleryName, itemLabel, total = galleryTotal)
            updateGalleryProgressLog(
                galleryProgressMessage(galleryName, itemLabel, galleryDeleted, gallerySkipped, total = galleryTotal)
            )
            if (!updateTask(force = true) {
                    it.copy(
                        currentGalleryIndex = index,
                        currentGalleryName = galleryName,
                        completedGalleries = completedGalleries,
                        currentGalleryDeleted = galleryDeleted,
                        currentGallerySkipped = gallerySkipped,
                        currentGalleryCaptchaSolved = currentGalleryCaptchaSolved,
                        totalDeleted = totalDeleted,
                        state = DeleteTaskState.RUNNING,
                        statusMessage = "$galleryName 최근 ${itemLabel}부터 삭제 중",
                        captchaRequired = false,
                        newestFirstPage = page,
                        newestFirstTotalCount = galleryTotal ?: -1
                    )
                }) return
            galleryTotal?.takeIf { it > 0 }?.let { total ->
                updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, total, galleryName)
            }

            var galleryFinished = false
            while (!galleryFinished && currentCoroutineContext().isActive) {
                val task = currentTask ?: return
                val canRestoreQueue = DeleteQueueCheckpoint.canRestoreQueue(
                    hasPersistedQueue = task.hasPersistedQueue,
                    queueGalleryIndex = task.queueGalleryIndex,
                    currentGalleryIndex = index,
                    hasQueueFile = deleteTaskStore.hasQueue(task.id)
                )

                val batchSize: Int
                val batchStartDeleted: Int
                if (canRestoreQueue) {
                    val remainingQueue = deleteTaskStore.loadRemainingQueue(task.id, task.queueCursor)
                    val queueIsConsistent = remainingQueue != null &&
                            task.queueCursor in 0..task.queueSize &&
                            remainingQueue.size + task.queueCursor == task.queueSize
                    if (!queueIsConsistent) {
                        pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 저장 큐를 읽지 못했습니다. 작업 기록을 확인해 주세요.")
                        _isDeleting.value = false
                        return
                    }
                    cleaner.importCollectedPosts(remainingQueue)
                    batchSize = task.queueSize
                    batchStartDeleted = task.newestFirstBatchStartDeleted
                    page = task.newestFirstPage.coerceAtLeast(1)
                } else {
                    cleaner.clearPostData()
                    if (!deleteTaskStore.deleteQueue(task.id)) {
                        pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 이전 삭제 큐를 정리하지 못했습니다.")
                        _isDeleting.value = false
                        return
                    }
                    delay(timing.pageRequestDelayMillis)
                    val pageResult = cleaner.getPostList(gno, initialTask.deleteType, page)
                    val pagePosts = when (pageResult) {
                        is PostListResult.Success -> {
                            pageResult.requireRequestedGallery()
                            if (galleryTotal == null && pageResult.totalCount != null) {
                                // On an old resumed task the DOM count excludes already deleted items,
                                // while skipped items are still present in the gallog list.
                                val discoveredTotal = pageResult.totalCount + galleryDeleted
                                galleryTotal = discoveredTotal
                                if (!updateTask(force = true) {
                                        it.copy(newestFirstTotalCount = discoveredTotal)
                                    }) return
                                updateGalleryProgressLog(
                                    galleryProgressMessage(
                                        galleryName,
                                        itemLabel,
                                        galleryDeleted,
                                        gallerySkipped,
                                        galleryTotal
                                    )
                                )
                                if (discoveredTotal > 0) {
                                    updateGalleryItemProgress(completedGalleries, galleryDeleted, gallerySkipped, totalGalleries, discoveredTotal, galleryName)
                                }
                            }
                            pageResult.posts
                        }
                        is PostListResult.Blocked, PostListResult.Failed -> {
                            pauseCurrentTask(
                                DeleteTaskState.NETWORK_ERROR,
                                "$galleryName ${page}페이지를 불러오지 못했습니다. 이어하기 시 이 페이지부터 재개합니다."
                            )
                            _isDeleting.value = false
                            return
                        }
                    }
                    if (pagePosts.isEmpty()) {
                        galleryFinished = true
                        continue
                    }

                    val unprocessedPosts = pagePosts.asReversed().filterNot(terminalPosts::contains)
                    if (unprocessedPosts.isEmpty()) {
                        delay(timing.pageRequestDelayMillis)
                        val totalPages = cleaner.getPageCount(gno, initialTask.deleteType)
                        if (totalPages <= 0) {
                            pauseCurrentTask(
                                DeleteTaskState.NETWORK_ERROR,
                                "$galleryName 페이지 수를 불러오지 못했습니다. 이어하기 시 ${page}페이지부터 재개합니다."
                            )
                            _isDeleting.value = false
                            return
                        }
                        if (page >= totalPages) {
                            galleryFinished = true
                            continue
                        }
                        page++
                        terminalPosts.clear()
                        if (!updateTask(force = true) {
                                it.copy(
                                    newestFirstPage = page,
                                    statusMessage = "$galleryName 다음 페이지 확인 중"
                                )
                            }) return
                        continue
                    }

                    val collectedBatch = cleaner.exportCollectedPosts(unprocessedPosts)
                    if (!deleteTaskStore.saveQueue(task.id, collectedBatch)) {
                        pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName ${page}페이지 삭제 큐를 저장하지 못했습니다.")
                        _isDeleting.value = false
                        return
                    }
                    batchSize = collectedBatch.size
                    batchStartDeleted = galleryDeleted
                    cleaner.importCollectedPosts(collectedBatch)
                    if (!updateTask(force = true) {
                            it.copy(
                                queueGalleryIndex = index,
                                queueSize = batchSize,
                                queueCursor = 0,
                                hasPersistedQueue = true,
                                newestFirstPage = page,
                                newestFirstBatchStartDeleted = batchStartDeleted,
                                currentGalleryDeleted = galleryDeleted,
                                currentGallerySkipped = gallerySkipped,
                                statusMessage = "$galleryName ${page}페이지 삭제 중"
                            )
                        }) return
                }

                val result = processNewestFirstBatch(
                    cleaner = cleaner,
                    task = initialTask,
                    galleryName = galleryName,
                    batchSize = batchSize,
                    galleryDeleted = galleryDeleted,
                    gallerySkipped = gallerySkipped,
                    totalDeleted = totalDeleted,
                    terminalPosts = terminalPosts,
                    galleryTotal = galleryTotal,
                    completedGalleries = completedGalleries,
                    totalGalleries = totalGalleries
                ) ?: return
                galleryDeleted = result.galleryDeleted
                gallerySkipped = result.gallerySkipped
                totalDeleted = result.totalDeleted
                currentCoroutineContext().ensureActive()

                val deletedInBatch = galleryDeleted > batchStartDeleted
                if (!deleteTaskStore.deleteQueue(task.id)) {
                    pauseCurrentTask(DeleteTaskState.INTERRUPTED, "$galleryName 처리한 삭제 큐를 정리하지 못했습니다.")
                    _isDeleting.value = false
                    return
                }
                if (!deletedInBatch) {
                    page++
                    terminalPosts.clear()
                }
                if (!updateTask(force = true) {
                        it.copy(
                            queueSize = 0,
                            queueCursor = 0,
                            hasPersistedQueue = false,
                            newestFirstPage = page,
                            newestFirstBatchStartDeleted = galleryDeleted,
                            currentGalleryDeleted = galleryDeleted,
                            currentGallerySkipped = gallerySkipped,
                            totalDeleted = totalDeleted,
                            statusMessage = "$galleryName ${page}페이지 확인 중"
                        )
                    }) return
            }
            currentCoroutineContext().ensureActive()

            completeGalleryLog(
                galleryName,
                itemLabel,
                galleryDeleted,
                gallerySkipped,
                total = galleryTotal
            )
            completedGalleries++
            _progress.value = completedGalleries.toFloat() / totalGalleries.toFloat()
            _currentGallery.value = galleryName
            _deletedCount.value = completedGalleries
            if (!updateTask(force = true) {
                    it.copy(
                        currentGalleryIndex = index + 1,
                        completedGalleries = completedGalleries,
                        currentGalleryDeleted = 0,
                        currentGallerySkipped = 0,
                        currentGalleryCaptchaSolved = 0,
                        totalDeleted = totalDeleted,
                        queueGalleryIndex = -1,
                        queueSize = 0,
                        queueCursor = 0,
                        hasPersistedQueue = false,
                        newestFirstPage = 1,
                        newestFirstBatchStartDeleted = 0,
                        newestFirstTotalCount = -1,
                        statusMessage = "$galleryName 처리 완료"
                    )
                }) return
            currentTask?.id?.let(deleteTaskStore::deleteQueue)
        }

        completeDeletion(initialTask, totalDeleted, completedGalleries, totalGalleries)
    }

    private suspend fun processNewestFirstBatch(
        cleaner: CleanerPort,
        task: DeleteTaskProgress,
        galleryName: String,
        batchSize: Int,
        galleryDeleted: Int,
        gallerySkipped: Int,
        totalDeleted: Int,
        terminalPosts: MutableSet<String>,
        galleryTotal: Int?,
        completedGalleries: Int,
        totalGalleries: Int
    ): NewestFirstBatchResult? {
        var deleted = galleryDeleted
        var skipped = gallerySkipped
        var deletedTotal = totalDeleted
        val itemLabel = if (task.deleteType == "posting") "글" else "댓글"
        fun updateProgress() {
            updateGalleryProgressLog(
                galleryProgressMessage(galleryName, itemLabel, deleted, skipped, galleryTotal)
            )
            galleryTotal?.let { total ->
                updateCurrentGalleryEstimatedTimeLeft(deleted, skipped, total)
                if (total > 0) updateGalleryItemProgress(completedGalleries, deleted, skipped, totalGalleries, total, galleryName)
            }
        }

        while (cleaner.getPostListSize() > 0 && currentCoroutineContext().isActive) {
            val postNo = cleaner.getFirstPost() ?: break
            if (minPostAgeDaysToDelete >= 0) {
                val ageDays = cleaner.getPostAgeDays(postNo)
                if (ageDays == null || ageDays < minPostAgeDaysToDelete) {
                    skipped++
                    deletionSpeedSkippedCount++
                    terminalPosts += postNo
                    cleaner.removeFirstPost()
                    if (!checkpointItemProgress(deleted, skipped, deletedTotal)) return null
                    updateProgress()
                    continue
                }
            }

            val regexFilterMismatch =
                (task.deleteType == "posting" && task.postContentFilterEnabled &&
                    !matchesTextRegex(cleaner.getPostText(postNo), postContentRegex)) ||
                    (task.deleteType == "comment" && task.commentContentFilterEnabled &&
                        !matchesTextRegex(cleaner.getPostText(postNo), commentRegexFilter))
            if (regexFilterMismatch) {
                skipped++
                deletionSpeedSkippedCount++
                terminalPosts += postNo
                cleaner.removeFirstPost()
                if (!checkpointItemProgress(deleted, skipped, deletedTotal)) return null
                updateProgress()
                continue
            }

            if (task.deleteType == "posting" &&
                (minRecommendToKeep >= 0 || minCommentToKeep >= 0 || minViewToKeep >= 0)
            ) {
                val postUrl = cleaner.getPostUrl(postNo)
                val postDetails = if (postUrl == null) null else {
                    delay(timing.postRequestDelayMillis)
                    cleaner.getPostDetails(postUrl)
                }
                if (postDetails == null || !postDetails.hasCountsRequiredBy(
                        task.recommendFilterEnabled,
                        task.commentFilterEnabled,
                        task.viewFilterEnabled
                    )
                ) {
                    skipped++
                    deletionSpeedSkippedCount++
                    terminalPosts += postNo
                    cleaner.removeFirstPost()
                    if (!checkpointItemProgress(deleted, skipped, deletedTotal)) return null
                    updateProgress()
                    continue
                }
                val shouldKeep =
                    (minRecommendToKeep >= 0 && postDetails.recommendCount?.let { it >= minRecommendToKeep } == true) ||
                            (minCommentToKeep >= 0 && postDetails.commentCount?.let { it >= minCommentToKeep } == true) ||
                            (minViewToKeep >= 0 && postDetails.viewCount?.let { it >= minViewToKeep } == true)
                if (shouldKeep) {
                    skipped++
                    deletionSpeedSkippedCount++
                    terminalPosts += postNo
                    cleaner.removeFirstPost()
                    if (!checkpointItemProgress(deleted, skipped, deletedTotal)) return null
                    updateProgress()
                    continue
                }
            }

            if (task.deleteType == "comment" && (myPostFilterEnabled || dcconOnlyFilterEnabled)) {
                if (!matchesCommentTypeFilter(cleaner, postNo)) {
                    skipped++
                    deletionSpeedSkippedCount++
                    terminalPosts += postNo
                    cleaner.removeFirstPost()
                    if (!checkpointItemProgress(deleted, skipped, deletedTotal)) return null
                    updateProgress()
                    continue
                }
            }

            markCaptchaDeletionAttemptStarted()
            var deleteResult = cleaner.deletePost(
                postNo,
                task.deleteType,
                solveCaptcha = false,
                deleteQuestionPosts = task.deleteQuestionPosts
            )
            if (deleteResult is DeleteResult.Error && isCaptchaError(deleteResult.message)) {
                val captchaResult = handleCaptcha(
                    cleaner,
                    postNo,
                    task.deleteType,
                    galleryName,
                    batchSize,
                    task.deleteQuestionPosts
                )
                    ?: return null
                deleteResult = captchaResult.result
                if (deleteResult is DeleteResult.Success) {
                    currentGalleryCaptchaSolved++
                    deleted++
                    deletedTotal++
                    deletionSpeedDeletedCount++
                    if (!checkpointItemProgress(deleted, skipped, deletedTotal, force = true)) return null
                    cleaner.removeFirstPost()
                    recordSuccessfulDeletionForCaptchaEstimate()
                    updateProgress()
                    delay(timing.captchaSettleDelayMillis)
                    continue
                }
            }

            if (deleteResult is DeleteResult.Success) {
                deleted++
                deletedTotal++
                deletionSpeedDeletedCount++
                if (!checkpointItemProgress(deleted, skipped, deletedTotal, force = true)) return null
                cleaner.removeFirstPost()
                recordSuccessfulDeletionForCaptchaEstimate()
                updateProgress()
            } else {
                skipped++
                deletionSpeedSkippedCount++
                terminalPosts += postNo
                logSink.addLog(
                    "Delete",
                    if (deleteResult is DeleteResult.Excluded) {
                        "$galleryName: $postNo 삭제 제외됨 (삭제 확인 필요: ${deleteResult.message.take(100)})"
                    } else {
                        "$galleryName: $postNo 삭제 실패로 건너뜀"
                    }
                )
                captchaDeleteAttemptStartedAt = 0L
                if (!checkpointItemProgress(deleted, skipped, deletedTotal, force = true)) return null
                cleaner.removeFirstPost()
                updateProgress()
            }
            delay(timing.postRequestDelayMillis)
        }
        currentCoroutineContext().ensureActive()
        return NewestFirstBatchResult(deleted, skipped, deletedTotal)
    }

    /** 작업 마무리. 이어서 할 단계가 있으면 같은 코루틴에서 바로 넘어가 삭제 중 상태(포그라운드 서비스)가 끊기지 않게 한다. */
    private suspend fun completeDeletion(
        initialTask: DeleteTaskProgress,
        totalDeleted: Int,
        completedGalleries: Int,
        totalGalleries: Int
    ) {
        val cleaner = cleaner ?: return
        val completedTask = currentTask
        val deletedPosts = initialTask.carriedDeletedPosts + if (initialTask.deleteType == "posting") totalDeleted else 0
        val deletedComments = initialTask.carriedDeletedComments + if (initialTask.deleteType == "comment") totalDeleted else 0
        val followUp = completedTask?.followUp
        if (followUp != null) {
            val job = currentCoroutineContext()[Job]
            currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            // 중단(기록 삭제)·일시정지·키 변경(updateTask)과 엇갈리면 멈춘 작업의 다음 단계가 남거나 끝난 기록이 되살아나서,
            // 같은 잠금 안에서 최신 기록을 확인하고 저장·교체한다.
            val next = synchronized(this) {
                val latest = currentTask
                // 사용자가 멈췄으면 그대로 끝내고, 일시정지됐으면 첫 단계 기록이 남아 이어하기 때 바로 다음 단계로 넘어간다.
                if (latest == null || latest.id != completedTask.id || latest.state != DeleteTaskState.RUNNING) return
                val next = followUp.copy(
                    loginId = latest.loginId,
                    twoCaptchaApiKey = latest.twoCaptchaApiKey,
                    recordGuestbookLog = latest.recordGuestbookLog,
                    carriedDeletedPosts = deletedPosts,
                    carriedDeletedComments = deletedComments,
                    state = DeleteTaskState.RUNNING,
                    statusMessage = "",
                    captchaRequired = false,
                    createdAt = now,
                    updatedAt = now
                )
                // 다음 단계를 먼저 저장하고 끝난 기록을 지워야 중간에 꺼져도 이어서 할 작업이 남는다.
                if (!deleteTaskStore.save(next)) {
                    handleTaskPersistenceFailure("다음 단계 삭제 작업을 저장하지 못했습니다.")
                    return
                }
                if (!deleteTaskStore.remove(latest.id)) {
                    handleTaskPersistenceFailure("완료된 삭제 작업 기록을 정리하지 못했습니다.")
                    return
                }
                currentTask = next
                // 키 등록과 엇갈려 옛 키로 되돌리지 않게 같은 잠금 안에서 적용한다.
                applyTaskSettings(next)
                operationsSinceCheckpoint = 0
                lastCheckpointAt = now
                next
            }
            // 멈춤 요청이 이미 삭제 중 표시를 껐으면 다시 켜지 않는다. 켜면 작업 없이 삭제 중으로 남는다.
            synchronized(deleteJobLock) {
                if (job?.isActive != true) return
                resetDeletionState(keepLog = true)
            }
            cleaner.clearPostData()
            _currentDeleteType.value = next.deleteType
            _itemCounts.value = com.dccleaner.app.model.DeleteItemCounts(next.totalDeleted, next.totalSkipped)
            _isTwoCaptchaConfigured.value = cleaner.has2CaptchaKey()
            addLogMessage(
                "✅ ${phaseLabel(initialTask.deleteType)} 삭제를 마쳤어요 (${totalDeleted}개) · " +
                    "이어서 ${phaseLabel(next.deleteType)} 삭제를 시작해요"
            )
            performDeletion(next)
            return
        }
        // 키 등록(useTwoCaptchaKey)이 사이에 끼어 끝난 기록을 다시 저장하지 않게 같은 잠금 안에서 지운다.
        val removed = synchronized(this) {
            (completedTask == null || deleteTaskStore.remove(completedTask.id)).also { if (it) currentTask = null }
        }
        if (!removed) {
            handleTaskPersistenceFailure("완료된 삭제 작업 기록을 정리하지 못했습니다.")
            return
        }
        cleaner.clearPostData()
        if (initialTask.recordGuestbookLog) {
            updateDeleteProgressStatus("📝 방명록 가동 기록 작성 중")
            val logged = try {
                cleaner.recordCleanerRunGuestbookLog(
                    deletedPosts,
                    deletedComments,
                    onProgress = { message ->
                        if (message.isGuestbookProblemLog()) addLogMessage(message)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLogMessage("⚠️ 방명록 가동 기록 작성 실패: ${e.message ?: "알 수 없는 오류"}")
                false
            }
            if (logged) {
                updateDeleteProgressStatus("✅ 방명록 가동 기록 작성 완료", finished = true)
            } else {
                updateDeleteProgressStatus("⚠️ 방명록 가동 기록 작성 실패", finished = true)
            }
        }
        // 앞 단계가 0개를 지웠으면 이어진 작업인지 알 수 없어 기존 문구로 보인다.
        val summary = if (initialTask.carriedDeletedPosts + initialTask.carriedDeletedComments > 0) {
            if (initialTask.deleteType == "posting") "댓글 ${deletedComments}개 · 글 ${deletedPosts}개 삭제"
            else "글 ${deletedPosts}개 · 댓글 ${deletedComments}개 삭제"
        } else {
            "총 $totalDeleted 개 글/댓글 삭제 ($completedGalleries/$totalGalleries 갤러리)"
        }
        _isCompleted.value = true
        _currentGalleryEstimatedTimeLeft.value = 0L
        speedUpdateJob?.cancel()
        _currentDeletionSpeedPerHour.value = 0
        notifier.notify("삭제 완료", summary)
        _isDeleting.value = false
        addLogMessage("🎉 모든 작업 완료! $summary")
    }

    private fun phaseLabel(deleteType: String): String = if (deleteType == "posting") "게시글" else "댓글"

    private data class NewestFirstBatchResult(
        val galleryDeleted: Int,
        val gallerySkipped: Int,
        val totalDeleted: Int
    )

    private suspend fun matchesCommentTypeFilter(
        cleaner: CleanerPort,
        postNo: String
    ): Boolean {
        if (dcconOnlyFilterEnabled && cleaner.isPostDccon(postNo)) return true
        if (myPostFilterEnabled) {
            val postUrl = cleaner.getPostUrl(postNo)
            if (postUrl != null) {
                delay(timing.postRequestDelayMillis)
                logSink.addLog("Service", "글 작성자 UID 확인 중 - postNo: $postNo")
                val writerUid = cleaner.getPostWriterUid(postUrl)
                return !writerUid.isNullOrEmpty() && writerUid == cleaner.getUserId()
            }
        }
        return false
    }

    private fun matchesTextRegex(text: String, regexText: String): Boolean {
        val regex = runCatching {
            val matchResult = Regex("^/(.+)/([a-zA-Z]*)$").find(regexText)
            if (matchResult != null) {
                val (pattern, flags) = matchResult.destructured
                val options = buildSet {
                    if ('i' in flags) add(RegexOption.IGNORE_CASE)
                    if ('m' in flags) add(RegexOption.MULTILINE)
                    if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
                }
                Regex(pattern, options)
            } else {
                Regex(regexText)
            }
        }.getOrNull()
        return regex?.containsMatchIn(text) == true
    }

    private suspend fun handleCaptcha(
        cleaner: CleanerPort,
        postNo: String,
        deleteType: String,
        galleryName: String,
        postCount: Int,
        deleteQuestionPosts: Boolean
    ): CaptchaDeleteResult? {
        var captchaSolved = false
        val maxRetries = if (_isTwoCaptchaConfigured.value) 3 else 0
        var retryCount = 0
        var deleteResult: DeleteResult = DeleteResult.Error("captcha required")

        while (retryCount < maxRetries && !captchaSolved) {
            retryCount++
            updateDeleteProgressStatus("2Captcha 캡챠 해결 중 ($retryCount/$maxRetries)")
            deleteResult = cleaner.deletePost(
                postNo,
                deleteType,
                solveCaptcha = true,
                deleteQuestionPosts = deleteQuestionPosts
            )
            if (deleteResult is DeleteResult.Success) {
                captchaSolved = true
            } else if (deleteResult is DeleteResult.Error) {
                if (retryCount < maxRetries) {
                    updateDeleteProgressStatus("⚠️ 2captcha 실패 - 재시도 중... ($retryCount/$maxRetries)")
                    delay(timing.captchaRetryDelayMillis)
                }
            } else {
                break
            }
        }

        if (!captchaSolved && deleteResult is DeleteResult.Error) {
            val keyAtPrompt = twoCaptchaKeyGeneration
            if (_isTwoCaptchaConfigured.value) {
                updateDeleteProgressStatus("⚠️ 2captcha 3번 실패 - 수동 해결 필요")
            } else {
                updateDeleteProgressStatus("⚠️ 캡챠 감지됨 - 수동 해결 필요")
            }
            _captchaFlag.value = true
            _showCaptchaDialog.value = true
            _nextCaptchaEstimatedTimeLeft.value = 0L
            if (!pauseCurrentTask(
                    DeleteTaskState.CAPTCHA_REQUIRED,
                    "캡챠 해결 후 앱에서 작업을 이어서 진행해 주세요.",
                    captchaRequired = true,
                    notify = false
                )
            ) return null
            notifier.notify("캡챠 해결이 필요합니다", "$galleryName 작업 중 수동 캡챠 해결이 필요합니다.")
            while (_captchaFlag.value && currentCoroutineContext().isActive) {
                delay(timing.captchaPollDelayMillis)
            }
            if (currentCoroutineContext().isActive && !_captchaFlag.value) {
                val solveWithUpdatedKey = _isTwoCaptchaConfigured.value && twoCaptchaKeyGeneration != keyAtPrompt
                delay(timing.captchaSettleDelayMillis)
                if (!updateTask(force = true) {
                        it.copy(
                            state = DeleteTaskState.RUNNING,
                            statusMessage = "캡챠 해결 완료, 삭제 재개 중",
                            captchaRequired = false
                        )
                    }) return null
                // 기다리는 동안 키를 등록했으면 처음 캡챠처럼 여러 번 시도하고, 그래도 안 되면 다시 직접 풀게 한다.
                if (solveWithUpdatedKey) return handleCaptcha(cleaner, postNo, deleteType, galleryName, postCount, deleteQuestionPosts)
                return CaptchaDeleteResult(
                    result = cleaner.deletePost(
                        postNo,
                        deleteType,
                        solveCaptcha = false,
                        deleteQuestionPosts = deleteQuestionPosts
                    ),
                    solvedManually = true
                )
            }
            return null
        }
        return CaptchaDeleteResult(result = deleteResult, solvedManually = false)
    }

    private fun updateGalleryItemProgress(
        completedGalleries: Int,
        galleryDeleted: Int,
        gallerySkipped: Int,
        totalGalleries: Int,
        postCount: Int,
        galleryName: String
    ) {
        val galleryProgress = ((galleryDeleted + gallerySkipped).toFloat() / postCount).coerceIn(0f, 1f)
        _progress.value = (completedGalleries + galleryProgress) / totalGalleries
        _currentGallery.value = galleryName
        _deletedCount.value = completedGalleries
    }
}

private data class CaptchaDeleteResult(
    val result: DeleteResult,
    val solvedManually: Boolean
)

private fun String.isGuestbookProblemLog(): Boolean =
    startsWith("⚠️") || startsWith("❌")
