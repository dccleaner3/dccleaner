package com.dccleaner.app.network

import com.dccleaner.app.model.PostListResult
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class ResponseOwnershipTest {
    @Test fun missingGalleryIsEmptyButWrongSelectionStillFails(): Unit = runBlocking {
        val html = """<body><div class='option_sort gallog'><div><ul>
            <li data-value=''>전체 보기</li><li data-value='other'>다른 갤러리</li>
            </ul></div></div><script>select_ul(box, 'filter', '');</script>
            <div class='bottom_paging_box'><a href='?cno=other&p=9'>끝</a></div>
            <ul class='cont_listbox'><li data-no='wrong'></li></ul></body>"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(html.toResponseBody()).build()
        }.build()
        val cleaner = Cleaner()
        Cleaner::class.java.getDeclaredField("client").apply { isAccessible = true }.set(cleaner, client)
        try {
            assertEquals(1, cleaner.getPageCount("chosen", "posting"))
            val missing = cleaner.getPostList("chosen", "posting", 1) as PostListResult.Success
            assertTrue(missing.posts.isEmpty())
            missing.requireRequestedGallery()
            assertFalse((cleaner.getPostList("other", "posting", 1) as PostListResult.Success).requestedGalleryMatched)
            assertFalse(gallogRequestedGalleryUnavailable(
                org.jsoup.Jsoup.parse(html), html.replace("'filter', ''", "'filter', 'other'"), "chosen"
            ))
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun blankSuccessfulDeleteResponseIsExcludedImmediately(): Unit = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("".toResponseBody()).build()
        }.build()
        val cleaner = Cleaner()
        Cleaner::class.java.getDeclaredField("client").apply { isAccessible = true }.set(cleaner, client)
        try {
            assertIs<com.dccleaner.app.model.DeleteResult.Excluded>(
                withTimeout(5_000) { cleaner.deletePost("post-1", "posting", false, false) }
            )
            assertEquals(1, requests.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun questionPostConfirmationStartsUpfrontAndFallsBackIfRejected(): Unit = runBlocking {
        val confirmationFlags = mutableListOf<Boolean>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = chain.request().body as FormBody
            confirmationFlags += (0 until body.size).any { body.name(it) == "del_limit_ok" && body.value(it) == "1" }
            val response = if (confirmationFlags.size <= 2) """{"result":"confirm","msg":"확인이 필요합니다"}"""
                else """{"result":"success"}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(response.toResponseBody()).build()
        }.build()
        val cleaner = Cleaner()
        Cleaner::class.java.getDeclaredField("client").apply { isAccessible = true }.set(cleaner, client)
        try {
            assertIs<com.dccleaner.app.model.DeleteResult.Success>(
                withTimeout(5_000) { cleaner.deletePost("question-1", "posting", false, true) }
            )
            assertEquals(listOf(true, false, true), confirmationFlags)
            assertIs<com.dccleaner.app.model.DeleteResult.Success>(
                withTimeout(5_000) { cleaner.deletePost("question-2", "posting", false, true) }
            )
            assertEquals(listOf(true, false, true, true), confirmationFlags)
            assertIs<com.dccleaner.app.model.DeleteResult.Success>(
                withTimeout(5_000) { cleaner.deletePost("normal", "posting", false, false) }
            )
            assertEquals(listOf(true, false, true, true, false), confirmationFlags)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun earlyReturnsCloseUnconsumedBodies(): Unit = runBlocking {
        val closed = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = object : ResponseBody() {
                private val trackedSource = object : ForwardingSource(Buffer().writeUtf8("unread body")) {
                    override fun close() { closed.incrementAndGet(); super.close() }
                }.buffer()
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = 11
                override fun source() = trackedSource
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (chain.request().url.encodedPath == "/missing") 404 else 403)
                .message("test").body(body).build()
        }.build()
        val cleaner = Cleaner()
        Cleaner::class.java.getDeclaredField("client").apply { isAccessible = true }.set(cleaner, client)
        try {
            assertEquals(-1, cleaner.getPageCount("chosen", "posting"))
            assertEquals(1, closed.get())
            assertEquals(PostListResult.Blocked, cleaner.getPostList("chosen", "posting", 1))
            assertEquals(2, closed.get())
            assertEquals(cleaner.getUserId(), cleaner.getPostWriterUid("https://example.invalid/missing"))
            assertEquals(3, closed.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
