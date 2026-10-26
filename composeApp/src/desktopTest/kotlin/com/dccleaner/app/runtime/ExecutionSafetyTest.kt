package com.dccleaner.app.runtime

import com.dccleaner.app.desktop.DesktopDeleteTaskStore
import com.dccleaner.app.model.*
import com.dccleaner.app.network.CleanerPort
import kotlinx.coroutines.*
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class ExecutionSafetyTest {
    private class Fake(
        var matched: Boolean = true,
        var outcome: DeleteResult = DeleteResult.Success(emptyMap()),
        val posts: List<CollectedPost> = listOf(CollectedPost("post-1", text = "selected item")),
        val firstPostDetails: PostDetails? = null,
        val autoSolve: Boolean = false
    ) {
        val queue = ArrayDeque<String>()
        val deleted = CopyOnWriteArrayList<String>()
        val post get() = posts.first()
        val successfulDeletes = CopyOnWriteArrayList<String>()
        val automaticAttempts = CopyOnWriteArrayList<String>()
        var configuredKey = ""
        val cleaner = Proxy.newProxyInstance(CleanerPort::class.java.classLoader,
            arrayOf(CleanerPort::class.java)) { _, method, args ->
            when (method.name) {
                "getUserId" -> "test-user"
                "has2CaptchaKey" -> configuredKey.isNotBlank()
                "isPostDccon" -> false
                "restore2CaptchaKey" -> { configuredKey = args[0] as String; Unit }
                "resetCaptchaState" -> Unit
                "getPageCount" -> 1
                "getPostUrl" -> "https://example.invalid/${args[0]}"
                "getPostDetails" -> if ((args[0] as String).endsWith(post.postNo)) firstPostDetails else PostDetails(0, 0, 0)
                "getPostWriterUid" -> if ((args[0] as String).endsWith(post.postNo)) null else "test-user"
                "getPostList" -> if (args.isNullOrEmpty()) queue.toList() else {
                    val posts = if ((args[2] as Int) > 1) emptyList() else posts.filterNot { it.postNo in successfulDeletes }
                    PostListResult.Success(posts.map { it.postNo }, matched, posts.size, posts)
                }
                "exportCollectedPosts" -> posts.filter { it.postNo in (args[0] as List<*>) }
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
                    val postNo = args[0] as String
                    deleted.add(postNo)
                    if (args[2] == true) automaticAttempts.add(postNo)
                    val result = if (postNo == post.postNo) {
                        if (autoSolve && args[2] == true && configuredKey.isNotBlank()) DeleteResult.Success(emptyMap()) else outcome
                    } else DeleteResult.Success(emptyMap())
                    if (result is DeleteResult.Success) successfulDeletes.add(postNo)
                    result
                }
                else -> error("Unexpected call: ${method.name}")
            }
        } as CleanerPort
    }

    @Test fun galleryMismatchStopsBothOrdersBeforeDeletion() = runBlocking {
        for (newest in listOf(false, true)) withEngine(Fake(matched = false)) { engine, store, fake ->
            engine.resumeDeletion(task(newest))
            awaitIdle(engine)
            assertTrue(fake.deleted.isEmpty())
            assertFalse(engine.isCompleted.value)
            assertEquals(DeleteTaskState.NETWORK_ERROR, store.getAll().single().state)
        }
    }

    @Test fun itemFailuresAreCountedAsExcludedAndDoNotStopEitherOrder() = runBlocking {
        for (newest in listOf(false, true)) {
            for (failure in listOf(DeleteResult.Failed, DeleteResult.Blocked, DeleteResult.Error("server error"))) {
                withEngine(Fake(outcome = failure, posts = twoPosts)) { engine, store, fake ->
                    engine.resumeDeletion(task(newest))
                    awaitIdle(engine)
                    assertTrue(engine.isCompleted.value)
                    assertTrue(store.getAll().isEmpty())
                    assertEquals(listOf("post-1", "post-2"), fake.deleted)
                    assertEquals(listOf("post-2"), fake.successfulDeletes)
                    assertEquals(DeleteItemCounts(1, 1), engine.itemCounts.value)
                    assertTrue(engine.deleteLog.value.any { "삭제 완료 (총 1개, 캡챠 해제 0개, 1개 제외)" in it })
                }
            }
        }
    }

    @Test fun partialGalleryProgressContributesToOverallPercentInBothOrders() = runBlocking {
        for (newest in listOf(false, true)) for (galleryCount in listOf(1, 2)) {
            withEngine(Fake(outcome = DeleteResult.Failed, posts = twoPosts)) { engine, _, fake ->
                val secondRequest = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                var attempts = 0
                engine.setCleaner(object : CleanerPort by fake.cleaner {
                    override suspend fun deletePost(postNo: String, postType: String,
                        solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                        if (++attempts == 2) {
                            secondRequest.complete(Unit)
                            release.await()
                        }
                        return fake.cleaner.deletePost(postNo, postType, solveCaptcha, deleteQuestionPosts)
                    }
                })
                val galleries = (1..galleryCount).map { "gallery-$it" }
                engine.resumeDeletion(task(newest).copy(selectedGalleries = galleries,
                    galleryMap = galleries.associateWith { it }))
                try {
                    withTimeout(5_000) { secondRequest.await() }
                    assertEquals(0.5f / galleryCount, engine.progress.value)
                } finally { release.complete(Unit) }
                awaitIdle(engine)
            }
        }
    }

    @Test fun itemTotalsSurviveGalleryTransitionsAndResume() = runBlocking {
        for (newest in listOf(false, true)) {
            withEngine(Fake(outcome = DeleteResult.Failed, posts = twoPosts)) { engine, _, _ ->
                engine.resumeDeletion(task(newest).copy(selectedGalleries = listOf("one", "two"),
                    galleryMap = mapOf("one" to "One", "two" to "Two")))
                awaitIdle(engine)
                assertEquals(DeleteItemCounts(1, 2), engine.itemCounts.value)
            }
            withEngine(Fake(outcome = DeleteResult.Failed, posts = twoPosts)) { engine, store, _ ->
                val checkpoint = task(newest).copy(selectedGalleries = listOf("done", "chosen"),
                    currentGalleryIndex = 1, completedGalleries = 1, totalDeleted = 10, totalSkipped = 4)
                assertTrue(store.save(checkpoint))
                engine.resumeDeletion(store.getAll().single())
                awaitIdle(engine)
                assertEquals(DeleteItemCounts(11, 5), engine.itemCounts.value)
            }
        }
    }

    @Test fun acknowledgingDeletionClearsRetainedCompletionWithoutClearingSessionOrCheckpoint() = runBlocking {
        for (type in listOf("posting", "comment")) withEngine(Fake()) { engine, store, fake ->
            engine.resumeDeletion(task().copy(deleteType = type))
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            val counts = engine.itemCounts.value
            val logs = engine.deleteLog.value
            val checkpoint = task().copy(id = "unrelated-interrupted-task", state = DeleteTaskState.INTERRUPTED)
            assertTrue(store.save(checkpoint))
            // 저장소가 저장 시각을 새로 찍으므로 저장된 그대로를 기준으로 본다.
            val saved = store.get(checkpoint.id)

            engine.acknowledgeDeletionResult()
            engine.acknowledgeDeletionResult()

            assertFalse(engine.isCompleted.value)
            assertFalse(engine.isDeleting.value)
            assertEquals("test-user", fake.cleaner.getUserId())
            assertEquals("test-user", engine.currentTaskLoginId.value)
            assertEquals(counts, engine.itemCounts.value)
            assertEquals(logs, engine.deleteLog.value)
            assertEquals(saved, store.get(checkpoint.id))
        }
    }

    @Test fun explicitExclusionsStillComplete() = runBlocking {
        for (newest in listOf(false, true)) withEngine(Fake(outcome = DeleteResult.Excluded("question post"))) { engine, store, _ ->
            engine.resumeDeletion(task(newest))
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            assertTrue(store.getAll().isEmpty())
        }
    }

    @Test fun missingFilterDataSkipsWithoutDeletingAndCountsExclusionsInBothOrders() = runBlocking {
        for (newest in listOf(false, true)) {
            for (details in listOf(null, PostDetails(null, null, null))) {
                for (filteredTask in listOf(
                    task(newest).copy(recommendFilterEnabled = true, minRecommendToKeep = 10),
                    task(newest).copy(commentFilterEnabled = true, minCommentToKeep = 10),
                    task(newest).copy(viewFilterEnabled = true, minViewToKeep = 10),
                    task(newest).copy(deleteType = "comment", myPostFilterEnabled = true)
                )) withEngine(Fake(posts = twoPosts, firstPostDetails = details)) { engine, store, fake ->
                    engine.resumeDeletion(filteredTask)
                    awaitIdle(engine)
                    assertTrue(engine.isCompleted.value)
                    assertTrue(store.getAll().isEmpty())
                    assertEquals(listOf("post-2"), fake.deleted)
                    assertTrue(engine.deleteLog.value.any { "삭제 완료 (총 1개, 캡챠 해제 0개, 1개 제외)" in it })
                }
            }
        }
    }

    @Test fun manualCaptchaKeepsCheckpointAndCanResume() = runBlocking {
        withEngine(Fake(outcome = DeleteResult.Error("captcha required"))) { engine, store, fake ->
            engine.resumeDeletion(task())
            withTimeout(5_000) {
                while (store.getAll().singleOrNull()?.state != DeleteTaskState.CAPTCHA_REQUIRED) delay(5)
            }
            assertTrue(engine.showCaptchaDialog.value)
            assertEquals(0, store.getAll().single().queueCursor)
            fake.outcome = DeleteResult.Success(emptyMap())
            engine.resolveCaptcha()
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            assertTrue(store.getAll().isEmpty())
        }
    }

    @Test fun keyConfiguredDuringCaptchaEnablesAutomaticRetryForCurrentTask() = runBlocking {
        withEngine(Fake(outcome = DeleteResult.Error("captcha required"), autoSolve = true)) { engine, store, fake ->
            engine.resumeDeletion(task())
            withTimeout(5_000) {
                while (store.getAll().singleOrNull()?.state != DeleteTaskState.CAPTCHA_REQUIRED) delay(5)
            }
            assertTrue(engine.useTwoCaptchaKey("test-key"))
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            assertTrue(fake.automaticAttempts.contains("post-1"))
            assertTrue(store.getAll().isEmpty())
        }
    }

    @Test fun reRegisteringTheSameKeyDuringManualCaptchaRetriesAutomatically() = runBlocking {
        // 2Captcha 잔액이 떨어져 직접 풀기로 넘어간 뒤 충전하고 같은 키를 다시 넣는 경우. 글을 제외하지 않고 자동으로 풀어 지운다.
        withEngine(Fake(outcome = DeleteResult.Error("captcha required"))) { engine, store, fake ->
            var charged = false
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    if (solveCaptcha && charged) fake.outcome = DeleteResult.Success(emptyMap())
                    return fake.cleaner.deletePost(postNo, postType, solveCaptcha, deleteQuestionPosts)
                }
            })
            engine.resumeDeletion(task().copy(twoCaptchaApiKey = "test-key"))
            withTimeout(5_000) {
                while (store.getAll().singleOrNull()?.state != DeleteTaskState.CAPTCHA_REQUIRED) delay(5)
            }
            val attemptsBefore = fake.automaticAttempts.size
            charged = true
            assertTrue(engine.useTwoCaptchaKey("test-key"))
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            assertTrue(fake.automaticAttempts.size > attemptsBefore)
            assertTrue(fake.successfulDeletes.contains("post-1"))
        }
    }

    @Test fun removingTheKeyMidRunSwitchesTheRunningTaskToManualCaptcha() = runBlocking {
        withEngine(Fake()) { engine, _, fake ->
            val entered = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            })
            engine.resumeDeletion(task().copy(twoCaptchaApiKey = "test-key"))
            withTimeout(5_000) { entered.await() }
            assertTrue(engine.isTwoCaptchaConfigured.value)
            assertFalse(engine.useTwoCaptchaKey(""))
            assertFalse(engine.isTwoCaptchaConfigured.value)
            assertEquals("", fake.configuredKey)
        }
    }

    @Test fun initialSaveFailureDoesNotStartDeletion() = runBlocking {
        withEngine(Fake()) { _, store, fake ->
            val rejectingStore = object : DeleteTaskStorePort by store {
                override fun save(task: DeleteTaskProgress): Boolean = false
            }
            val engine = DccleanerExecutionEngine(rejectingStore, timing = DccleanerExecutionTiming.Immediate)
            try {
                engine.setCleaner(fake.cleaner)
                engine.resumeDeletion(task())
                assertFalse(engine.isDeleting.value)
                assertFalse(engine.isCompleted.value)
                assertNotNull(engine.errorMessage.value)
                assertTrue(fake.deleted.isEmpty())
            } finally { engine.close() }
        }
    }

    @Test fun restartWaitsForCancelledRequestToFinish() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            var requests = 0
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    if (++requests == 1) {
                        entered.complete(Unit)
                        try { awaitCancellation() }
                        finally { withContext(NonCancellable) { release.await(); finished.complete(Unit) } }
                    }
                    assertTrue(finished.isCompleted, "A replacement must not overlap the old request")
                    return fake.cleaner.deletePost(postNo, postType, solveCaptcha, deleteQuestionPosts)
                }
            })
            engine.resumeDeletion(task())
            withTimeout(5_000) { entered.await() }
            engine.stopDeletion()
            engine.resumeDeletion(task())
            try {
                delay(50)
                assertEquals(1, requests)
            } finally { release.complete(Unit) }
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
            assertEquals(2, requests)
            assertTrue(store.getAll().isEmpty())
        }
    }

    @Test fun logoutWaitsForCancellationAndPreservesResumeRecord() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            val cleanedUp = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    entered.complete(Unit)
                    try { awaitCancellation() }
                    finally { cleanedUp.complete(Unit) }
                }
            })
            engine.resumeDeletion(task())
            withTimeout(5_000) { entered.await(); engine.stopForLogout() }
            assertTrue(cleanedUp.isCompleted)
            assertFalse(engine.isDeleting.value)
            assertNull(engine.getCurrentTaskId())
            assertEquals("", engine.getCurrentTaskLoginId())
            val checkpoint = store.getAll().single()
            assertEquals(DeleteTaskState.INTERRUPTED, checkpoint.state)
            assertEquals(0, checkpoint.queueCursor)
            engine.setCleaner(fake.cleaner)
            engine.resumeDeletion(checkpoint)
            awaitIdle(engine)
            assertTrue(engine.isCompleted.value)
        }
    }

    @Test fun handOffStopWaitsForInFlightRequestAndDropsTheRecord() = runBlocking {
        withEngine(Fake()) { engine, store, fake ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            engine.setCleaner(object : CleanerPort by fake.cleaner {
                override suspend fun deletePost(postNo: String, postType: String,
                    solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                    entered.complete(Unit)
                    try { awaitCancellation() }
                    finally { withContext(NonCancellable) { release.await() } }
                }
            })
            // 최근 글부터 삭제처럼 서버가 못 하는 조건이어도 똑같이 끝낸다. 조건 안내는 삭제 시작 창이 한다.
            engine.resumeDeletion(task(newest = true))
            withTimeout(5_000) { entered.await() }
            val stop = async { engine.stopForHandOff() }
            delay(50)
            // 보내던 삭제 요청이 끝나기 전에는 서버 작업을 시작할 수 없게 기다린다.
            assertFalse(stop.isCompleted)
            release.complete(Unit)
            assertTrue(withTimeout(5_000) { stop.await() })
            assertFalse(engine.isDeleting.value)
            // 초고속으로 새로 시작하므로 이어하기 기록은 남기지 않는다.
            assertTrue(store.getAll().isEmpty())
            engine.useTwoCaptchaKey("test-key")
            assertTrue(store.getAll().isEmpty())
            assertFalse(engine.stopForHandOff())
        }
    }

    @Test fun failedLogoutCheckpointCanBeRetriedWithoutDiscardingTheTask() = runBlocking {
        withEngine(Fake()) { _, store, fake ->
            var allowSave = true
            val guardedStore = object : DeleteTaskStorePort by store {
                override fun save(task: DeleteTaskProgress) = allowSave && store.save(task)
            }
            val engine = DccleanerExecutionEngine(guardedStore, timing = DccleanerExecutionTiming.Immediate)
            val entered = CompletableDeferred<Unit>()
            try {
                engine.setCleaner(object : CleanerPort by fake.cleaner {
                    override suspend fun deletePost(postNo: String, postType: String,
                        solveCaptcha: Boolean, deleteQuestionPosts: Boolean): DeleteResult {
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                })
                engine.resumeDeletion(task())
                withTimeout(5_000) { entered.await() }
                allowSave = false
                assertFailsWith<IllegalStateException> { engine.stopForLogout() }
                assertNotNull(engine.getCurrentTaskId())
                assertFalse(engine.isDeleting.value)
                allowSave = true
                engine.stopForLogout()
                assertNull(engine.getCurrentTaskId())
                assertEquals(DeleteTaskState.INTERRUPTED, store.getAll().single().state)
            } finally { engine.close() }
        }
    }

    private val twoPosts = listOf(CollectedPost("post-1"), CollectedPost("post-2"))

    private fun task(newest: Boolean = false) = DeleteTaskProgress(
        loginId = "test-user", deleteType = "posting", selectedGalleries = listOf("chosen"),
        galleryMap = mapOf("chosen" to "Chosen"), deleteNewestFirst = newest, recordGuestbookLog = false)

    private suspend fun awaitIdle(engine: DccleanerExecutionEngine) {
        withTimeout(5_000) { while (engine.isDeleting.value) delay(5) }
    }

    private suspend fun withEngine(fake: Fake,
        verify: suspend (DccleanerExecutionEngine, DesktopDeleteTaskStore, Fake) -> Unit) {
        val root = Files.createTempDirectory("dccleaner-execution-test").toFile()
        val store = DesktopDeleteTaskStore(root)
        val engine = DccleanerExecutionEngine(store, timing = DccleanerExecutionTiming.Immediate)
        try {
            engine.setCleaner(fake.cleaner)
            verify(engine, store, fake)
        } finally {
            engine.close()
            root.deleteRecursively()
        }
    }
}
