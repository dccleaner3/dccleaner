package com.dccleaner.app.runtime

import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.network.CleanerPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DccleanerTaskController(
    deleteTaskStore: DeleteTaskStorePort,
    logSink: RuntimeLogSink = NoOpRuntimeLogSink,
    notifier: RuntimeNotifier = NoOpRuntimeNotifier,
    timing: DccleanerExecutionTiming = DccleanerExecutionTiming(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val engine = DccleanerExecutionEngine(
        deleteTaskStore = deleteTaskStore,
        logSink = logSink,
        notifier = notifier,
        scope = scope,
        timing = timing
    )
    private val daewangconRunner = DaewangconRunner(
        logSink = logSink,
        notifier = notifier,
        scope = scope,
        timing = timing
    )

    val isDeleting = engine.isDeleting
    val isCompleted = engine.isCompleted
    val progress = engine.progress
    val currentGallery = engine.currentGallery
    val currentGalleryEstimatedTimeLeft = engine.currentGalleryEstimatedTimeLeft
    val nextCaptchaEstimatedTimeLeft = engine.nextCaptchaEstimatedTimeLeft
    val isTwoCaptchaConfigured = engine.isTwoCaptchaConfigured
    val currentTaskLoginId = engine.currentTaskLoginId
    val currentDeleteType = engine.currentDeleteType
    val deletedCount = engine.deletedCount
    val totalCount = engine.totalCount
    val deleteLog = engine.deleteLog
    val errorMessage = engine.errorMessage
    val showCaptchaDialog = engine.showCaptchaDialog
    val captchaFlag = engine.captchaFlag
    val isDaewangconRunning = daewangconRunner.isRunning
    val isDaewangconCompleted = daewangconRunner.isCompleted
    val daewangconErrorMessage = daewangconRunner.errorMessage
    val daewangconProgress = daewangconRunner.progress
    val daewangconLog = daewangconRunner.logs
    val daewangconPostCount = daewangconRunner.postCount
    val daewangconCommentCount = daewangconRunner.commentCount

    fun setCleaner(cleaner: CleanerPort) {
        engine.setCleaner(cleaner)
        daewangconRunner.setCleaner(cleaner)
    }

    fun clearError() = engine.clearError()
    fun clearLogs() = engine.clearLogs()
    fun getCurrentTaskLoginId(): String = engine.getCurrentTaskLoginId()

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
    ) = engine.startDeletion(
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

    fun resumeDeletion(task: DeleteTaskProgress) = engine.resumeDeletion(task)
    fun stopDeletion(preserveTask: Boolean = false) = engine.stopDeletion(preserveTask)
    fun interruptDeletion(
        state: DeleteTaskState = DeleteTaskState.INTERRUPTED,
        message: String = "앱 종료로 삭제 작업이 중단되었습니다. 다음 실행 시 이어서 진행할 수 있습니다.",
        notify: Boolean = true
    ) = engine.pauseDeletion(state, message, notify = notify)
    fun resolveCaptcha() = engine.resolveCaptcha()

    @Suppress("UNUSED_PARAMETER")
    fun startDaewangcon(
        galleryId: String,
        postNo: String,
        postSubject: String,
        postContent: String,
        commentContent: String
    ) = daewangconRunner.start()

    fun stopDaewangcon() = daewangconRunner.stop()
    fun interruptDaewangcon(message: String) = daewangconRunner.interrupt(message)
    fun acknowledgeDaewangconResult() = daewangconRunner.acknowledgeResult()

    fun close() {
        if (engine.isDeleting.value) {
            engine.pauseDeletion(
                state = DeleteTaskState.INTERRUPTED,
                message = "앱 종료로 삭제 작업이 중단되었습니다. 다음 실행 시 이어서 진행할 수 있습니다.",
                notify = true
            )
        }
        if (daewangconRunner.isRunning.value) {
            daewangconRunner.interrupt("앱 종료로 대왕콘 작업이 중단되었습니다.")
        }
        daewangconRunner.close()
        engine.close()
        scope.cancel()
    }
}
