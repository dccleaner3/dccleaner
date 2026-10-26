package com.dccleaner.app.util

import java.text.NumberFormat

/**
 * 삭제 1건마다 반복되는 시작·확인·성공 줄을 묶어 [intervalMillis]마다 태그별 요약 한 줄로 남긴다.
 * 실패·캡챠·제외·오류처럼 원인 추적에 필요한 줄은 그대로 남긴다.
 */
class LogVolumeFilter(
    private val intervalMillis: Long = 5 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val counts = linkedMapOf<Pair<String, String>, Long>()
    private var windowStart = now()

    /** 지금 써야 할 (태그, 메시지) 줄들. 묶인 줄이면 비어 있고, 요약 시점이면 요약이 앞에 온다. */
    @Synchronized
    fun filter(tag: String, message: String): List<Pair<String, String>> {
        val out = if (now() - windowStart >= intervalMillis) drain() else ArrayList(1)
        // "삭제 성공" 바로 앞에 항상 찍히던 줄이라 요약도 남기지 않는다.
        if (tag == "Cleaner" && message == "삭제 응답 파싱 성공") return out
        val label = routineLabel(tag, message)
        if (label == null) out += tag to message
        else counts.merge(tag to label, 1L, Long::plus)
        return out
    }

    /** 남은 요약을 지금 내보낸다. */
    @Synchronized
    fun drain(): MutableList<Pair<String, String>> {
        windowStart = now()
        val out = counts.map { (key, n) -> key.first to "요약 ${key.second}: ${NumberFormat.getIntegerInstance().format(n)}건" }
            .toMutableList()
        counts.clear()
        return out
    }

    private fun routineLabel(tag: String, message: String): String? = when (tag) {
        "Cleaner" -> when {
            message.startsWith("삭제 성공 - postNo:") -> "삭제 성공"
            message.startsWith("글 삭제 시작 - ") -> "삭제 시작"
            else -> null
        }
        "Service" -> when {
            message.startsWith("글 삭제 시도 - ") -> "삭제 시도"
            message.startsWith("글 상세 정보 확인 중 - ") -> "글 상세 정보 확인"
            message.startsWith("글 작성자 UID 확인 중 - ") -> "작성자 확인"
            else -> null
        }
        else -> null
    }
}
