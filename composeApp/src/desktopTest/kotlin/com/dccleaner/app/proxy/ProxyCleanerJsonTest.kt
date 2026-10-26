package com.dccleaner.app.proxy

import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.ProxyCleanerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import com.dccleaner.app.model.ProxyFollowUp
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class ProxyCleanerJsonTest {
    @Test
    fun configUsesServerContractKeys() {
        val json = sampleConfig().toJson()

        assertEquals(
            setOf(
                "galleries", "delete_newest_first", "delete_question_posts", "recommend_filter_enabled",
                "min_recommend_to_keep", "comment_filter_enabled", "min_comment_to_keep", "view_filter_enabled",
                "min_view_to_keep", "post_content_filter_enabled", "post_content_regex",
                "comment_content_filter_enabled", "comment_content_regex", "my_post_filter_enabled",
                "dccon_only_filter_enabled", "date_filter_enabled", "min_post_age_days", "excluded_post_numbers",
                "record_guestbook_log"
            ),
            json.keys
        )
        assertEquals(listOf("gall_a", "gall_b"), (json["galleries"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("true", json["recommend_filter_enabled"]?.jsonPrimitive?.content)
        assertEquals(3, json["min_recommend_to_keep"]?.jsonPrimitive?.content?.toInt())
        assertEquals("/광고/i", json["post_content_regex"]?.jsonPrimitive?.content)
        assertEquals(30, json["min_post_age_days"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun parsesJobRowWithNullsAndFloats() {
        val job = Json.parseToJsonElement(
            """
            {"id":"j1","status":"completed","delete_type":"comment","reserved_count":5000,"consumed_count":121,"deleted_count":121,
             "skipped_count":3,"progress":0.25,"galleries_done":1,"galleries_total":4,"current_gallery":null,
             "speed_per_hour":9500.7,"recent_speed_per_hour":10400,"eta_seconds":null,"status_message":"삭제 중",
             "log_tail":["a","b"],"cancel_requested":false,"error":null}
            """.trimIndent()
        ).jsonObject.toProxyJob()

        assertEquals("completed", job.status)
        assertEquals(0.25f, job.progress)
        assertEquals("", job.currentGallery)
        assertEquals(9500, job.speedPerHour)
        assertEquals(0L, job.etaSeconds)
        assertEquals(emptyList(), job.logTail)
        assertEquals("삭제 중", job.statusMessage)
        assertNull(job.error)
        assertEquals(4_879L, job.returnedCount)
    }

    @Test
    fun oldServerRowsCannotExposeProviderLogsOrExceptionText() {
        val job = Json.parseToJsonElement(
            """{"id":"j1","status":"failed","reserved_count":10,"consumed_count":2,"deleted_count":2,
                "speed_per_hour":10400,"status_message":"외부 서비스 캡챠 해결 중",
                "log_tail":["외부 서비스 코드 준비", "java.lang.IllegalStateException: internal failure"],
                "error":"java.lang.IllegalStateException: internal failure"}"""
        ).jsonObject.toProxyJob()
        assertEquals("작업 상태를 확인하고 있습니다.", job.statusMessage)
        assertEquals(emptyList(), job.logTail)
        assertEquals("서버 오류로 중단되었습니다.", job.error)
        assertEquals(2, job.deletedCount)
        assertEquals(10400, job.speedPerHour)
        assertEquals(false, "log_tail" in JOB_COLUMNS.split(','))
    }

    @Test
    fun readsChargeCodeOnlyInServerShape() {
        fun purchase(code: String) = Json.parseToJsonElement(
            """{"id":"p","requested_count":10,"credited_count":10,"amount_won":5000,"pricing_version":"v",$code}"""
        ).jsonObject.toCreditPurchase().code
        assertEquals("K7Q3", purchase(""""code":"K7Q3""""))
        assertEquals(null, purchase(""""code":null"""))
        assertEquals(null, purchase(""""code":"O0I1""""))
        assertEquals(null, purchase(""""x":1"""))
    }

    @Test
    fun rejectsLegacyMoneyAndFractionalCountResponses() {
        listOf("""{"reserved":5000,"charged":36.3}""", """{"reserved_count":0.3}""").forEach {
            assertFailsWith<ProxyCleanerException> { Json.parseToJsonElement(it).jsonObject.toProxyJob() }
        }
        assertFailsWith<ProxyCleanerException> {
            Json.parseToJsonElement("""{"delta":5000}""").jsonObject.toLedgerEntry()
        }
        assertFailsWith<ProxyCleanerException> {
            Json.parseToJsonElement("""{"delta_count":1.5}""").jsonObject.toLedgerEntry()
        }
        assertEquals(-3L, Json.parseToJsonElement("""{"delta_count":-3}""").jsonObject.toLedgerEntry().delta)
    }

    @Test
    fun ledgerTimestampsUseLocalTime() {
        assertEquals("2026.10.04 14:05", "2026-10-04T05:05:10.123456+00:00".toLocalTimestamp(ZoneId.of("Asia/Seoul")))
        assertEquals("garbage", "garbage".toLocalTimestamp())
    }

    @Test
    fun followUpIsSentOnlyForPhasedJobs() {
        assertEquals(false, "follow_up" in sampleConfig().toJson().keys)
        val json = sampleConfig().copy(
            followUp = ProxyFollowUp(deleteType = "posting", galleries = listOf("gall_c"), dateFilterEnabled = false, minPostAgeDays = 5)
        ).toJson()
        val followUp = json["follow_up"] as JsonObject
        assertEquals(setOf("delete_type", "galleries", "delete_newest_first", "date_filter_enabled", "min_post_age_days"), followUp.keys)
        assertEquals("posting", followUp["delete_type"]?.jsonPrimitive?.content)
        assertEquals(listOf("gall_c"), (followUp["galleries"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("false", followUp["delete_newest_first"]?.jsonPrimitive?.content)
    }

    @Test
    fun readsPhaseFromJobConfig() {
        fun job(config: String) = Json.parseToJsonElement(
            """{"id":"j","status":"running","delete_type":"comment","reserved_count":10,"config":$config}"""
        ).jsonObject.toProxyJob()
        job("""{"galleries":[]}""").let { assertEquals(1, it.phaseIndex); assertEquals(1, it.phaseCount); assertNull(it.nextDeleteType) }
        job("""{"follow_up":{"delete_type":"posting"}}""").let {
            assertEquals(1, it.phaseIndex); assertEquals(2, it.phaseCount); assertEquals("posting", it.nextDeleteType)
        }
        job("""{"phase_index":2,"phase_count":2}""").let { assertEquals(2, it.phaseIndex); assertEquals(2, it.phaseCount) }
        // 예전 행처럼 config가 없으면 한 종류 작업이다.
        Json.parseToJsonElement("""{"id":"j","status":"running","reserved_count":1}""").jsonObject.toProxyJob()
            .let { assertEquals(1, it.phaseCount) }
        // 이상한 모양은 한 종류 작업으로 보고, 실수로 온 단계 번호는 읽는다.
        listOf("null", "\"x\"", "[]", """{"follow_up":"posting"}""", """{"phase_index":3,"phase_count":0}""").forEach {
            job(it).let { j -> assertEquals(1, j.phaseIndex); assertEquals(1, j.phaseCount); assertNull(j.nextDeleteType) }
        }
        job("""{"phase_index":2.0,"phase_count":2.0}""").let { assertEquals(2, it.phaseIndex); assertEquals(2, it.phaseCount) }
        job("""{"follow_up":{"delete_type":"bogus"}}""").let { assertEquals(2, it.phaseCount); assertNull(it.nextDeleteType) }
    }

    private fun sampleConfig() = ProxyJobConfig(
        galleries = listOf("gall_a", "gall_b"),
        deleteNewestFirst = false,
        deleteQuestionPosts = true,
        recommendFilterEnabled = true,
        minRecommendToKeep = 3,
        commentFilterEnabled = false,
        minCommentToKeep = 1,
        viewFilterEnabled = false,
        minViewToKeep = 1,
        postContentFilterEnabled = true,
        postContentRegex = "/광고/i",
        commentContentFilterEnabled = false,
        commentContentRegex = "",
        myPostFilterEnabled = false,
        dcconOnlyFilterEnabled = false,
        dateFilterEnabled = true,
        minPostAgeDays = 30,
        excludedPostNumbers = emptyList(),
        recordGuestbookLog = false
    )
}
