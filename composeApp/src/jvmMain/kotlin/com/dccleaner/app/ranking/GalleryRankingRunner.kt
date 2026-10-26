package com.dccleaner.app.ranking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 몇 위까지 보일지는 결과 화면에서 정하니, 요청에는 없고 모든 사람을 집계해 둔다. */
data class GalleryRankingRequest(val input: String, val period: RankingPeriod, val dates: ClosedRange<LocalDate>)

sealed interface GalleryRankingState {
    data object Idle : GalleryRankingState
    data class Running(val request: GalleryRankingRequest, val galleryName: String?, val progress: CrawlProgress) : GalleryRankingState
    data class Done(val request: GalleryRankingRequest, val result: GalleryRankingResult) : GalleryRankingState
    data class Failed(val request: GalleryRankingRequest, val message: String) : GalleryRankingState
}

/**
 * 화면과 상관없이 앱 프로세스 안에서 돈다. 다른 탭으로 가거나 화면을 내려도 계속 만들고,
 * 안드로이드는 [GalleryRankingState.Running] 동안 포그라운드 서비스가 프로세스를 붙잡는다.
 */
object GalleryRankingRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crawler = GalleryRankingCrawler()
    private var job: Job? = null
    private val _state = MutableStateFlow<GalleryRankingState>(GalleryRankingState.Idle)
    val state: StateFlow<GalleryRankingState> = _state.asStateFlow()

    fun start(request: GalleryRankingRequest): Boolean {
        if (_state.value is GalleryRankingState.Running) return false
        _state.value = GalleryRankingState.Running(request, null, CrawlProgress(CrawlProgress.Stage.RESOLVING))
        job = scope.launch {
            val outcome = try {
                GalleryRankingState.Done(request, build(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryRankingException) {
                GalleryRankingState.Failed(request, e.message.orEmpty())
            } catch (e: Exception) {
                GalleryRankingState.Failed(request, "갤창랭킹을 만들지 못했어요. 잠시 후 다시 시도해 주세요")
            }
            // 취소 뒤 새로 시작했다면 이전 작업의 결과로 덮어쓰지 않는다.
            replaceIfCurrent(request) { outcome }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = GalleryRankingState.Idle
    }

    /** 결과를 닫고 새로 만들 수 있게 한다. */
    fun reset() {
        if (_state.value !is GalleryRankingState.Running) _state.value = GalleryRankingState.Idle
    }

    /**
     * 지금 [request]를 만드는 중일 때만 상태를 바꾼다. 같은 내용으로 다시 시작해도 구분되도록 요청 객체 자체로 비교하고,
     * 댓글을 여러 스레드가 동시에 모아 확인과 쓰기를 한 번에(update) 한다.
     */
    private fun replaceIfCurrent(request: GalleryRankingRequest, next: () -> GalleryRankingState) =
        _state.update { if (it is GalleryRankingState.Running && it.request === request) next() else it }

    private suspend fun build(request: GalleryRankingRequest): GalleryRankingResult {
        val gallery = crawler.resolve(request.input)
        fun publish(progress: CrawlProgress) {
            replaceIfCurrent(request) { GalleryRankingState.Running(request, gallery.name, progress) }
        }
        publish(CrawlProgress(CrawlProgress.Stage.POSTS))
        val dates = request.dates
        val defaultNick = crawler.defaultNick(gallery)
        // 글·댓글 원본은 받는 대로 집계에 넘기고 버린다. 한 달치(수십만 개)를 쌓아 두면 휴대폰 메모리가 모자라 앱이 죽는다.
        val acc = GalleryRankingAggregator.Accumulator(dates, setOfNotNull(defaultNick))
        var postCount = 0
        val targets = crawler.collectPosts(gallery, dates, { postCount++; acc.addPost(it) }, ::publish)
        crawler.collectComments(gallery, targets, postCount, acc::addComment, ::publish)
        val result = acc.result(gallery, request.period, Int.MAX_VALUE)
        val shown = RankingSort.entries.flatMap(result::users)
        return result.copy(icons = crawler.fetchIcons(shown.map { it.iconUrl }))
    }
}
