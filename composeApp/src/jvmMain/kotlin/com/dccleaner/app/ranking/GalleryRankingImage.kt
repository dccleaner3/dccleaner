package com.dccleaner.app.ranking

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private const val WIDTH = 900f
/** 폰에서도 선명하게 2배로 그린다. 좌표는 900px 기준. */
private const val SCALE = 2f
private const val HEADER = 202f
private val Background = Color(0xFF111111)
private val CardColor = Color(0xFF1B1B1B)
private val ChipColor = Color(0xFF222222)
private val Signature = Color(0xFF4DD0FF)
private val Muted = Color(0xFF8C8C8C)
private val Dim = Color(0xFF5E5E5E)
private val Soft = Color(0xFFEDEDED)
private val Medal = listOf(Color(0xFFFFD54A), Color(0xFFC9CED6), Color(0xFFD9925A))
/** 이미지는 기기 밀도와 상관없이 픽셀 단위로 잰다(1sp = 1px). */
private val Px = Density(1f)

/**
 * Pretendard Medium에서 영문·숫자·기호·자주 쓰는 한글 2,350자만 남긴 글꼴(SIL OFL 1.1, 옆의 DCCleanerRanking-OFL.txt).
 * OFL 예약 이름이라 고친 글꼴 이름은 'DCCleaner Ranking'이다. 빠진 글자는 기기 글꼴로 그려진다.
 * 굵은 글씨는 Medium을 굵게 합성한다.
 */
internal fun rankingFontBytes(): ByteArray =
    requireNotNull(GalleryRankingResult::class.java.getResourceAsStream("/dccleaner/DCCleanerRanking-Medium.ttf")) {
        "DCCleanerRanking-Medium.ttf is missing"
    }.use { it.readBytes() }

/**
 * autoM처럼 아이콘 가장자리부터 이어진 흰 배경을 투명하게 만든다(ARGB 픽셀을 바로 고친다).
 * 흰색에 가깝고(최소 채널 225 이상) 회색 끼가 적은(채널 차 20 이하) 픽셀만 배경으로 본다.
 */
internal fun clearEdgeBackground(pixels: IntArray, width: Int, height: Int) {
    fun background(p: Int): Boolean {
        val c = pixels[p]
        val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
        val min = minOf(r, g, b)
        return c ushr 24 > 0 && min >= 225 && maxOf(r, g, b) - min <= 20
    }
    val visited = BooleanArray(width * height)
    val queue = ArrayDeque<Int>()
    fun visit(p: Int) {
        if (!visited[p] && background(p)) { visited[p] = true; queue += p }
    }
    for (x in 0 until width) { visit(x); visit((height - 1) * width + x) }
    for (y in 0 until height) { visit(y * width); visit(y * width + width - 1) }
    while (queue.isNotEmpty()) {
        val p = queue.removeFirst()
        pixels[p] = pixels[p] and 0x00FFFFFF
        val x = p % width
        if (x > 0) visit(p - 1)
        if (x + 1 < width) visit(p + 1)
        if (p >= width) visit(p - width)
        if (p + width < pixels.size) visit(p + width)
    }
}

/**
 * 이미지 모양. [perImage]는 한 장에 넣는 사람 수로, 2배로 그려서 한 장이 너무 길어져 메모리가 터지지 않게 나눈다.
 */
enum class RankingImageStyle(val label: String, val perImage: Int) { CARD("카드형", 20), TABLE("표형", 50) }

internal fun RankingImageStyle.pageCount(result: GalleryRankingResult, sort: RankingSort): Int =
    ((result.users(sort).size + perImage - 1) / perImage).coerceAtLeast(1)

internal fun RankingImageStyle.imageCount(result: GalleryRankingResult, sorts: List<RankingSort> = RankingSort.entries): Int =
    sorts.sumOf { pageCount(result, it) }

/**
 * 정렬 하나를 [style] 모양으로 나눠 그린다. [timeline]이 꺼져 있거나 기간이면 하루 타임라인을 빼고 줄인다.
 * 기간이면 [perDay]일 때 하루 평균, 아니면 기간 합계로 적는다. 머리의 기준 칸에는 함께 저장하는 [sorts]만 그린다.
 */
