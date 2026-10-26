package com.dccleaner.app.model

import androidx.compose.runtime.Immutable

const val PROXY_BALANCE_EXHAUSTED_MESSAGE = "충전된 삭제 개수를 모두 사용해 작업을 멈췄습니다"
const val PRIVACY_POLICY_URL = "https://github.com/dccleaner3/dccleaner/blob/main/PRIVACY.md"

@Immutable
data class ProxyJob(
    val id: String,
    val status: String,
    val deleteType: String,
    val reservedCount: Long,
    /** Successful deletions settled by the server; null until settlement. */
    val consumedCount: Long?,
    val deletedCount: Int,
    val skippedCount: Int,
    val progress: Float,
    val galleriesDone: Int,
    val galleriesTotal: Int,
    val currentGallery: String,
    val speedPerHour: Int,
    val recentSpeedPerHour: Int,
    val etaSeconds: Long,
    val statusMessage: String,
    val logTail: List<String>,
    val cancelRequested: Boolean,
    val error: String?,
    /** Last server update; a running job whose value stops changing is treated as stalled. */
    val heartbeatAtMillis: Long? = null,
    /** 진행 중인 갤러리에서 처리한 개수와 전체 개수. 전체를 모르거나 갤러리 사이면 null. */
    val galleryProcessed: Int? = null,
    val galleryTotal: Int? = null,
    /** 글+댓글 이어 삭제면 지금 단계(1부터)와 전체 단계 수. 한 종류만 지우면 1/1. */
    val phaseIndex: Int = 1,
    val phaseCount: Int = 1,
    /** 이어 삭제 1단계일 때 다음에 지울 종류("posting"·"comment"). 아니면 null. */
    val nextDeleteType: String? = null
) {
    val isActive: Boolean
        get() = status == "queued" || status == "running"

    val needsSettlement: Boolean
        get() = status == "cancelled" && consumedCount == null

    /** The server finishes normally but stops early when the reserved balance runs out. */
    val stoppedByBalance: Boolean
        get() = status == "completed" && statusMessage.trim() == PROXY_BALANCE_EXHAUSTED_MESSAGE

    val returnedCount: Long?
        get() = consumedCount?.let { (reservedCount - it).coerceAtLeast(0L) }
}

@Immutable
data class CreditLedgerEntry(
    /** Deletion count; positive for purchases and returns. */
    val delta: Long,
    val kind: String,
    val note: String,
    val createdAt: String
)

@Immutable
data class CreditPurchase(
    val id: String,
    val requestedCount: Int,
    val creditedCount: Long,
    val amountWon: Int,
    val pricingVersion: String,
    /** 휴대폰 채팅방에 손으로 칠 수 있는 짧은 신청 코드(예: K7Q3). 이전 서버는 주지 않는다. */
    val code: String? = null
)

/**
 * 초고속 삭제를 충전한 사람들의 최근 활동. 실제로 지급된 충전만 세고, 누가 얼마를 냈는지는 서버가 주지 않는다.
 * 사람 수는 계정 기준이다.
 */
@Immutable
data class PurchaseActivity(
    val buyers24h: Int,
    val buyers7d: Int,
    /** 최근 7일 안의 지급, 최신순 최대 5건. */
    val recent: List<RecentCreditPurchase>
)

/** 사람 수가 이보다 적으면 오히려 '사는 사람이 없다'로 읽혀 요약을 띄우지 않는다. */
private const val MIN_BUYERS_FOR_SUMMARY = 3

/** 요약을 띄울 만큼 활동이 있는지. 시각과 상관없이 사람 수로만 정해진다. */
val PurchaseActivity.hasSummary: Boolean
    get() = buyers24h >= MIN_BUYERS_FOR_SUMMARY || buyers7d >= MIN_BUYERS_FOR_SUMMARY

