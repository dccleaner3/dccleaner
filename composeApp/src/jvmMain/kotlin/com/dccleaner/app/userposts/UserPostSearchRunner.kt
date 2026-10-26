package com.dccleaner.app.userposts

import com.dccleaner.app.ranking.GalleryRankingCrawler
import com.dccleaner.app.ranking.GalleryRankingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UserPostSearchState {
    data object Idle : UserPostSearchState

    /** 갤러리와 닉네임을 확인하는 중. */
    data class Starting(val request: UserPostSearchRequest) : UserPostSearchState

    /** 시작하지 못했다(갤러리·식별코드 확인 실패 등). 찾은 글은 없다. */
    data class Failed(val request: UserPostSearchRequest, val message: String) : UserPostSearchState

    data class Active(
        val request: UserPostSearchRequest,
        val target: UserPostTarget,
        /** 찾은 글, 최신순. */
        val posts: List<FoundPost>,
        /** 지금 더 찾는 중인지. */
        val loading: Boolean,
        /** 처음 글까지 다 찾았는지. */
        val exhausted: Boolean,
        /** 처음 글까지 확인한 대략의 비율(0~1). */
        val progress: Float,
        /** 지난번 더 불러오기에서 한 번에 찾는 한도까지 찾았는데 새 글이 없었는지(계속 찾기를 권한다). */
        val lastRoundEmpty: Boolean,
        /** 마지막 시도가 실패했으면 그 이유. 찾은 글은 그대로 두고, 더 불러오기로 같은 자리부터 다시 한다. */
        val error: String?,
        /** 최근 글부터 확인한 범위(대략의 글 수). 진행률 대신 보여 준다. */
        val checkedPosts: Long = 0,
        /** 끝까지 자동으로 찾는 중인지. 멈추거나 실패하거나 끝나면 false. */
        val untilEnd: Boolean = false
    ) : UserPostSearchState
}

/**
 * 화면과 상관없이 앱 프로세스 안에서 돈다. 다른 탭에 다녀와도 결과가 남고, 로그아웃하면 [clear]로 지운다.
 * 한 번에 한 사람만 찾는다.
 */
object UserPostSearchRunner {
    /** 더 불러오기 한 번에 보내는 요청 수의 한도. 큰 갤러리에서 오래 붙잡고 있지 않게 끊고, 계속 찾을지 사용자가 정한다. */
    internal const val MAX_REQUESTS_PER_ROUND = 100
    /** '처음 글까지' 모드는 지켜보지 않아도 오래 돌므로 동시 요청을 줄이고 라운드 사이에 쉰다(디시가 IP를 막지 않게). */
    private const val UNTIL_END_CONCURRENCY = 2
    private const val UNTIL_END_ROUND_PAUSE_MILLIS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val crawler = UserPostSearchCrawler()
    // 화면(메인 스레드)과 검색(IO 스레드)이 함께 보므로 바로 보이게 한다.
    @Volatile private var job: Job? = null
    @Volatile private var session: UserPostSearchSession? = null
    private val _state = MutableStateFlow<UserPostSearchState>(UserPostSearchState.Idle)
    val state: StateFlow<UserPostSearchState> = _state.asStateFlow()

    /** 입력이 맞지 않으면 그 이유를, 맞으면 null. 화면에서 찾기 전에 확인한다. */
    fun validate(request: UserPostSearchRequest): String? = when {
        GalleryRankingCrawler.parseGalleryInput(request.galleryInput) == null ->
            if (request.galleryInput.isBlank()) "갤러리 주소나 ID를 넣어 주세요" else "갤러리 주소나 ID를 확인해 주세요"
        request.kind == UserPostTargetKind.UID && UserPostSearchCrawler.normalizeUid(request.value) == null ->
            if (request.value.isBlank()) "식별코드를 넣어 주세요" else "식별코드는 영문·숫자로 돼 있어요. 닉네임이 아니라 식별코드를 넣어 주세요"
        request.kind == UserPostTargetKind.IP && UserPostSearchCrawler.normalizeIp(request.value) == null ->
            if (request.value.isBlank()) "IP를 넣어 주세요" else "IP는 118.235처럼 앞 두 자리를 넣어 주세요"
        request.kind == UserPostTargetKind.IP && request.nickname.isBlank() -> "글쓴이 닉네임을 넣어 주세요"
        else -> null
    }

