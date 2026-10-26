package com.dccleaner.app.util

fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60

    return if (hours > 0) {
        "${hours}시간 ${minutes}분 ${seconds}초"
    } else {
        "${minutes}분 ${seconds}초"
    }
}
