package com.dccleaner.app.ui.ranking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ranking.CrawlProgress
import com.dccleaner.app.ranking.GalleryRankingCrawler
import com.dccleaner.app.ranking.GalleryRankingRequest
import com.dccleaner.app.ranking.GalleryRankingResult
import com.dccleaner.app.ranking.GalleryRankingRunner
import com.dccleaner.app.ranking.GalleryRankingState
import com.dccleaner.app.ranking.KST
import com.dccleaner.app.ranking.RankingPeriod
import com.dccleaner.app.ranking.RankingSort
import com.dccleaner.app.ranking.RankingImageStyle
import com.dccleaner.app.ranking.imageCount
import com.dccleaner.app.ranking.pageCount
import com.dccleaner.app.ranking.dateLabel
import com.dccleaner.app.ranking.formatDate
import com.dccleaner.app.ranking.isSingleDay
import com.dccleaner.app.ranking.renderRankingImages
import com.dccleaner.app.ranking.title
import com.dccleaner.app.ranking.toText
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import com.dccleaner.app.ui.theme.BusyIndicator
import com.dccleaner.app.ui.theme.BusyBar

/** 플랫폼마다 다른 부분: 백그라운드 유지, PNG 변환, 저장 위치. */
internal interface RankingPlatform {
    /** 만들기 시작 직후. 안드로이드는 앱을 내려도 끝까지 만들도록 포그라운드 서비스를 띄운다. */
    fun onStarted()
    val keepsRunningInBackground: String
    fun encodePng(image: ImageBitmap): ByteArray
    /** 저장하고 어디에 저장했는지 사용자에게 보여 줄 문구를 돌려준다. */
    suspend fun saveImages(images: Sequence<Pair<String, ByteArray>>): String
    /** 이미지에 쓰는 글꼴(Pretendard Medium을 줄인 DCCleaner Ranking). */
    val rankingFont: FontFamily
    /** 닉 아이콘(gif·png)을 풀고 [clearEdgeBackground]로 흰 배경을 지운다. 못 풀면 null. */
    fun decodeIcon(bytes: ByteArray): ImageBitmap?
}

@Composable
internal expect fun rememberRankingPlatform(): RankingPlatform

private const val DEFAULT_TOP = 10

/** 순위 −/+ 버튼이 멈추는 자리. 많이 쓰는 TOP 수에, 20은 카드형 한 장·50은 표형 한 장. 100 위로는 100씩. */
private val RANK_STEPS = listOf(5, 10, 20, 30, 50, 100)

/** [value]보다 큰 첫 단계. 직접 넣은 37이면 50. */
internal fun nextRankStep(value: Int): Int =
    RANK_STEPS.firstOrNull { it > value } ?: ((value / 100 + 1) * 100)

/** [value]보다 작은 마지막 단계. 5 이하면 더 내려갈 단계가 없다. */
internal fun previousRankStep(value: Int): Int? = when {
    value > 100 -> (value - 1) / 100 * 100
    else -> RANK_STEPS.lastOrNull { it < value }
}

actual fun clearGalleryRanking() = GalleryRankingRunner.cancel()

@Composable
actual fun galleryRankingBadge(): String? {
    val state by GalleryRankingRunner.state.collectAsState()
    return if (state is GalleryRankingState.Running) "만드는 중" else null
}