/**
 * 견적·충전 화면에 보여 줄 요약: 사람 수와(있으면) 마지막 충전 시각. 좁은 화면에서 둘을 따로 줄바꿈할 수 있게 나눠 준다.
 * 보여 줄 만큼 활동이 없으면 null(아무것도 띄우지 않는다).
 */
fun PurchaseActivity.summary(nowMillis: Long): List<String>? {
    val people = when {
        buyers24h >= MIN_BUYERS_FOR_SUMMARY -> "최근 24시간 동안 ${buyers24h}명이 충전했어요"
        buyers7d >= MIN_BUYERS_FOR_SUMMARY -> "최근 7일 동안 ${buyers7d}명이 충전했어요"
        else -> return null
    }
    // 한 시간 안에 충전이 있었을 때만 덧붙인다. 오래된 시각은 활동이 뜸해 보이게만 한다. 1분 안은 초를 세지 않고 '방금'이다.
    // 기기 시계를 되돌려 경과 시간이 음수면 시각을 알 수 없으니 붙이지 않는다('방금'으로 부풀리지 않는다).
    val latestSeconds = recent.firstOrNull()?.let { (nowMillis - it.creditedAtMillis) / 1_000 }
        ?.takeIf { it in 0 until 3_600 } ?: return listOf(people)
    return listOf(people, if (latestSeconds < 60) "방금 충전" else "마지막 충전 ${latestSeconds / 60}분 전")
}

/** 충전 탭 '최근 충전' 한 줄. 누가 얼마를, 몇 개를 충전했는지는 서버가 주지 않는다(가격표가 공개라 개수로 매출을 셀 수 있다). */
@Immutable
data class RecentCreditPurchase(
    /** 기기 시계로 환산한 지급 시각. 서버가 계산한 경과 초에서 구해 기기·서버 시계가 달라도 'n분 전'이 맞다. */
    val creditedAtMillis: Long
)

/** 최근 충전 기록의 경과 시간 표시. 음수(기기 시계를 되돌림)는 방금으로 본다. 1분 안은 위 요약('방금 충전')과 맞춰 초를 세지 않는다. */
fun formatTimeAgo(seconds: Long): String = when {
    seconds < 60 -> "방금"
    seconds < 3_600 -> "${seconds / 60}분 전"
    seconds < 86_400 -> "${seconds / 3_600}시간 전"
    else -> "${seconds / 86_400}일 전"
}

/** Mirrors start_deletion_job.p_config; field meanings match startDeletion arguments. */
@Immutable
data class ProxyJobConfig(
    val galleries: List<String>,
    val deleteNewestFirst: Boolean,
    val deleteQuestionPosts: Boolean,
    val recommendFilterEnabled: Boolean,
    val minRecommendToKeep: Int,
    val commentFilterEnabled: Boolean,
    val minCommentToKeep: Int,
    val viewFilterEnabled: Boolean,
    val minViewToKeep: Int,
    val postContentFilterEnabled: Boolean,
    val postContentRegex: String,
    val commentContentFilterEnabled: Boolean,
    val commentContentRegex: String,
    val myPostFilterEnabled: Boolean,
    val dcconOnlyFilterEnabled: Boolean,
    val dateFilterEnabled: Boolean,
    val minPostAgeDays: Int,
    val excludedPostNumbers: List<String>,
    val recordGuestbookLog: Boolean,
    /** 초고속 이어 삭제: 이 단계가 끝나면 서버가 같은 작업에서 이어 지울 두 번째 단계. */
    val followUp: ProxyFollowUp? = null
)

/** 초고속 이어 삭제의 두 번째 단계. 나머지 조건은 위 설정을 같이 쓰고, 종류별로 따로인 것만 담는다. */
@Immutable
data class ProxyFollowUp(
    val deleteType: String,
    val galleries: List<String>,
    val dateFilterEnabled: Boolean,
    val minPostAgeDays: Int
)

class ProxyCleanerException(val code: String) : Exception(code)

