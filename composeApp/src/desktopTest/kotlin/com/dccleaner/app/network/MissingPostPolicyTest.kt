package com.dccleaner.app.network

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.*

class MissingPostPolicyTest {
    @Test fun notFoundStillUsesTheIntentionalOwnPostPolicy() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/login") { exchange ->
            val body = "<div id='login_box'><a class='logout'>Logout</a></div>".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val cleaner = Cleaner(loginBoxUrl = "$baseUrl/login", loginSubmitUrl = "$baseUrl/login")
            assertTrue(cleaner.login("TestUser", "test-password"))
            assertEquals("testuser", cleaner.getPostWriterUid("$baseUrl/missing"))
            val details = assertNotNull(cleaner.getPostDetails("$baseUrl/missing"))
            assertEquals(0, details.recommendCount)
            assertEquals(0, details.commentCount)
            assertEquals(0, details.viewCount)
        } finally { server.stop(0) }
    }
}