@Composable
actual fun GalleryRankingContent(uiColors: UiColors, onSendGuestbook: (List<String>) -> Unit, guestbookSending: Boolean,
                                 modifier: Modifier) {
    val platform = rememberRankingPlatform()
    val state by GalleryRankingRunner.state.collectAsState()
    val lastRequest = when (val s = state) {
        is GalleryRankingState.Running -> s.request
        is GalleryRankingState.Done -> s.request
        is GalleryRankingState.Failed -> s.request
        GalleryRankingState.Idle -> null
    }
    val today = LocalDate.now(KST)
    var input by rememberSaveable { mutableStateOf(lastRequest?.input.orEmpty()) }
    var period by rememberSaveable { mutableStateOf(lastRequest?.period ?: RankingPeriod.DAY) }
    var anchor by rememberSaveable { mutableStateOf(lastRequest?.dates?.start ?: period.defaultAnchor(today)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val running = state is GalleryRankingState.Running
    val range = period.range(anchor, today)

    fun start() {
        if (GalleryRankingCrawler.parseGalleryInput(input) == null) {
            inputError = if (input.isBlank()) "갤러리 주소나 ID를 넣어 주세요" else "갤러리 주소나 ID를 확인해 주세요"
            return
        }
        inputError = null
        val request = GalleryRankingRequest(input.trim(), period, period.range(anchor))
        if (GalleryRankingRunner.start(request)) platform.onStarted()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val s = state) {
            is GalleryRankingState.Done -> {
                DoneCard(uiColors, s.result, platform, onNew = GalleryRankingRunner::reset, onSendGuestbook = onSendGuestbook,
                    guestbookSending = guestbookSending)
                return@Column
            }
            is GalleryRankingState.Running -> {
                ProgressCard(uiColors, s, platform.keepsRunningInBackground)
                return@Column
            }
            is GalleryRankingState.Failed -> ErrorCard(uiColors, s.message, onRetry = ::start)
            GalleryRankingState.Idle -> Unit
        }

        SectionLabel("갤러리", uiColors)
        RankingCard(uiColors) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; inputError = null },
                label = { Text("갤러리 주소 또는 ID") },
                placeholder = { Text("gall.dcinside.com/board/lists/?id=…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingText = { Text(inputError ?: "PC·모바일 주소, 갤러리 ID 모두 돼요") },
                isError = inputError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                colors = dccleanerOutlinedTextFieldColors(uiColors),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.size(4.dp))
        SectionLabel("기간", uiColors)
        RankingCard(uiColors) {
            ChoiceRow(RankingPeriod.entries, period, { it.label }, uiColors) { option ->
                // 단위를 바꾸면 그 단위의 기본값(어제·지난주·지난달)으로 맞춘다.
                period = option
                anchor = option.defaultAnchor(today)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp))
                    .background(uiColors.surfaceVariant).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { anchor = period.shift(anchor, -1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 ${period.label}")
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .then(if (period == RankingPeriod.DAY) Modifier.clickable(onClickLabel = "달력 열기") { showDatePicker = true } else Modifier)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(rangeTitle(period, range, today), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    rangeDetail(period, range)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary, textAlign = TextAlign.Center)
                    }
                }
                // 오늘이 낀 단위까지만 앞으로 갈 수 있다.
                val canGoNext = !period.range(period.shift(anchor, 1), today).start.isAfter(today)
                IconButton(onClick = { anchor = period.shift(anchor, 1) }, enabled = canGoNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 ${period.label}")
                }
            }
            if (period == RankingPeriod.DAY) TextButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("달력에서 날짜 고르기")
            }
            val note = buildList {
                if (range.endInclusive == today) add("오늘은 지금까지의 활동만 세요")
                if (period != RankingPeriod.DAY) add("기간이 길수록 오래 걸려요")
            }.joinToString(" · ")
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary,
                modifier = Modifier.padding(top = if (period == RankingPeriod.DAY) 0.dp else 10.dp, start = 4.dp))
        }

        if (showDatePicker) RankingDatePicker(
            initial = anchor,
            latest = today,
            onPick = { anchor = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )

        Spacer(Modifier.size(8.dp))
        Button(
            onClick = ::start,
            enabled = !running,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
            shape = RoundedCornerShape(12.dp)
        ) { Text("갤창랭킹 만들기", fontWeight = FontWeight.Bold) }
        Text(platform.keepsRunningInBackground, style = MaterialTheme.typography.bodySmall,
            color = uiColors.textSecondary, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
    }
}

