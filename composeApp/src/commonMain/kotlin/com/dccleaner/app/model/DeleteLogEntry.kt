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