/** Whole won, rounded half up, with thousands separators: 29894.0 -> "29,894원". */
fun formatWon(value: Double): String {
    val rounded = kotlin.math.floor(kotlin.math.abs(value) + 0.5).toLong()
    val digits = rounded.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (value < 0 && rounded != 0L) "-" else "") + digits + "원"
}

/** Per-deletion price, which is below one won and so keeps its decimals: 0.3 -> "0.3원". */
fun formatUnitPrice(value: Double): String {
    val hundredths = kotlin.math.floor(kotlin.math.abs(value) * 100 + 0.5).toLong()
    val fraction = when {
        hundredths % 100 == 0L -> ""
        hundredths % 10 == 0L -> ".${hundredths % 100 / 10}"
        else -> ".${(hundredths % 100).toString().padStart(2, '0')}"
    }
    return "${hundredths / 100}${fraction}원"
}

/** The server treats an empty gallery list as "every gallery". */
fun proxyGalleryScope(selectedGalleries: List<String>, availableGalleries: Collection<String>): List<String> =
    if (availableGalleries.isNotEmpty() && selectedGalleries.toSet() == availableGalleries.toSet()) emptyList()
    else selectedGalleries

fun proxyCleanerErrorMessage(code: String): String = knownProxyCleanerErrorMessage(code)
    ?: if (code.startsWith("permission denied")) "초고속 삭제 서버가 아직 준비 중입니다. 잠시 후 다시 시도해 주세요."
    else "초고속 삭제 요청에 실패했습니다. 잠시 후 다시 시도해 주세요."

private fun knownProxyCleanerErrorMessage(code: String): String? = when (code) {
    "insufficient_credits" -> "삭제 가능한 개수가 부족합니다. 충전 후 다시 시도해 주세요."
    "server_upgrade_required", "invalid_server_response" -> "초고속 삭제 서버 업데이트가 필요합니다. 잠시 후 다시 시도해 주세요."
    "purchase_already_credited" -> "이미 충전이 완료된 신청입니다. 새로고침해 주세요."
    "purchase_expired" -> "충전 견적이 만료되었습니다. 다시 눌러 최신 견적을 확인해 주세요."
    "pricing_unavailable" -> "가격 정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요. (pricing_unavailable)"
    "purchase_pending_limit" ->
        "결제 대기 중인 충전 신청이 너무 많습니다(최대 5개). 신청은 24시간 뒤 만료되며, 급하면 사용자 코드와 함께 문의해 주세요."
    "purchase_rate_limit" -> "충전 신청을 너무 자주 했습니다. 잠시 후 다시 시도해 주세요."
    "job_rate_limit" -> "작업은 1분에 3번까지 시작할 수 있어요. 잠시 후 다시 시도해 주세요."
    "job_limit_reached" -> "초고속 삭제는 계정 5개까지 동시에 할 수 있어요. 진행 중인 작업이 끝난 뒤 시작해 주세요."
    "event_rate_limit" -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
    "invalid_requested_count" -> "충전 개수는 1개 이상 10,000,000개 이하로 입력해 주세요."
    "purchase_request_conflict" -> "같은 신청에 다른 수량이 들어갔습니다. 새로고침 후 다시 시도해 주세요."
    "balance_exceeds_engine_limit" -> "한 번에 보유할 수 있는 개수(2,147,483,647개)를 넘었습니다."
    "negative_balance_requires_review" -> "잔고 확인이 필요합니다. 사용자 코드와 함께 문의해 주세요."
    "accounting_upgrade_required" -> "초고속 삭제 서버가 아직 준비 중입니다. 잠시 후 다시 시도해 주세요."
    "job_already_active" -> "이 계정은 이미 초고속 삭제가 진행 중이에요. 끝난 뒤 다시 시도해 주세요."
    "invalid_config" -> "삭제 설정을 서버가 받아들이지 않았습니다. 조건을 확인해 주세요."
    "invalid_delete_type" -> "삭제 대상을 확인해 주세요. 게시글 또는 댓글을 선택해 주세요."
    "unsupported_option" -> "초고속 삭제에서 지원하지 않는 조건입니다. 삭제 조건을 확인해 주세요."
    "unsupported_regex_filter" -> "정규식 조건은 직접 삭제에서만 사용할 수 있어요. 초고속 삭제를 끄고 다시 시도해 주세요."
    "unsupported_newest_first" -> "최신순 삭제는 직접 삭제에서만 사용할 수 있어요. 초고속 삭제를 끄고 다시 시도해 주세요."
    "invalid_credentials" -> "계정 정보를 암호화하지 못했습니다. 앱을 최신 버전으로 업데이트해 주세요."
    "profile_missing" -> "사용자 코드를 확인하지 못했습니다. 충전 탭을 열어 새로고침해 주세요."
    "job_not_found" -> "작업을 찾지 못했습니다. 이미 끝났을 수 있습니다."
    "not_authenticated", "session_invalid" ->
        "초고속 삭제 계정 정보가 만료되었습니다. 사용자 코드와 함께 문의해 주세요."
    "no_account" -> "삭제 가능한 개수가 없습니다. 충전 탭에서 먼저 충전해 주세요."
    "anonymous_provider_disabled" -> "초고속 삭제 서버가 아직 준비 중입니다(익명 로그인 꺼짐). 잠시 후 다시 시도해 주세요."
    "signup_failed" -> "사용자 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."
    "token_read_failed" ->
        "기기에 저장된 사용자 코드를 읽지 못했습니다. 잔고 보호를 위해 진행을 멈췄습니다. 앱을 다시 시작해 주세요."
    "token_storage_failed" ->
        "사용자 코드를 기기에 저장하지 못했습니다. 잔고가 사라질 수 있어 진행을 멈췄습니다."
    "network" -> "서버에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요."
    else -> null
}