/** 기간 단위별 큰 제목: "10월 17일 (토)", "10월 둘째 주", "2026년 9월". */
private fun rangeTitle(period: RankingPeriod, range: ClosedRange<LocalDate>, today: LocalDate): String = when (period) {
    RankingPeriod.DAY -> {
        val weekday = range.start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        val relative = when (range.start) { today -> " · 오늘"; today.minusDays(1) -> " · 어제"; else -> "" }
        "${yearPrefix(range.start, today)}${formatDate(range.start)} ($weekday)$relative"
    }
    RankingPeriod.WEEK -> when (range.start) {
        today.with(java.time.DayOfWeek.MONDAY) -> "이번 주"
        today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1) -> "지난주"
        else -> "${yearPrefix(range.start, today)}${formatDate(range.start)}부터 한 주"
    }
    RankingPeriod.MONTH -> "${range.start.year}년 ${range.start.monthValue}월" +
        if (range.start.withDayOfMonth(1) == today.withDayOfMonth(1)) " · 이번 달" else ""
}

/** 실제로 세는 날짜. 하루는 날짜가 제목에 있으니 없다. */
private fun rangeDetail(period: RankingPeriod, range: ClosedRange<LocalDate>): String? =
    if (period == RankingPeriod.DAY) null
    else "${formatDate(range.start)} (${weekday(range.start)}) – ${formatDate(range.endInclusive)} (${weekday(range.endInclusive)})"

/** 진행 중에 보여 줄 기간: "2023년 11월 15일 (수)", "4월 7일 – 4월 13일 · 주". */
private fun requestDates(request: GalleryRankingRequest): String {
    val today = LocalDate.now(KST)
    val (start, end) = request.dates.start to request.dates.endInclusive
    return if (start == end) "${yearPrefix(start, today)}${formatDate(start)} (${weekday(start)})"
    else "${yearPrefix(start, today)}${formatDate(start)} – ${formatDate(end)} · ${request.period.label}"
}

private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

private fun yearPrefix(date: LocalDate, today: LocalDate) = if (date.year != today.year) "${date.year}년 " else ""

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, uiColors: UiColors, check: Boolean = true,
                          onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = { if (check) SegmentedButtonDefaults.Icon(selected == option) },
                colors = SegmentedButtonDefaults.colors(activeContainerColor = uiColors.primary.copy(alpha = 0.16f),
                    activeContentColor = uiColors.primary)
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun ProgressCard(uiColors: UiColors, state: GalleryRankingState.Running, background: String) {
    val progress = state.progress
    val (title, detail, fraction) = when (progress.stage) {
        CrawlProgress.Stage.RESOLVING -> Triple("갤러리를 확인하고 있어요", "", null)
        CrawlProgress.Stage.POSTS -> if (progress.seeking) Triple("시작 날짜가 있는 페이지를 찾고 있어요", "", null) else Triple(
            "글을 모으고 있어요",
            progress.reachedDate?.let { "확인한 글 ${"%,d".format(progress.posts)}개 · ${formatDate(it)}까지 내려왔어요" }.orEmpty(),
            progress.listFraction
        )
        CrawlProgress.Stage.COMMENTS -> {
            val fraction = if (progress.commentRequestsTotal == 0) 1f
                else progress.commentRequestsDone.toFloat() / progress.commentRequestsTotal
            Triple("댓글을 모으고 있어요",
                "글 ${"%,d".format(progress.posts)}개의 댓글 ${(fraction * 100).toInt()}% 확인", fraction)
        }
    }
    SectionLabel("만드는 중", uiColors)
    RankingCard(uiColors) {
        Text(state.galleryName ?: state.request.input, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(requestDates(state.request), style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        Spacer(Modifier.size(10.dp))
        val stepLabel = when (progress.stage) {
            CrawlProgress.Stage.RESOLVING -> "1/3"
            CrawlProgress.Stage.POSTS -> "2/3"
            CrawlProgress.Stage.COMMENTS -> "3/3"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 아직 얼마나 걸릴지 모르는 0%일 때는 움직이는 막대로 '진행 중'만 알린다.
            if (fraction == null || fraction <= 0f) BusyBar(Modifier.weight(1f), color = uiColors.primary)
            else LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f),
                color = uiColors.primary, drawStopIndicator = {})
            Spacer(Modifier.width(10.dp))
            Text("단계 $stepLabel", style = MaterialTheme.typography.labelMedium, color = uiColors.textSecondary)
        }
        Spacer(Modifier.size(12.dp))
        Text(background, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        // 오래 걸린 수집을 실수로 날리지 않게 한 번 더 묻는다. 그사이 끝나면 이 카드와 함께 창도 닫힌다.
        var confirmCancel by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = { confirmCancel = true },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("만들기 취소") }
        if (confirmCancel) AlertDialog(
            onDismissRequest = { confirmCancel = false },
            // 제목은 휴대폰 폭에서도 한 줄에 들어가게 짧게, 본문은 잃는 양을 숫자로 보여 줘 판단을 돕는다.
            title = { Text("만들기를 취소할까요?") },
            text = {
                Text(if (progress.posts > 0) "지금까지 모은 글 ${"%,d".format(progress.posts)}개가 사라져요.\n다시 만들면 처음부터 모아야 해요."
                    else "지금까지 확인한 내용이 사라져요.\n다시 만들면 처음부터 시작해요.")
            },
            confirmButton = {
                Button(onClick = GalleryRankingRunner::cancel, shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = uiColors.danger)) { Text("만들기 취소") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("계속 만들기", color = uiColors.primary) }
            }
        )
    }
}

