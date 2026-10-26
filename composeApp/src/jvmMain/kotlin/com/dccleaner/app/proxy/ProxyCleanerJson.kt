package com.dccleaner.app.proxy

import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.ProxyCleanerException
import com.dccleaner.app.model.ProxyJob
import com.dccleaner.app.model.ProxyJobConfig
import com.dccleaner.app.model.proxyJobMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val JOB_COLUMNS =
    "id,status,delete_type,reserved_count,deleted_count,skipped_count,progress,galleries_done,galleries_total," +
        "current_gallery,speed_per_hour,recent_speed_per_hour,eta_seconds,status_message," +
        "cancel_requested,error,consumed_count,heartbeat_at,gallery_processed,gallery_total,config"

internal fun ProxyJobConfig.toJson(): JsonObject = buildJsonObject {
    put("galleries", JsonArray(galleries.map(::JsonPrimitive)))
    put("delete_newest_first", deleteNewestFirst)
    put("delete_question_posts", deleteQuestionPosts)
    put("recommend_filter_enabled", recommendFilterEnabled)
    put("min_recommend_to_keep", minRecommendToKeep)
    put("comment_filter_enabled", commentFilterEnabled)
    put("min_comment_to_keep", minCommentToKeep)
    put("view_filter_enabled", viewFilterEnabled)
    put("min_view_to_keep", minViewToKeep)
    put("post_content_filter_enabled", postContentFilterEnabled)
    put("post_content_regex", postContentRegex)
    put("comment_content_filter_enabled", commentContentFilterEnabled)
    put("comment_content_regex", commentContentRegex)
    put("my_post_filter_enabled", myPostFilterEnabled)
    put("dccon_only_filter_enabled", dcconOnlyFilterEnabled)
    put("date_filter_enabled", dateFilterEnabled)
    put("min_post_age_days", minPostAgeDays)
    put("excluded_post_numbers", JsonArray(excludedPostNumbers.map(::JsonPrimitive)))
    put("record_guestbook_log", recordGuestbookLog)
    // 이어 삭제일 때만 넣는다. 예전 서버·앱과 같은 모양을 유지하려고 없으면 키도 없다.
    followUp?.let { next ->
        put("follow_up", buildJsonObject {
            put("delete_type", next.deleteType)
            put("galleries", JsonArray(next.galleries.map(::JsonPrimitive)))
            // 최근부터 삭제는 초고속 삭제가 지원하지 않아 늘 끈다.
            put("delete_newest_first", false)
            put("date_filter_enabled", next.dateFilterEnabled)
            put("min_post_age_days", next.minPostAgeDays)
        })
    }
}

internal fun JsonObject.toProxyJob(): ProxyJob = ProxyJob(
    id = string("id"),
    status = string("status"),
    deleteType = string("delete_type"),
    reservedCount = requiredInteger("reserved_count").also { if (it < 0) invalidResponse() },
    consumedCount = this["consumed_count"]?.takeUnless { it is JsonNull }?.let {
        requiredInteger("consumed_count").also { count -> if (count < 0 || count > requiredInteger("reserved_count")) invalidResponse() }
    },
    deletedCount = long("deleted_count").toInt(),
    skippedCount = long("skipped_count").toInt(),
    progress = (this["progress"].primitiveOrNull()?.doubleOrNull ?: 0.0).toFloat().coerceIn(0f, 1f),
    galleriesDone = long("galleries_done").toInt(),
    galleriesTotal = long("galleries_total").toInt(),
    currentGallery = string("current_gallery"),
    speedPerHour = long("speed_per_hour").toInt(),
    recentSpeedPerHour = long("recent_speed_per_hour").toInt(),
    etaSeconds = long("eta_seconds"),
    statusMessage = proxyJobMessage(string("status_message")),
    logTail = emptyList(),
    cancelRequested = this["cancel_requested"].primitiveOrNull()?.booleanOrNull ?: false,
    error = this["error"].primitiveOrNull()?.contentOrNull?.takeIf(String::isNotBlank)
        ?.let { proxyJobMessage(it, error = true) },
    heartbeatAtMillis = this["heartbeat_at"].primitiveOrNull()?.contentOrNull
        ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
    galleryProcessed = this["gallery_processed"].primitiveOrNull()?.longOrNull?.takeIf { it in 0..Int.MAX_VALUE }?.toInt(),
    galleryTotal = this["gallery_total"].primitiveOrNull()?.longOrNull?.takeIf { it in 0..Int.MAX_VALUE }?.toInt(),
    phaseIndex = phaseOf("phase_index"),
    phaseCount = phaseOf("phase_count"),
    nextDeleteType = (this["config"] as? JsonObject)?.get("follow_up")?.let { it as? JsonObject }
        ?.get("delete_type").primitiveOrNull()?.contentOrNull?.takeIf { it == "posting" || it == "comment" }
)

