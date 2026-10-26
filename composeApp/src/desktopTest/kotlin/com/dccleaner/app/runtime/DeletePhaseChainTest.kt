package com.dccleaner.app.runtime

import com.dccleaner.app.desktop.DesktopDeleteTaskStore
import com.dccleaner.app.model.*
import com.dccleaner.app.network.CleanerPort
import kotlinx.coroutines.*
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

/** 댓글 → 글처럼 두 단계를 한 번에 이어서 지우는 흐름. */
class DeletePhaseChainTest {
    /** 갤러리·종류별 항목을 따로 가진 가짜 클리너. c2는 하루 지난 댓글이라 날짜 필터(5일)에 걸린다. */
    private class Fake {
        val items = mapOf(
            ("cg" to "comment") to listOf("c1", "c2", "c3"),
            ("pg" to "posting") to listOf("p1", "p2", "p3")
        )
        val ages = mapOf("c1" to 10L, "c2" to 1L, "c3" to 10L, "p1" to 1L, "p2" to 1L, "p3" to 1L)
        val all = items.values.flatten().map { CollectedPost(it) }
        val queue = ArrayDeque<String>()
        val deleted = CopyOnWriteArrayList<Pair<String, String>>()
        val listRequests = CopyOnWriteArrayList<Pair<String, String>>()
        val guestbook = CopyOnWriteArrayList<Pair<Int, Int>>()
        val cleaner = Proxy.newProxyInstance(CleanerPort::class.java.classLoader,
            arrayOf(CleanerPort::class.java)) { _, method, args ->
            when (method.name) {
                "getUserId" -> "test-user"
                "has2CaptchaKey" -> false
                "restore2CaptchaKey", "resetCaptchaState" -> Unit
                "getPageCount" -> 1
                "getPostAgeDays" -> ages[args[0] as String]
                "getPostList" -> if (args.isNullOrEmpty()) queue.toList() else {
                    val gno = args[0] as String
                    val type = args[1] as String
                    listRequests.add(gno to type)
                    val done = deleted.map { it.first }
                    val posts = if ((args[2] as Int) > 1) emptyList()
                    else items[gno to type].orEmpty().filterNot { it in done }
                    PostListResult.Success(posts, true, posts.size, posts.map(::CollectedPost))
                }
                "exportCollectedPosts" -> all.filter { it.postNo in (args[0] as List<*>) }
                "importCollectedPosts" -> {
                    queue.clear()
                    @Suppress("UNCHECKED_CAST")
                    queue.addAll((args[0] as List<CollectedPost>).map { it.postNo })
                    Unit
                }
                "clearPostData" -> { queue.clear(); Unit }
                "getPostListSize" -> queue.size
                "getFirstPost" -> queue.firstOrNull()
                "removeFirstPost" -> { queue.removeFirst(); Unit }
                "deletePost" -> {
                    deleted.add(args[0] as String to args[1] as String)
                    DeleteResult.Success(emptyMap())
                }
                "recordCleanerRunGuestbookLog" -> { guestbook.add(args[0] as Int to args[1] as Int); true }
                else -> error("Unexpected call: ${method.name}")
            }
        } as CleanerPort
    }

    private val notifications = CopyOnWriteArrayList<Pair<String, String>>()
    private val notifier = object : RuntimeNotifier {
        override fun notify(title: String, message: String) { notifications.add(title to message) }
    }

    private val postPhase = DeletePhase(
        deleteType = "posting", selectedGalleries = listOf("pg"), galleryMap = mapOf("pg" to "PostGallery"),
        dateFilterEnabled = false, deleteNewestFirst = true, minPostAgeDaysToDelete = -1
    )

    private fun DccleanerExecutionEngine.startCommentsThenPosts() = startDeletion(
        selectedGalleries = listOf("cg"), deleteType = "comment", galleryMap = mapOf("cg" to "CommentGallery"),
        dateFilterEnabled = true, minPostAgeDaysToDelete = 5, deleteQuestionPosts = true,
        followUp = postPhase
    )