@Composable
private fun ErrorCard(uiColors: UiColors, message: String, onRetry: () -> Unit) {
    RankingCard(uiColors) {
        Text("갤창랭킹을 만들지 못했어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = uiColors.danger)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("같은 조건으로 다시 시도") }
    }
    Spacer(Modifier.size(8.dp))
}

private enum class ResultView(val label: String) { IMAGE("이미지"), TEXT("텍스트") }

/** 결과를 텍스트·이미지 미리보기로 보여 주고, 옵션을 바꾸면 미리보기에 바로 반영한다. 복사·저장은 보고 있는 방식으로 한다. */
@Composable
private fun DoneCard(uiColors: UiColors, result: GalleryRankingResult, platform: RankingPlatform, onNew: () -> Unit,
                     onSendGuestbook: (List<String>) -> Unit, guestbookSending: Boolean) {
    var view by rememberSaveable { mutableStateOf(ResultView.IMAGE) }
    var topText by rememberSaveable { mutableStateOf(DEFAULT_TOP.toString()) }
    // 주·달만: 끄면 기간 총합, 켜면 하루 평균. 텍스트·이미지 공통.
    var perDay by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable { mutableStateOf(RankingImageStyle.CARD) }
    // 하루짜리만 타임라인이 있다.
    var timeline by rememberSaveable { mutableStateOf(true) }
    // 결과에 넣을 순서 기준. 기본은 셋 다지만 주·달은 시간순을 끈다(기간 합계 활동시간은 잘 안 쓴다). 하나는 늘 남긴다.
    var sorts by rememberSaveable { mutableStateOf(if (result.dates.isSingleDay) RankingSort.entries.toList() else listOf(RankingSort.POST, RankingSort.COMMENT)) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val empty = result.byTime.isEmpty()
    val period = !result.dates.isSingleDay
    // 집계는 모든 사람을 해 두고, 몇 위까지 보일지는 여기서 자른다. 비우거나 0이면 기본값.
    val topN = (topText.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_TOP).coerceAtMost(result.byTime.size.coerceAtLeast(1))
    val shown = remember(result, topN) { result.top(topN) }
    // 텍스트는 늘 순위마다 그 지표만 적는다(시간순엔 시간, 글순엔 글 수, 댓글순엔 댓글 수).
    val text = remember(shown, perDay, sorts) { shown.toText(perDay = perDay, metricOnly = true, sorts = sorts) }
    val icons = remember(result) { result.icons.mapNotNull { (url, bytes) -> platform.decodeIcon(bytes)?.let { url to it } }.toMap() }
    val imageCount = style.imageCount(shown, sorts)
    // 순위 수와 상관없이 집계된 모든 고닉·반고닉. 유동은 방명록을 받을 수 없어 뺀다.
    val guestbookIds = remember(result) { result.guestbookIds() }

    // 미리보기는 고른 첫 기준의 첫 장만, 절반 크기로 그린다(긴 이미지를 그대로 띄우면 메모리·텍스처 한도에 걸린다).
    // 옵션을 바꿔도 새 그림이 나올 때까지 이전 그림을 그대로 둔다. 비우면 높이가 줄어 화면이 위로 튄다.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    if (view == ResultView.IMAGE && !empty) LaunchedEffect(shown, style, timeline, perDay, sorts) {
        rendering = true
        val first = withContext(Dispatchers.Default) {
            renderRankingImages(shown, sorts.first(), measurer, platform.rankingFont, icons, style, timeline, perDay, sorts).first().halfSize()
        }
        preview = first
        rendering = false
    }

    fun saveImages() {
        saving = true
        message = null
        // 저장 도중 옵션을 바꿔도 한 묶음이 같은 모양으로 나오게 누른 순간의 값으로 고정한다.
        val style = style
        val timeline = timeline
        val perDay = perDay
        val sorts = sorts
        scope.launch {
            message = runCatching {
                val stamp = result.dates.start.toString().replace("-", "")
                // 한 장 그리고 → PNG로 바꾸고 → 저장한 뒤 다음 장. 순위를 길게 잡아도 한 번에 한 장만 메모리에 둔다.
                // 이름 정렬로 봐도 고른 순서대로 놓이게 기준 앞에 번호를 붙인다(한글 이름순이면 글·댓글·시간이 된다).
                val images = sorts.asSequence().withIndex().flatMap { (order, sort) ->
                    val pages = style.pageCount(shown, sort)
                    renderRankingImages(shown, sort, measurer, platform.rankingFont, icons, style, timeline, perDay, sorts)
                        .mapIndexed { i, page ->
                            val part = if (pages > 1) "_${i + 1}" else ""
                            "갤창랭킹_${result.gallery.id}_${result.period.label}_${stamp}_${order + 1}${sort.label}$part.png" to
                                platform.encodePng(page)
                        }
                }
                withContext(Dispatchers.Default) { platform.saveImages(images) }
            }.getOrElse { "이미지를 저장하지 못했어요. 다시 시도해 주세요" }
            saving = false
        }
    }

    SectionLabel("결과", uiColors)
    RankingCard(uiColors) {
        Text("${result.gallery.name} ${result.title()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("${result.dateLabel()} · 글 ${"%,d".format(result.postCount)}개 · 댓글 ${"%,d".format(result.commentCount)}개 집계",
            style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        if (empty) {
            Text("이 기간에 쓴 글이 없거나 모두 기본 닉(ㅇㅇ) 유동이 쓴 글이에요", color = uiColors.textSecondary,
                modifier = Modifier.padding(top = 12.dp))
            return@RankingCard
        }
        Spacer(Modifier.size(16.dp))
        ChoiceRow(ResultView.entries, view, { it.label }, uiColors, check = false) { view = it; message = null }

        // 옵션: 한 묶음 안에 '이름 ─ 조작' 줄로만 둔다.
        OptionGroup(uiColors) {
            OptionRow("순위", "최대 ${"%,d".format(result.byTime.size)}위", uiColors, first = true) {
                RankStepper(topText, topN, max = result.byTime.size, uiColors) { topText = it }
            }
            // 셋이 한 줄 오른쪽에 들어가기엔 좁고, 체크 표시가 있어야 여러 개 고르는 줄로 읽혀 이름 아래에 꽉 채워 둔다.
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = uiColors.outline.copy(alpha = 0.35f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("기준", style = MaterialTheme.typography.bodyLarge)
                    Text("여러 개 고를 수 있어요", style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp))
                }
                MultiChoice(RankingSort.entries, sorts, { it.label }, uiColors) { sort, on ->
                    sorts = RankingSort.entries.filter { if (it == sort) on else it in sorts }
                }
            }
            if (period) SwitchRow("하루 평균으로 보기", perDay, uiColors) { perDay = it }
            if (view == ResultView.IMAGE) {
                if (!period) SwitchRow("타임라인", timeline, uiColors) { timeline = it }
                OptionRow("모양", null, uiColors) {
                    CompactChoice(RankingImageStyle.entries, style, { it.label }, uiColors) { style = it }
                }
            }
        }

        val (actionLabel, action) = when (view) {
            ResultView.IMAGE -> (if (saving) "저장 중…" else "이미지 ${imageCount}장 저장") to ::saveImages
            ResultView.TEXT -> "텍스트 복사" to {
                clipboard.setText(AnnotatedString(text))
                message = "복사했어요. 붙여 넣어 확인해 주세요"
            }
        }
        Button(
            onClick = action,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
            shape = RoundedCornerShape(12.dp)
        ) { Text(actionLabel, fontWeight = FontWeight.Bold) }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = uiColors.success, modifier = Modifier.padding(top = 8.dp))
        }
        if (guestbookIds.isNotEmpty()) OutlinedButton(
            onClick = { onSendGuestbook(guestbookIds) },
            enabled = !guestbookSending,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (guestbookSending) "방명록을 보내는 중이에요. 끝나면 넣을 수 있어요"
                else "수집된 유저 ${"%,d".format(guestbookIds.size)}명에게 방명록 보내기", textAlign = TextAlign.Center,
                color = if (guestbookSending) uiColors.textSecondary else Color.Unspecified)
        }

        Text(if (view == ResultView.IMAGE) "미리보기 · ${sorts.first().label}" else "미리보기", style = MaterialTheme.typography.labelMedium,
            color = uiColors.textSecondary, modifier = Modifier.padding(top = 16.dp, start = 4.dp, bottom = 6.dp))
        when (view) {
            ResultView.IMAGE -> {
                val image = preview
                if (image == null) Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    BusyIndicator(color = uiColors.primary)
                } else Box(contentAlignment = Alignment.Center) {
                    Image(image, contentDescription = "${sorts.first().label} 이미지 미리보기", contentScale = ContentScale.FillWidth,
                        alpha = if (rendering) 0.5f else 1f,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)))
                    if (rendering) BusyIndicator(color = uiColors.primary)
                }
            }
            ResultView.TEXT -> Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(12.dp)).background(uiColors.surfaceVariant)
                    .verticalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
    // 만든 결과는 저장하지 않으면 사라지니, 새로 만들기 전에 한 번 묻는다. 빈 결과는 잃을 게 없어 바로 넘어간다.
    var confirmNew by remember { mutableStateOf(false) }
    TextButton(onClick = { if (empty) onNew() else confirmNew = true }, modifier = Modifier.fillMaxWidth()) { Text("다른 갤러리·기간으로 새로 만들기") }
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text("새로 만들까요?") },
        text = { Text("지금 결과는 사라져요. 필요하면 먼저 이미지로 저장하거나 텍스트를 복사해 두세요.") },
        confirmButton = {
            // 결과를 버리는 확인이라 반고닉 글 찾기의 '새로 찾기'와 같은 경고색을 쓴다.
            Button(onClick = { confirmNew = false; onNew() }, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.danger)) { Text("새로 만들기") }
        },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("취소", color = uiColors.primary) } }
    )
}

