package com.dccleaner.app.ranking

import java.time.LocalDate
import java.util.Locale

internal fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}시간 ${minutes % 60}분"
    else -> "${minutes}분"
}

internal fun formatDate(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

/** 올해가 아니면 앞에 "2023년 "을 붙인다. */
private fun LocalDate.yearPrefix() = if (year != LocalDate.now(KST).year) "${year}년 " else ""

internal fun GalleryRankingResult.title(): String = when (period) {
    RankingPeriod.DAY -> "${dates.start.yearPrefix()}${formatDate(dates.start)} 갤창 리스트"
    RankingPeriod.WEEK -> "주간 갤창 리스트"
    RankingPeriod.MONTH -> "${dates.start.yearPrefix()}${dates.start.monthValue}월 갤창 리스트"
}

internal fun GalleryRankingResult.dateLabel(): String =
    if (dates.isSingleDay) dates.start.yearPrefix() + formatDate(dates.start)
    else "${dates.start.yearPrefix()}${formatDate(dates.start)} - ${formatDate(dates.endInclusive)}"

/** 하루면 그날 활동시간, 기간이면 하루 평균. 활동은 했는데 평균이 1분이 안 되면 0분 대신 '1분 미만'. */
internal fun GalleryRankingResult.activityValue(user: RankedUser): String {
    val minutes = user.activityMinutes / dates.days
    return if (minutes == 0 && user.activityMinutes > 0) "1분 미만" else formatMinutes(minutes)
}

internal fun GalleryRankingResult.activityLabel(user: RankedUser): String =
    if (dates.isSingleDay) activityValue(user) else "하루 평균 ${activityValue(user)}"

/** 기간을 하루 평균으로 볼지. 하루짜리는 [perDay]를 무시한다. */
private fun GalleryRankingResult.averaged(perDay: Boolean) = perDay && !dates.isSingleDay

/**
 * 순서 기준 하나의 값. 하루거나 기간 합계면 시간은 그대로, 글·댓글은 'N개'.
 * 기간 하루 평균이면 시간은 [activityValue], 글·댓글은 10 미만은 소수 한 자리(0.6개, .0은 뗀다), 10 이상은 반올림.
 * 0개도 '0개'로 준다(이미지는 따로 '−'로 바꾼다).
 */
internal fun GalleryRankingResult.metricValue(user: RankedUser, sort: RankingSort, perDay: Boolean): String {
    val average = averaged(perDay)
    val count = when (sort) {
        RankingSort.TIME -> return if (average) activityValue(user) else formatMinutes(user.activityMinutes)
        RankingSort.POST -> user.posts
        RankingSort.COMMENT -> user.comments
    }
    if (!average) return "${count}개"
    val avg = count.toDouble() / dates.days
    return if (avg >= 10) "${Math.round(avg)}개" else "${String.format(Locale.ROOT, "%.1f", avg).removeSuffix(".0")}개"
}

/** '3시간 20분' / '글 12개' / '댓글 0.6개'. 하루 평균인지는 머리말에 한 번만 적는다. */
private fun GalleryRankingResult.metricLabel(user: RankedUser, sort: RankingSort, perDay: Boolean): String {
    val value = metricValue(user, sort, perDay)
    return when (sort) {
        RankingSort.TIME -> value
        RankingSort.POST -> "글 $value"
        RankingSort.COMMENT -> "댓글 $value"
    }
}

/**
 * 복사용 글. [perDay]면 기간 값을 하루 평균으로, 아니면 기간 합계로 쓴다(하루짜리는 무시).
 * [metricOnly]면 순서마다 그 기준 값만, 아니면 기준 값 뒤에 나머지 둘도 붙인다.
 */
internal fun GalleryRankingResult.toText(
    perDay: Boolean = false,
    metricOnly: Boolean = false,
    sorts: List<RankingSort> = RankingSort.entries
): String = buildString {
    appendLine("${gallery.name} ${title()}")
    append(dateLabel()).append(" · 글 ${postCount}개 · 댓글 ${commentCount}개 집계")
    if (averaged(perDay)) append(" · 하루 평균")
    appendLine()
    sorts.forEach { sort ->
        appendLine()
        appendLine("[${sort.label}]")
        val users = users(sort)
        if (users.isEmpty()) appendLine("집계할 활동이 없어요")
        val shown = if (metricOnly) listOf(sort) else listOf(sort) + (RankingSort.entries - sort)
        users.forEachIndexed { index, user ->
            appendLine("${index + 1}. ${user.displayName} — ${shown.joinToString(" · ") { metricLabel(user, it, perDay) }}")
        }
    }
}.trimEnd()
