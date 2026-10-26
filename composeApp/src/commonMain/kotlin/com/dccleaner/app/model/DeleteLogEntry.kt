package com.dccleaner.app.model

const val DELETE_PROGRESS_LOG_MARKER = "[[DCCLEANER_PROGRESS]]"

fun String.isDeleteProgressLog(): Boolean =
    substringAfter("] ", this).startsWith(DELETE_PROGRESS_LOG_MARKER)

fun String.deleteLogDisplayText(): String =
    replace(DELETE_PROGRESS_LOG_MARKER, "").trimStart()

internal fun List<String>.replaceDeleteProgressLog(timestampedMessage: String): List<String> {
    val progressIndex = indexOfLast { it.contains(DELETE_PROGRESS_LOG_MARKER) }
    if (progressIndex < 0) return this + timestampedMessage

    return toMutableList().apply {
        removeAt(progressIndex)
        add(timestampedMessage)
    }
}

private const val DELETE_PROGRESS_LINE_PREFIX = "🗑️"

/**
 * The proxy server strips the progress marker from log lines, so the last "🗑️" line stands in for
 * the live progress line. It stops being live once its gallery has completed or a new one started,
 * mirroring how the local engine replaces the marker line when a gallery finishes.
 */
internal fun List<String>.withDeleteProgressMarked(): List<String> {
    val index = indexOfLast { it.deleteLogMessage().removePrefix(DELETE_PROGRESS_LOG_MARKER).startsWith(DELETE_PROGRESS_LINE_PREFIX) }
    if (index < 0 || this[index].isDeleteProgressLog()) return this
    if (drop(index + 1).any { it.isDeleteGalleryCompletionLog() || it.isDeleteGalleryStartLog() }) return this
    val line = this[index]
    val markedLine = if ("] " in line) line.substringBefore("] ") + "] " + DELETE_PROGRESS_LOG_MARKER + line.substringAfter("] ")
    else DELETE_PROGRESS_LOG_MARKER + line
    return toMutableList().also { it[index] = markedLine }
}

internal fun deleteGalleryProgressMessage(
    deleted: Int,
    skipped: Int,
    total: Int?
): String {
    val processed = deleted + skipped
    val skippedText = if (skipped > 0) " · ${skipped}개 제외" else ""
    return if (total != null) {
        val percent = if (total > 0) {
            (processed * 100 / total).coerceIn(0, 100)
        } else {
            100
        }
        "🗑️ $processed/$total ($percent%$skippedText)"
    } else {
        "🗑️ $processed/? (전체 수 알 수 없음$skippedText)"
    }
}

internal fun String.isDeleteGalleryStartLog(): Boolean {
    val message = deleteLogMessage()
    return message.startsWith("🚀 ") && message.endsWith(" 삭제 시작")
}

internal fun String.isDeleteGalleryCompletionLog(): Boolean {
    val message = deleteLogMessage()
    return message.startsWith("✅ ") && " 삭제 완료 " in message
}

internal fun String.isGuestbookRunLogStart(): Boolean =
    deleteLogMessage() == "📝 방명록 가동 기록 작성 중"

internal fun String.isGuestbookRunLogCompletion(): Boolean {
    val message = deleteLogMessage()
    return message == "✅ 방명록 가동 기록 작성 완료" ||
        message == "⚠️ 방명록 가동 기록 작성 실패"
}

private fun String.deleteLogMessage(): String =
    substringAfter("] ", this)
        .removePrefix(DELETE_PROGRESS_LOG_MARKER)

private val GALLERY_PROGRESS_REGEX = Regex("""🗑️ (\d+)/(\d+|\?)""")

/**
 * 진행 중인 갤러리의 "🗑️ 처리/전체" 줄에서 숫자만 꺼낸다. 서버는 로그 원문 대신 이 숫자만 기록한다.
 * 전체 수를 모르면 second가 null, 진행 중인 갤러리가 없으면 null.
 */
fun List<String>.currentGalleryProgress(): Pair<Int, Int?>? {
    val line = lastOrNull { it.contains(DELETE_PROGRESS_LOG_MARKER) } ?: return null
    val match = GALLERY_PROGRESS_REGEX.find(line) ?: return null
    return match.groupValues[1].toInt() to match.groupValues[2].toIntOrNull()
}
