package com.dccleaner.app.runtime

import com.dccleaner.app.model.DaewangconDefaults
import com.dccleaner.app.model.DaewangconProgress
import com.dccleaner.app.model.WriteResult
import com.dccleaner.app.network.CleanerPort
import com.dccleaner.app.util.formatDurationMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DaewangconRunner(
    private val logSink: RuntimeLogSink = NoOpRuntimeLogSink,
    private val notifier: RuntimeNotifier = NoOpRuntimeNotifier,
    private val scope: CoroutineScope,
    private val timing: DccleanerExecutionTiming = DccleanerExecutionTiming()
) {
    companion object {
        private const val TARGET_GALLERY_ID = "kingcon"
        private const val TARGET_POST_NO = "1400"
    }

    private var cleaner: CleanerPort? = null
    private val jobLock = Any()
    private var job: Job? = null
    private var requiredPostCount = DaewangconDefaults.DEFAULT_REQUIRED_POST_COUNT
    private var requiredCommentCount = DaewangconDefaults.DEFAULT_REQUIRED_COMMENT_COUNT

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val _postCount = MutableStateFlow(0)
    val postCount: StateFlow<Int> = _postCount.asStateFlow()
    private val _commentCount = MutableStateFlow(0)
    val commentCount: StateFlow<Int> = _commentCount.asStateFlow()

    fun setCleaner(cleaner: CleanerPort) {
        this.cleaner = cleaner
    }

    fun start() {
        synchronized(jobLock) {
            if (job?.isActive == true) return
            resetState()
            job = scope.launch {
                try {
                    perform()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(e.message ?: "알 수 없는 오류")
                } finally {
                    val finishingJob = currentCoroutineContext()[Job]
                    synchronized(jobLock) {
                        if (job === finishingJob) {
                            job = null
                            _isRunning.value = false
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(jobLock) {
            if (job?.isActive == true) addLog("🛑 사용자에 의해 작업이 중단됨")
            job?.cancel()
            _isRunning.value = false
        }
    }

    fun interrupt(message: String) {
        synchronized(jobLock) {
            job?.cancel()
            _isRunning.value = false
            _isCompleted.value = false
            _errorMessage.value = message
            addLog("❌ $message")
        }
    }

    fun acknowledgeResult() {
        _isCompleted.value = false
        _errorMessage.value = null
        if (!_isRunning.value) _progress.value = 0f
    }

    fun close() {
        synchronized(jobLock) {
            job?.cancel()
            job = null
            _isRunning.value = false
        }
    }

    private fun resetState() {
        _isRunning.value = true
        _isCompleted.value = false
        _errorMessage.value = null
        _progress.value = 0f
        _logs.value = emptyList()
        _postCount.value = 0
        _commentCount.value = 0
        requiredPostCount = DaewangconDefaults.DEFAULT_REQUIRED_POST_COUNT
        requiredCommentCount = DaewangconDefaults.DEFAULT_REQUIRED_COMMENT_COUNT
    }

    private suspend fun perform() {
        val cleaner = cleaner ?: return fail("로그인 정보를 불러오지 못했습니다.")

        addLog("🎯 대왕콘 얻기 시작")
        addLog("🔎 디시 서버에서 오늘 대왕콘 진행도 확인 중...")
        val initial = cleaner.getDaewangconProgress()
            ?: return fail("대왕콘 진행도를 확인하지 못해 자동 작성을 시작하지 않았습니다.")
        applyProgress(initial)

        val postsNeeded = initial.remainingPostCount
        val commentsNeeded = initial.remainingCommentCount
        addLog(
            "📊 현재 글 ${initial.postCount}/${initial.requiredPostCount}, " +
                    "댓글 ${initial.commentCount}/${initial.requiredCommentCount}"
        )
        if (initial.durationHours > 0) {
            addLog("📋 서버 설정: 대왕콘 ${initial.durationHours}시간, 상태 ${initial.status.ifBlank { "확인 불가" }}")
        }
        addLog("✍️ 추가 필요: 글 ${postsNeeded}개, 댓글 ${commentsNeeded}개")

        if (initial.requirementsMet) {
            addLog("✅ 오늘 작성 조건을 이미 충족했습니다. 추가 작성 없이 설정을 진행합니다.")
        } else {
            coroutineScope {
                if (postsNeeded > 0) launch {
                    writeMissing(
                        label = "글",
                        missing = postsNeeded,
                        batchSize = DaewangconDefaults.POST_BATCH_SIZE,
                        batchDelayMillis = timing.daewangconPostBatchDelayMillis,
                        intervalDelayMillis = timing.daewangconPostIntervalDelayMillis,
                        required = initial.requiredPostCount,
                        current = { _postCount.value },
                        write = {
                            val text = createText(_postCount.value + 1)
                            cleaner.writePost(TARGET_GALLERY_ID, text, text)
                        },
                        onSuccess = { _postCount.update { it + 1 } }
                    )
                }
                if (commentsNeeded > 0) launch {
                    writeMissing(
                        label = "댓글",
                        missing = commentsNeeded,
                        batchSize = DaewangconDefaults.COMMENT_BATCH_SIZE,
                        batchDelayMillis = timing.daewangconCommentBatchDelayMillis,
                        intervalDelayMillis = timing.daewangconCommentIntervalDelayMillis,
                        required = initial.requiredCommentCount,
                        current = { _commentCount.value },
                        write = {
                            val text = createText(_commentCount.value + 1)
                            cleaner.writeComment(TARGET_GALLERY_ID, TARGET_POST_NO, text)
                        },
                        onSuccess = { _commentCount.update { it + 1 } }
                    )
                }
            }
        }

        currentCoroutineContext().ensureActive()
        addLog("🔎 디시 서버에서 작성 결과 최종 확인 중...")
        val final = cleaner.getDaewangconProgress()
            ?: return fail("작성 후 대왕콘 진행도를 확인하지 못해 설정 요청을 보내지 않았습니다.")
        applyProgress(final)

        if (!final.requirementsMet) {
            return fail(
                "일부 작성이 서버 진행도에 반영되지 않았습니다. " +
                        "(글 ${final.postCount}/${final.requiredPostCount}, " +
                        "댓글 ${final.commentCount}/${final.requiredCommentCount})"
            )
        }

        addLog("🎁 대왕콘 설정 요청 중...")
        when (val result = cleaner.setBigcon()) {
            is WriteResult.Success -> {
                _progress.value = 1f
                _isCompleted.value = true
                addLog("🎉 대왕콘 작업 완료! 서버 기준 작성 조건을 충족했습니다.")
                notifier.notify("대왕콘 작업 완료", "서버 기준 글/댓글 조건 충족 후 대왕콘 설정 완료")
            }
            is WriteResult.Failed -> fail("대왕콘 설정 요청에 실패했습니다: ${result.message}")
        }
    }

    private suspend fun writeMissing(
        label: String,
        missing: Int,
        batchSize: Int,
        batchDelayMillis: Long,
        intervalDelayMillis: Long,
        required: Int,
        current: () -> Int,
        write: suspend () -> WriteResult,
        onSuccess: () -> Unit
    ) {
        addLog("${if (label == "글") "📝" else "💬"} $label 작성 시작 (${missing}개)")
        for (i in 1..missing) {
            currentCoroutineContext().ensureActive()
            if (i > 1 && (i - 1) % batchSize == 0 && batchDelayMillis > 0L) {
                addLog("⏳ ${formatDurationMillis(batchDelayMillis)} 대기 중... (${label}쓰기 제한)")
                delay(batchDelayMillis)
            }

            when (val result = write()) {
                is WriteResult.Success -> {
                    onSuccess()
                    updateProgress()
                    addLog("✅ $label 작성 완료 (${current()}/$required)")
                }
                is WriteResult.Failed -> addLog("❌ $label 작성 실패 ($i/$missing): ${result.message}")
            }
            if (i < missing && intervalDelayMillis > 0L) delay(intervalDelayMillis)
        }
    }

    private fun applyProgress(value: DaewangconProgress) {
        requiredPostCount = value.requiredPostCount.coerceAtLeast(1)
        requiredCommentCount = value.requiredCommentCount.coerceAtLeast(1)
        _postCount.value = value.postCount.coerceAtLeast(0)
        _commentCount.value = value.commentCount.coerceAtLeast(0)
        updateProgress()
    }

    private fun updateProgress() {
        val completed =
            _postCount.value.coerceIn(0, requiredPostCount) +
                    _commentCount.value.coerceIn(0, requiredCommentCount)
        val required = requiredPostCount + requiredCommentCount
        _progress.value = if (required > 0) completed.toFloat() / required.toFloat() else 0f
    }

    private fun createText(count: Int): String {
        val idTag = (System.currentTimeMillis() % 100_000).toInt().toString(16).padStart(5, '0')
        return "$count - 디시클린어 모바일 [$idTag]"
    }

    private fun fail(message: String) {
        _errorMessage.value = message
        _isCompleted.value = false
        addLog("❌ $message")
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { it + "[$timestamp] $message" }
        scope.launch { logSink.addLog("Daewangcon", message) }
    }
}