@Composable
private fun OptionGroup(uiColors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(uiColors.surfaceVariant), content = content)
}

/** 옵션 한 줄: 왼쪽 이름(옆에 흐린 보조 글자), 오른쪽 조작. 첫 줄이 아니면 위에 얇은 선. */
@Composable
private fun OptionRow(title: String, hint: String?, uiColors: UiColors, first: Boolean = false, modifier: Modifier = Modifier,
                      control: @Composable () -> Unit) {
    if (!first) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = uiColors.outline.copy(alpha = 0.35f))
    Row(modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary,
            modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        control()
    }
}

/** 줄 전체를 눌러도 켜고 끈다. */
@Composable
private fun SwitchRow(title: String, checked: Boolean, uiColors: UiColors, onChange: (Boolean) -> Unit) {
    OptionRow(title, null, uiColors, modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = uiColors.primary))
    }
}

/** − 숫자 + . 숫자는 직접 고쳐 쓸 수도 있다. 버튼은 [RANK_STEPS] 단계로, 집계한 사람 수([max])에서 멈춘다. */
@Composable
private fun RankStepper(text: String, value: Int, max: Int, uiColors: UiColors, onChange: (String) -> Unit) {
    val previous = previousRankStep(value)
    val next = nextRankStep(value).coerceAtMost(max.coerceAtLeast(1))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { previous?.let { onChange(it.toString()) } }, enabled = previous != null) {
            Icon(Icons.Filled.Remove, contentDescription = previous?.let { "${it}위로 줄이기" } ?: "순위 줄이기")
        }
        BasicTextField(
            value = text,
            onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(uiColors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(48.dp)
        )
        IconButton(onClick = { onChange(next.toString()) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = if (value < max) "${next}위로 늘리기" else "순위 늘리기")
        }
    }
}