    @Test fun commentsThenPostsRunBothWithTheirOwnOptions() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            var stateAtPhaseTwo: Pair<Boolean, Boolean>? = null
            val questionFlags = CopyOnWriteArrayList<Pair<String, Boolean>>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    if (postType == "posting") {
                        stateAtPhaseTwo = stateAtPhaseTwo ?: (engine.isDeleting.value to engine.isCompleted.value)
                    }
                    questionFlags.add(postType to deleteQuestionPosts)
                    return fake.cleaner.deletePost(postNo, postType, solveCaptcha, deleteQuestionPosts)
                }
            })
            engine.startCommentsThenPosts()
            awaitIdle(engine)

            assertEquals(true to false, stateAtPhaseTwo)
            // 질문 글 옵션은 글 단계에만 적용된다.
            assertEquals(setOf("comment" to false, "posting" to true), questionFlags.toSet())
            assertTrue(engine.isCompleted.value)
            assertTrue(store.getAll().isEmpty())
            assertEquals(setOf("cg" to "comment", "pg" to "posting"), fake.listRequests.toSet())
            // 댓글은 날짜 필터로 c2를 남기고, 글은 날짜 필터 없이 최근부터 모두 지운다.
            assertEquals(listOf("c1", "c3"), fake.deleted.filter { it.second == "comment" }.map { it.first })
            assertEquals(setOf("p1", "p2", "p3"), fake.deleted.filter { it.second == "posting" }.map { it.first }.toSet())
            assertEquals(listOf(3 to 2), fake.guestbook)
            val completed = notifications.filter { "삭제 완료" in it.first }
            assertEquals(1, completed.size)
            assertTrue("댓글 2개 · 글 3개" in completed.single().second, completed.single().second)
            assertTrue(engine.deleteLog.value.any { "댓글 삭제를 마쳤어요 (2개) · 이어서 게시글 삭제를 시작해요" in it })
        }
    }

    @Test fun interruptionInFirstPhaseKeepsFollowUpAndResumeFinishesBoth() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            })
            engine.startCommentsThenPosts()
            withTimeout(5_000) { entered.await() }
            engine.pauseDeletion(DeleteTaskState.INTERRUPTED, "앱 종료", notify = false)
            awaitIdle(engine)

            val saved = store.getAll().single()
            assertEquals("comment", saved.deleteType)
            val followUp = assertNotNull(saved.followUp)
            assertEquals("posting", followUp.deleteType)
            assertEquals(listOf("pg"), followUp.selectedGalleries)
            assertTrue(followUp.deleteNewestFirst)
            assertEquals(-1, followUp.minPostAgeDaysToDelete)

            engine.setCleaner(fake.cleaner)
            engine.resumeDeletion(saved)
            withTimeout(5_000) { while (!engine.isCompleted.value) delay(5) }
            awaitIdle(engine)
            assertTrue(store.getAll().isEmpty())
            assertEquals(setOf("p1", "p2", "p3"), fake.deleted.filter { it.second == "posting" }.map { it.first }.toSet())
            assertEquals(listOf(3 to 2), fake.guestbook)
        }
    }

    @Test fun userStopInFirstPhaseCancelsWholeChain() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
            })
            engine.startCommentsThenPosts()
            withTimeout(5_000) { entered.await() }
            engine.stopDeletion()
            withTimeout(5_000) { cancelled.await() }
            delay(50)

            assertTrue(store.getAll().isEmpty())
            assertFalse(engine.isCompleted.value)
            assertFalse(engine.isDeleting.value)
            assertTrue(fake.listRequests.none { it.second == "posting" })
            assertTrue(fake.guestbook.isEmpty())
        }
    }

    @Test fun interruptionInSecondPhaseResumesWithCarriedCounts() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    if (postType == "comment") return fake.cleaner.deletePost(postNo, postType, solveCaptcha, deleteQuestionPosts)
                    entered.complete(Unit)
                    awaitCancellation()
                }
            })
            engine.startCommentsThenPosts()
            withTimeout(5_000) { entered.await() }
            engine.pauseDeletion(DeleteTaskState.INTERRUPTED, "앱 종료", notify = false)
            awaitIdle(engine)

            val saved = store.getAll().single()
            assertEquals("posting", saved.deleteType)
            assertNull(saved.followUp)
            assertEquals(0 to 2, saved.carriedDeletedPosts to saved.carriedDeletedComments)

            engine.setCleaner(fake.cleaner)
            engine.resumeDeletion(saved)
            withTimeout(5_000) { while (!engine.isCompleted.value) delay(5) }
            awaitIdle(engine)
            assertTrue(store.getAll().isEmpty())
            assertEquals(listOf(3 to 2), fake.guestbook)
            assertTrue("댓글 2개 · 글 3개" in notifications.single { "삭제 완료" in it.first }.second)
        }
    }

    // 다음 단계 저장 중에 들어온 중단이 다음 단계 기록을 남기거나 삭제 중 표시를 켠 채로 두면 안 된다.
    @Test fun stopDuringPhaseSwitchDiscardsWholeChain() = runBlocking {
        val stopper = java.util.concurrent.atomic.AtomicReference<Thread?>()
        withEngine(Fake(), wrap = { store, engine ->
            object : DeleteTaskStorePort by store {
                override fun save(task: DeleteTaskProgress): Boolean {
                    val saved = store.save(task)
                    if (task.deleteType == "posting" &&
                        stopper.compareAndSet(null, Thread { engine().stopDeletion() })) {
                        stopper.get()!!.start()
                        Thread.sleep(100)
                    }
                    return saved
                }
            }
        }) { engine, store, fake ->
            engine.startCommentsThenPosts()
            withTimeout(5_000) { while (stopper.get() == null) delay(5) }
            stopper.get()!!.join(5_000)
            // 중단 직후 잠깐 꺼졌다가 다음 단계가 다시 켜는 경우를 잡으려고 조금 기다린 뒤 본다.
            delay(200)
            awaitIdle(engine)

            assertFalse(engine.isDeleting.value)
            assertFalse(engine.isCompleted.value)
            assertTrue(store.getAll().isEmpty(), store.getAll().toString())
            assertTrue(fake.guestbook.isEmpty())
        }
    }

    @Test fun failedSaveOfNextPhaseKeepsFirstPhaseResumable() = runBlocking {
        var rejectPosting = true
        withEngine(Fake(), wrap = { store, _ ->
            object : DeleteTaskStorePort by store {
                override fun save(task: DeleteTaskProgress) =
                    !(rejectPosting && task.deleteType == "posting") && store.save(task)
            }
        }) { engine, store, fake ->
            engine.startCommentsThenPosts()
            awaitIdle(engine)

            assertNotNull(engine.errorMessage.value)
            val saved = store.getAll().single()
            assertEquals("comment", saved.deleteType)
            assertEquals("posting", saved.followUp?.deleteType)
            assertTrue(fake.deleted.none { it.second == "posting" })

            rejectPosting = false
            engine.resumeDeletion(saved)
            withTimeout(5_000) { while (!engine.isCompleted.value) delay(5) }
            awaitIdle(engine)
            assertTrue(store.getAll().isEmpty())
            assertEquals(listOf(3 to 2), fake.guestbook)
        }
    }

    private suspend fun awaitIdle(engine: DccleanerExecutionEngine) {
        withTimeout(5_000) { while (engine.isDeleting.value) delay(5) }
    }

    private suspend fun withEngine(fake: Fake,
        wrap: (DesktopDeleteTaskStore, () -> DccleanerExecutionEngine) -> DeleteTaskStorePort = { s, _ -> s },
        verify: suspend (DccleanerExecutionEngine, DesktopDeleteTaskStore, Fake) -> Unit) {
        val root = Files.createTempDirectory("dccleaner-phase-chain-test").toFile()
        val store = DesktopDeleteTaskStore(root)
        lateinit var engine: DccleanerExecutionEngine
        engine = DccleanerExecutionEngine(wrap(store) { engine }, notifier = notifier, timing = DccleanerExecutionTiming.Immediate)
        try {
            engine.setCleaner(fake.cleaner)
            verify(engine, store, fake)
        } finally {
            engine.close()
            root.deleteRecursively()
        }
    }
}