/** Public server messages only. Old rows may contain internal details or raw exceptions. */
fun proxyJobMessage(message: String, error: Boolean = false): String = when (val value = message.trim()) {
    "", "작업 준비 중", "로그인 중", "갤러리 목록 확인 중", "삭제 진행 중", "삭제 중", "삭제 완료",
    "캡챠 해결 중", "작업을 잠시 멈췄습니다.", "작업 상태를 확인하고 있습니다.",
    "작업이 접수되었습니다. 진행 상태를 확인하고 있습니다.",
    "작업 소유권이 변경되어 중단했습니다.", "사용자가 중단했습니다.", "사용자 취소",
    "서버 연결이 불안정해 작업을 잠시 멈췄습니다.", "작업 시간 한도를 초과했습니다.",
    "삭제 조건 또는 암호화된 계정 정보가 올바르지 않습니다.", "서버 오류로 중단되었습니다.",
    "로그인에 실패했습니다. 아이디와 비밀번호를 확인해 주세요.",
    "서버 캡챠 설정 오류입니다.", "서버 캡챠 설정 오류입니다. 운영자에게 문의해 주세요.",
    "갤러리 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
    "삭제 응답을 확인하지 못했습니다.", "삭제 작업이 중단되었습니다.", "캡챠 재시도 한도를 초과했습니다.",
    "삭제 응답을 확인하고 있습니다. 사용자 코드와 함께 문의해 주세요.", "삭제를 계속할 수 없어 작업을 멈췄습니다.",
    "30분 동안 지울 항목을 찾지 못해 작업을 멈췄습니다. 삭제 조건을 확인해 주세요.",
    PROXY_BALANCE_EXHAUSTED_MESSAGE -> value
    else -> knownProxyCleanerErrorMessage(value)
        ?: if (error) "서버 오류로 중단되었습니다." else "작업 상태를 확인하고 있습니다."
}

fun creditLedgerKindLabel(kind: String): String = when (kind) {
    "charge" -> "충전"
    "reserve" -> "작업 예약"
    "refund" -> "반환"
    "adjust" -> "조정"
    else -> kind
}