internal fun renderRankingImages(
    result: GalleryRankingResult,
    sort: RankingSort,
    measurer: TextMeasurer,
    font: FontFamily,
    icons: Map<String, ImageBitmap>,
    style: RankingImageStyle = RankingImageStyle.CARD,
    timeline: Boolean = true,
    perDay: Boolean = false,
    sorts: List<RankingSort> = RankingSort.entries
): Sequence<ImageBitmap> {
    val pen = Pen(measurer, font, perDay)
    val pages = result.users(sort).chunked(style.perImage).ifEmpty { listOf(emptyList()) }
    val withTimeline = timeline && result.dates.isSingleDay
    // 한 장이 수십 MB라 꺼낼 때마다 한 장씩만 그린다.
    return pages.asSequence().mapIndexed { page, users ->
        val label = if (pages.size > 1) " (${page + 1}/${pages.size})" else ""
        val first = page * style.perImage
        val body = when {
            users.isEmpty() -> 80f
            style == RankingImageStyle.CARD -> users.sumOf { (cardHeight(result, it, withTimeline) + 12f).toDouble() }.toFloat()
            else -> tableHeight(users.size, withTimeline) + 16f
        }
        val height = HEADER + body + 70f
        val bitmap = ImageBitmap((WIDTH * SCALE).toInt(), (height * SCALE).toInt())
        CanvasDrawScope().draw(Px, LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH * SCALE, height * SCALE)) {
            drawRect(Background)
            scale(SCALE, Offset.Zero) {
                pen.header(this, result, sort, sorts, label)
                if (style == RankingImageStyle.TABLE) {
                    if (users.isNotEmpty()) pen.table(this, result, sort, users, icons, first, HEADER, withTimeline)
                } else {
                    var y = HEADER
                    users.forEachIndexed { i, user ->
                        pen.card(this, result, sort, user, icons[user.iconUrl], first + i, y, withTimeline)
                        y += cardHeight(result, user, withTimeline) + 12f
                    }
                }
                if (users.isEmpty()) pen.text(this, "집계할 활동이 없어요", WIDTH / 2, HEADER + 30f, 20f, Muted, bold = true, align = Align.CENTER)
                pen.text(this, "디시클리너 갤창랭킹", WIDTH / 2, height - 34f, 12f, Dim, align = Align.CENTER)
            }
        }
        bitmap
    }
}

private fun cardHeight(result: GalleryRankingResult, user: RankedUser, timeline: Boolean): Float =
    112f + (if (result.dates.isSingleDay && habits(user).isNotEmpty()) 26f else 0f) + (if (timeline) 50f else 0f)

private fun rowHeight(timeline: Boolean) = if (timeline) 76f else 58f

/** 머리글 44 + 행들, 타임라인이면 맨 아래 시각 눈금 자리 14. */
private fun tableHeight(rows: Int, timeline: Boolean) = 44f + rows * rowHeight(timeline) + if (timeline) 14f else 0f

/** [days]가 1이면 그대로, 아니면 하루 평균(10개 미만은 소수 한 자리). 0은 −. */
private fun countValue(count: Int, days: Int): String {
    if (count == 0) return "−"
    if (days == 1) return "${count}개"
    val v = count.toDouble() / days
    return if (v >= 10) "${Math.round(v)}개" else "%.1f개".format(v).replace(".0개", "개")
}

/** 하루면 그날 값. 기간이면 [perDay]일 때 하루 평균, 아니면 기간 합계. */
private fun GalleryRankingResult.value(user: RankedUser, sort: RankingSort, perDay: Boolean): String {
    val days = if (perDay) dates.days else 1
    return when (sort) {
        RankingSort.TIME -> if (days == 1) formatMinutes(user.activityMinutes) else activityValue(user)
        RankingSort.POST -> countValue(user.posts, days)
        RankingSort.COMMENT -> countValue(user.comments, days)
    }
}

private fun GalleryRankingResult.heroLabel(sort: RankingSort, perDay: Boolean): String {
    val prefix = when {
        dates.isSingleDay -> ""
        perDay -> "하루 평균 "
        else -> "총 "
    }
    return prefix + when (sort) {
        RankingSort.TIME -> if (dates.isSingleDay) "활동시간" else "활동"
        RankingSort.POST -> "글"
        RankingSort.COMMENT -> "댓글"
    }
}