/**
 * 이어 삭제 단계. 서버는 2단계로 넘어갈 때 config에 phase_index·phase_count를 적고 follow_up을 지운다.
 * 1단계에는 follow_up만 있으므로 1/2로 본다. 둘 다 없으면 한 종류 작업(1/1).
 */
private fun JsonObject.phaseOf(key: String): Int {
    val config = this["config"] as? JsonObject ?: return 1
    // 2.0처럼 실수로 와도 읽는다. 범위를 벗어난 값은 없는 것으로 본다.
    config.long(key).takeIf { it in 1..2 }?.let { return it.toInt() }
    val hasFollowUp = config["follow_up"] is JsonObject
    return if (key == "phase_count" && hasFollowUp) 2 else 1
}

internal fun JsonObject.toLedgerEntry(): CreditLedgerEntry = CreditLedgerEntry(
    delta = requiredInteger("delta_count"),
    kind = string("kind"),
    note = string("note"),
    createdAt = string("created_at").toLocalTimestamp()
)

internal fun JsonObject.toCreditPurchase(): CreditPurchase {
    val requested = requiredInteger("requested_count")
    val credited = requiredInteger("credited_count")
    val amount = requiredInteger("amount_won")
    if (requested !in 1..Int.MAX_VALUE.toLong() || credited < requested || amount !in 1..Int.MAX_VALUE.toLong() ||
        string("id").isBlank() || string("pricing_version").isBlank()) invalidResponse()
    val code = this["code"].primitiveOrNull()?.contentOrNull?.takeIf { CHARGE_CODE.matches(it) }
    return CreditPurchase(string("id"), requested.toInt(), credited, amount.toInt(), string("pricing_version"), code)
}

internal fun JsonObject.requiredInteger(key: String): Long =
    this[key].primitiveOrNull()?.contentOrNull?.toLongOrNull() ?: invalidResponse()

private fun invalidResponse(): Nothing = throw ProxyCleanerException("invalid_server_response")

/** 서버(_new_charge_code)가 만드는 신청 코드 모양. 다른 값이 오면 코드 없이 보여 준다. */
private val CHARGE_CODE = Regex("[ACDEFGHJKLMNPQRTUVWXY][34679][ACDEFGHJKLMNPQRTUVWXY][34679]")
private val LEDGER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

internal fun String.toLocalTimestamp(zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(this).atZoneSameInstant(zone).format(LEDGER_TIME_FORMAT) }.getOrDefault(this)

private fun JsonElement?.primitiveOrNull(): JsonPrimitive? =
    if (this == null || this is JsonNull) null else (this as? JsonPrimitive)

private fun JsonObject.string(key: String): String = this[key].primitiveOrNull()?.contentOrNull.orEmpty()

// Numeric columns may arrive as floats (e.g. speeds) depending on the column type.
private fun JsonObject.long(key: String): Long = this[key].primitiveOrNull()?.let {
    it.longOrNull ?: it.doubleOrNull?.toLong()
} ?: 0L

internal fun JsonElement.stringValue(): String = jsonPrimitive.content