    fun start(request: UserPostSearchRequest): Boolean {
        if (validate(request) != null) return false
        cancelJob()
        session = null
        _state.value = UserPostSearchState.Starting(request)
        job = scope.launch {
            try {
                val gallery = crawler.resolveGallery(request.galleryInput)
                // 작성자를 숨긴 갤러리는 검색 결과가 0개라 끝까지 찾아도 모른다. 시작할 때 일반 목록으로 먼저 확인한다.
                if (crawler.hidesWriters(gallery)) throw UserPostSearchException("이 갤러리는 글쓴이의 식별코드·IP를 숨겨서 찾을 수 없어요")
                val target = when (request.kind) {
                    UserPostTargetKind.UID -> {
                        val uid = requireNotNull(UserPostSearchCrawler.normalizeUid(request.value))
                        UserPostTarget(gallery, request.kind, uid, crawler.gallogNickname(uid))
                    }
                    UserPostTargetKind.IP -> UserPostTarget(gallery, request.kind,
                        requireNotNull(UserPostSearchCrawler.normalizeIp(request.value)), request.nickname.trim())
                }
                val newSession = UserPostSearchSession(crawler, target)
                // 닉네임 확인은 취소돼도 끝까지 돌 수 있다. 그사이 멈췄다면 찾는 중 상태로 되살리지 않는다.
                ensureActive()
                session = newSession
                _state.value = UserPostSearchState.Active(request, target, emptyList(), loading = true, exhausted = false,
                    progress = 0f, lastRoundEmpty = false, error = null)
                runRound(newSession, keepGoing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 갤러리 확인은 취소돼도 끝까지 돌 수 있다. 그사이 멈추거나 새로 시작했다면 이 실패로 덮어쓰지 않는다.
                ensureActive()
                if (session == null) _state.value = UserPostSearchState.Failed(request, e.userMessage())
            }
        }
        return true
    }

    /**
     * 다음 글을 더 찾는다. 실패했던 자리부터 다시 하는 것도 이것으로 한다.
     * [untilEnd]면 글을 찾아도 멈추지 않고, 중지하거나 실패하거나 처음 글까지 볼 때까지 이어서 찾는다.
     */
    fun loadMore(untilEnd: Boolean = false) {
        val current = _state.value as? UserPostSearchState.Active ?: return
        val activeSession = session ?: return
        if (current.loading || current.exhausted) return
        _state.value = current.copy(loading = true, error = null, lastRoundEmpty = false, untilEnd = untilEnd)
        job = scope.launch {
            while (runRound(activeSession, keepGoing = untilEnd)) delay(UNTIL_END_ROUND_PAUSE_MILLIS)
        }
    }

    /** 실패한 채 멈춰 있으면 같은 자리부터 한 번 다시 시도한다(예: 백그라운드에서 앱이 멈췄다가 돌아왔을 때). */
    fun retryAfterInterruption() {
        val current = _state.value as? UserPostSearchState.Active ?: return
        if (current.error != null && !current.exhausted && !current.loading) loadMore(current.untilEnd)
    }

    /** 찾던 것을 멈춘다. 찾은 글은 남는다. 시작 확인 중이었다면 처음으로 돌아간다. */
    fun stop() {
        cancelJob()
        _state.update { state ->
            when (state) {
                is UserPostSearchState.Active -> state.copy(loading = false, untilEnd = false)
                is UserPostSearchState.Starting -> UserPostSearchState.Idle
                else -> state
            }
        }
    }

    /** 결과를 닫고 새로 찾을 수 있게 한다. 로그아웃할 때도 부른다. */
    fun clear() {
        cancelJob()
        session = null
        _state.value = UserPostSearchState.Idle
    }

    /**
     * 한 번(요청 한도까지) 찾는다. [keepGoing]이고 아직 끝이 아니면 찾는 중 상태를 유지한 채 true를 돌려줘 이어서 찾게 한다.
     */
    private suspend fun runRound(activeSession: UserPostSearchSession, keepGoing: Boolean): Boolean {
        var found = 0
        return try {
            activeSession.loadMore(MAX_REQUESTS_PER_ROUND, if (keepGoing) UNTIL_END_CONCURRENCY else UserPostSearchSession.CONCURRENCY) { posts ->
                found += posts.size
                // 디시 검색은 글 번호 순서가 아닐 때가 있어 합칠 때 최신순으로 맞춘다. 새 글이 없고 확인 범위도 그대로면 화면을 건드리지 않는다.
                val checked = activeSession.checkedPosts
                updateIfCurrent(activeSession) {
                    if (posts.isEmpty() && checked == it.checkedPosts) it
                    else it.copy(posts = (it.posts + posts).sortedByDescending { post -> post.no }, checkedPosts = checked,
                        progress = activeSession.progress)
                }
            }
            val more = keepGoing && !activeSession.exhausted
            // 멈춘 뒤 끝난 요청이 찾는 중 표시를 다시 켜지 않게 한다.
            currentCoroutineContext().ensureActive()
            updateIfCurrent(activeSession) {
                it.copy(loading = more, untilEnd = more, exhausted = activeSession.exhausted, progress = activeSession.progress,
                    checkedPosts = activeSession.checkedPosts, lastRoundEmpty = !more && found == 0 && !activeSession.exhausted)
            }
            more
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // '처음 글까지' 모드는 남겨 둬서, 돌아와 다시 시도할 때 이어서 끝까지 찾는다. 화면은 찾는 중일 때만 이 값을 본다.
            updateIfCurrent(activeSession) {
                it.copy(loading = false, exhausted = activeSession.exhausted, progress = activeSession.progress,
                    checkedPosts = activeSession.checkedPosts, error = e.userMessage())
            }
            false
        }
    }

    /** 멈추거나 새로 시작했다면 이전 검색의 결과로 덮어쓰지 않는다. */
    private fun updateIfCurrent(activeSession: UserPostSearchSession, next: (UserPostSearchState.Active) -> UserPostSearchState.Active) {
        _state.update { state -> if (state is UserPostSearchState.Active && session === activeSession) next(state) else state }
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }

    private fun Exception.userMessage(): String = when (this) {
        is UserPostSearchException, is GalleryRankingException -> message.orEmpty()
        else -> "글을 찾지 못했어요. 잠시 후 다시 시도해 주세요"
    }
}