private fun shortLabel(sort: RankingSort) = when (sort) {
    RankingSort.TIME -> "활동"; RankingSort.POST -> "글"; RankingSort.COMMENT -> "댓글"
}

private fun habits(user: RankedUser) = listOfNotNull(
    user.topComment?.let { (content, percent) -> "자주 쓴 말 “${content.take(18)}${if (content.length > 18) "…" else ""}” $percent%" },
    user.dcconPercent?.let { "디시콘 $it%" }
).joinToString("  ·  ")

private fun rankColor(index: Int) = Medal.getOrElse(index) { Color(0xFFBDBDBD) }

private enum class Align { START, CENTER, END }

private class Pen(private val measurer: TextMeasurer, private val font: FontFamily, private val perDay: Boolean) {
    private fun style(size: Float, bold: Boolean, color: Color) =
        TextStyle(color = color, fontSize = size.sp, fontFamily = font, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)

    fun measure(value: String, size: Float, bold: Boolean, maxWidth: Float = 4000f): TextLayoutResult =
        measurer.measure(value, style(size, bold, Color.White), overflow = TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)), density = Px)

    /**
     * [y]는 글자 윗변, [x]는 [align] 기준점. 넘치면 말줄임.
     * [shrink]면 먼저 글자를 [size]의 60%까지 1px씩 줄여 맞추고, 줄인 만큼 세로 가운데를 유지한다.
     */
    fun text(scope: DrawScope, value: String, x: Float, y: Float, size: Float, color: Color, bold: Boolean = false,
             align: Align = Align.START, maxWidth: Float = 4000f, shrink: Boolean = false): TextLayoutResult {
        val fitted = if (!shrink) size else generateSequence(size) { it - 1f }
            .first { it <= size * 0.6f || measure(value, it, bold).size.width <= maxWidth }
        val layout = measurer.measure(value, style(fitted, bold, color), overflow = TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)), density = Px)
        val left = when (align) {
            Align.START -> x; Align.CENTER -> x - layout.size.width / 2f; Align.END -> x - layout.size.width
        }
        scope.drawText(layout, topLeft = Offset(left, y + (size - fitted) / 2))
        return layout
    }

    fun header(scope: DrawScope, result: GalleryRankingResult, sort: RankingSort, sorts: List<RankingSort>, pageLabel: String) = with(scope) {
        text(this, result.gallery.name, WIDTH / 2, 34f, 17f, Signature, bold = true, align = Align.CENTER, maxWidth = WIDTH - 80)
        text(this, result.title() + pageLabel, WIDTH / 2, 60f, 32f, Color.White, bold = true, align = Align.CENTER, maxWidth = WIDTH - 80)
        val summary = buildString {
            append("${result.dateLabel()}  ·  글 ${"%,d".format(result.postCount)}개  ·  댓글 ${"%,d".format(result.commentCount)}개 집계")
            if (!result.dates.isSingleDay && perDay) append("  ·  하루 평균")
        }
        text(this, summary, WIDTH / 2, 106f, 15f, Muted, align = Align.CENTER, maxWidth = WIDTH - 40)
        val segment = 120f
        val left = (WIDTH - segment * sorts.size) / 2
        val top = 142f
        drawRoundRect(ChipColor, Offset(left, top), Size(segment * sorts.size, 36f), CornerRadius(18f))
        sorts.forEachIndexed { i, s ->
            val on = s == sort
            if (on) drawRoundRect(Signature, Offset(left + i * segment + 3, top + 3), Size(segment - 6, 30f), CornerRadius(15f))
            text(this, s.label, left + i * segment + segment / 2, top + 8f, 15f, if (on) Background else Muted, bold = true, align = Align.CENTER)
        }
    }

    fun card(scope: DrawScope, result: GalleryRankingResult, sort: RankingSort, user: RankedUser, icon: ImageBitmap?,
             index: Int, y: Float, timeline: Boolean) = with(scope) {
        val height = cardHeight(result, user, timeline)
        val rankColor = rankColor(index)
        drawRoundRect(CardColor, Offset(16f, y), Size(WIDTH - 32f, height), CornerRadius(16f))
        if (index < 3) drawRoundRect(rankColor, Offset(16f, y + 18f), Size(4f, height - 36f), CornerRadius(2f))
        val rank = "${index + 1}"
        text(this, rank, 62f, y + 30f + (if (rank.length < 3) 0f else 6f), if (rank.length < 3) 38f else 28f, rankColor,
            bold = true, align = Align.CENTER, maxWidth = 84f)

        // 정렬 기준 지표를 오른쪽에 크게
        val right = WIDTH - 44f
        val heroWidth = 200f
        text(this, result.heroLabel(sort, perDay), right, y + 22f, 13f, Muted, align = Align.END)
        text(this, result.value(user, sort, perDay), right, y + 40f, 28f, Signature, bold = true, align = Align.END,
            maxWidth = heroWidth, shrink = true)

        val x = 120f
        drawName(this, user, icon, x, y + 22f, right - heroWidth - x - 16f)

        // 나머지 지표 칩: 이름표 · 값 · (그 부문 순위)
        var chipX = x
        val chips = RankingSort.entries.filter { it != sort }.map { s ->
            Triple(shortLabel(s), result.value(user, s, perDay), when (s) { RankingSort.POST -> user.postRank; RankingSort.COMMENT -> user.commentRank; else -> 0 })
        } + listOfNotNull(user.avgCommentsPerPost?.takeIf { it > 0 }?.let { Triple("글당 댓글", "${it}개", 0) })
        chips.forEach { (label, value, rank) ->
            val labelWidth = measure(label, 13f, false).size.width
            val valueWidth = measure(value, 15f, true).size.width
            val badge = if (rank > 0 && value != "−") "${rank}위" else ""
            val badgeWidth = if (badge.isEmpty()) 0f else measure(badge, 12f, true).size.width + 14f
            val chipWidth = 14f + labelWidth + 6f + valueWidth + (if (badge.isEmpty()) 0f else 8f + badgeWidth) + 14f
            drawRoundRect(ChipColor, Offset(chipX, y + 64f), Size(chipWidth, 30f), CornerRadius(9f))
            text(this, label, chipX + 14f, y + 71f, 13f, Muted)
            text(this, value, chipX + 14f + labelWidth + 6f, y + 69f, 15f, if (value == "−") Dim else Soft, bold = true)
            if (badge.isNotEmpty()) {
                val badgeX = chipX + 14f + labelWidth + 6f + valueWidth + 8f
                drawRoundRect(Color(0xFF203540), Offset(badgeX, y + 70f), Size(badgeWidth, 18f), CornerRadius(5f))
                text(this, badge, badgeX + 7f, y + 71f, 12f, Signature, bold = true)
            }
            chipX += chipWidth + 8f
        }

        var lineY = y + 106f
        val habit = if (result.dates.isSingleDay) habits(user) else ""
        if (habit.isNotEmpty()) {
            text(this, habit, x, lineY, 13f, Color(0xFF9FDFF5), maxWidth = right - x)
            lineY += 26f
        }
        if (timeline) drawTimeline(this, user, x, lineY + 4f, right - x, 18f, labels = true, radius = 4f)
    }

    /** 표형: 정렬 기준 열에 옅은 배경을 깔고 그 열 값을 크게. 열 x는 오른쪽 끝. */
    fun table(scope: DrawScope, result: GalleryRankingResult, sort: RankingSort, users: List<RankedUser>,
              icons: Map<String, ImageBitmap>, firstIndex: Int, top: Float, timeline: Boolean) = with(scope) {
        val rowH = rowHeight(timeline)
        val columns = listOf(RankingSort.TIME to 654f, RankingSort.POST to 764f, RankingSort.COMMENT to 864f)
        val widths = mapOf(RankingSort.TIME to 130f, RankingSort.POST to 100f, RankingSort.COMMENT to 100f)
        val height = tableHeight(users.size, timeline)
        drawRoundRect(CardColor, Offset(16f, top), Size(WIDTH - 32f, height), CornerRadius(16f))
        val active = columns.first { it.first == sort }.second
        val activeWidth = widths.getValue(sort)
        drawRect(Signature.copy(alpha = 0.07f), Offset(active - activeWidth + 8f, top), Size(activeWidth + 4f, height))

        text(this, "순위", 58f, top + 14f, 13f, Muted, align = Align.CENTER)
        text(this, if (timeline) "닉네임 · 하루 중 활동한 때" else "닉네임", 104f, top + 14f, 13f, Muted)
        columns.forEach { (s, right) ->
            val label = shortLabel(s) + if (s == RankingSort.TIME && result.dates.isSingleDay) "시간" else ""
            text(this, label, right - 12f, top + 14f, 13f, if (s == sort) Signature else Muted, bold = s == sort, align = Align.END)
        }
        drawLine(Color(0xFF2A2A2A), Offset(16f, top + 44f), Offset(WIDTH - 16f, top + 44f), 1f)

        users.forEachIndexed { i, user ->
            val y = top + 44f + i * rowH
            if (i > 0) drawLine(Color(0xFF242424), Offset(32f, y), Offset(WIDTH - 32f, y), 1f)
            val index = firstIndex + i
            val center = if (timeline) y + 26f else y + rowH / 2
            val rank = "${index + 1}"
            if (index < 3) drawCircle(rankColor(index).copy(alpha = 0.16f), 17f, Offset(58f, center))
            text(this, rank, 58f, center - 12f, if (rank.length < 3) 18f else 15f, if (index < 3) rankColor(index) else Muted,
                bold = true, align = Align.CENTER, maxWidth = 80f)
            val nameMax = columns.first().second - widths.getValue(RankingSort.TIME) - 104f - 6f
            drawName(this, user, icons[user.iconUrl], 104f, if (timeline) y + 13f else y + rowH / 2 - 13f, nameMax, size = 19f)
            if (timeline) drawTimeline(this, user, 104f, y + 46f, 400f, 12f, labels = i == users.lastIndex, radius = 3f)
            columns.forEach { (s, right) ->
                val on = s == sort
                val value = result.value(user, s, perDay)
                text(this, value, right - 12f, (if (timeline) y + 25f else y + rowH / 2) - if (on) 13f else 11f,
                    if (on) 20f else 16f, if (on) Signature else if (value == "−") Dim else Soft, bold = on,
                    align = Align.END, maxWidth = widths.getValue(s), shrink = true)
            }
        }
    }

    /** 이름 뒤에 아이콘(16px, 베스트콘 30px). 아이콘 자리까지 빼고 말줄임. */
    private fun drawName(scope: DrawScope, user: RankedUser, icon: ImageBitmap?, x: Float, y: Float, maxWidth: Float, size: Float = 23f) {
        val iconSize = if ("bestcon" in user.iconUrl) 30f else 16f
        val name = text(scope, user.displayName, x, y, size, Color.White, bold = true,
            maxWidth = maxWidth - if (icon != null) iconSize + 6f else 0f)
        if (icon != null) scope.drawImage(icon,
            dstOffset = IntOffset((x + name.size.width + 6f).toInt(), (y + name.size.height / 2f - iconSize / 2).toInt()),
            dstSize = IntSize(iconSize.toInt(), iconSize.toInt()))
    }

    private fun drawTimeline(scope: DrawScope, user: RankedUser, x: Float, y: Float, width: Float, barHeight: Float,
                             labels: Boolean, radius: Float) = with(scope) {
        drawRoundRect(Color(0xFF262626), Offset(x, y), Size(width, barHeight), CornerRadius(radius))
        listOf(6, 12, 18).forEach { hour ->
            val hx = x + hour / 24f * width
            drawLine(Color(0xFF3A3A3A), Offset(hx, y), Offset(hx, y + barHeight), 1f)
        }
        user.dayBlocks.forEach { block ->
            val bx = x + block.first / 1440f * width
            val bw = ((block.last - block.first + 1) / 1440f * width).coerceAtLeast(3f)
            drawRoundRect(Signature, Offset(bx, y), Size(bw, barHeight), CornerRadius((bw / 2).coerceAtMost(radius / 2)))
        }
        if (labels) listOf(0, 6, 12, 18, 24).forEach { hour ->
            val align = when (hour) { 0 -> Align.START; 24 -> Align.END; else -> Align.CENTER }
            text(this, "${hour}시", x + hour / 24f * width, y + barHeight + 4f, 11f, Dim, align = align)
        }
    }
}