/** 옵션 줄 오른쪽에 들어가는 작은 선택. 체크 표시 없이 색으로만 구분한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CompactChoice(options: List<T>, selected: T, label: (T) -> String, uiColors: UiColors, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(activeContainerColor = uiColors.primary.copy(alpha = 0.16f),
                    activeContentColor = uiColors.primary),
                modifier = Modifier.heightIn(max = 40.dp)
            ) { Text(label(option), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** 여러 개 고르는 [ChoiceRow]. 마지막 하나는 끌 수 없다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> MultiChoice(options: List<T>, selected: List<T>, label: (T) -> String, uiColors: UiColors, onToggle: (T, Boolean) -> Unit) {
    MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val checked = option in selected
            SegmentedButton(
                checked = checked,
                onCheckedChange = { if (it || selected.size > 1) onToggle(option, it) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = { SegmentedButtonDefaults.Icon(checked) },
                colors = SegmentedButtonDefaults.colors(activeContainerColor = uiColors.primary.copy(alpha = 0.16f),
                    activeContentColor = uiColors.primary),
                modifier = Modifier.heightIn(max = 40.dp)
            ) { Text(label(option), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** 미리보기용으로 가로세로 절반으로 줄인다. */
private fun ImageBitmap.halfSize(): ImageBitmap {
    val small = ImageBitmap(width / 2, height / 2)
    Canvas(small).drawImageRect(this, dstSize = IntSize(small.width, small.height), paint = Paint().apply { filterQuality = FilterQuality.Medium })
    return small
}

/** DatePicker는 UTC 자정 밀리초를 쓴다. 오늘보다 뒤의 날은 고를 수 없다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingDatePicker(initial: LocalDate, latest: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.date().isAfter(latest)
            override fun isSelectableYear(year: Int) = year <= latest.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.date()) } ?: onDismiss() }) { Text("고르기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@Composable
private fun SectionLabel(text: String, uiColors: UiColors) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = uiColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
private fun RankingCard(uiColors: UiColors, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
