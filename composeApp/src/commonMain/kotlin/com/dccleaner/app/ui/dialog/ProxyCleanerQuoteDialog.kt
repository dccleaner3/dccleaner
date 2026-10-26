package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.hasSummary
import com.dccleaner.app.ui.proxy.PurchaseActivityLine
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.calculateQuote
import com.dccleaner.app.platform.fetchProxyCleanerPricing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.dccleaner.app.ui.theme.BusyIndicator

internal const val TWO_CAPTCHA_DELETIONS_PER_HOUR = 1_800

@Composable
fun ProxyCleanerEntryButton(
    uiColors: UiColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = uiColors.primary.copy(alpha = 0.12f), contentColor = uiColors.primary)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("더 빠르고 편하게 · 초고속 삭제", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text("유료 · 시간과 비용 비교", style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ProxyCleanerQuoteDialog(
    uiColors: UiColors,
    initialPostCount: Int,
    initialCommentCount: Int,
    gallList: Map<String, String>,
    selectedGalleries: List<String>,
    onOpenTwoCaptchaSetup: () -> Unit,
    onDismiss: () -> Unit,
    onOpenCredits: ((postCount: Int, commentCount: Int) -> Unit)? = null,
    /** 다른 사람들의 최근 실제 충전. 충전으로 이어지는 흐름에서만 버튼 위에 한 줄로 보여 준다. */
    purchaseActivity: PurchaseActivity? = null
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var useTwoCaptcha by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf<ProxyCleanerPricing?>(null) }
    var pricingFailed by remember { mutableStateOf(false) }
    var postCountText by remember { mutableStateOf(initialPostCount.toString()) }
    var commentCountText by remember { mutableStateOf(initialCommentCount.toString()) }
    LaunchedEffect(Unit) {
        pricing = fetchProxyCleanerPricing()
        pricingFailed = pricing == null
    }

    val postCount = postCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val commentCount = commentCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val quote = pricing?.calculateQuote(postCount, commentCount)
    val totalCount = postCount + commentCount
    val directEstimatedSeconds = (totalCount.toLong() * 3_600L + TWO_CAPTCHA_DELETIONS_PER_HOUR - 1) / TWO_CAPTCHA_DELETIONS_PER_HOUR
    val hourlyCount = pricing?.let { hourlyDeletionCount(it.deletionsPerSecond) }
    // 사용자가 직접 삭제하는 속도는 측정값과 상관없이 항상 시간당 1,800개로 본다.
    val multiplier = hourlyCount?.let { speedMultiplier(it, TWO_CAPTCHA_DELETIONS_PER_HOUR) }
    val applicationUrlReady = pricing?.kakaoOpenChatUrl?.isConfiguredKakaoOpenChatUrl() == true

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    focusManager.clearFocus()
                }
            },
            color = uiColors.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ProxyCleanerTopBar(onDismiss)
                HorizontalDivider(color = uiColors.outline)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HookSection(uiColors, hourlyCount, multiplier)
                    EstimateInputCard(
                        uiColors = uiColors,
                        postCountText = postCountText,
                        commentCountText = commentCountText,
                        onPostCountChange = { postCountText = it },
                        onCommentCountChange = { commentCountText = it },
                        totalCount = totalCount,
                        galleryScopeText = buildGalleryScopeText(gallList, selectedGalleries)
                    )
                    MethodComparisonCard(
                        uiColors = uiColors,
                        directSeconds = directEstimatedSeconds,
                        proxySeconds = quote?.estimatedSeconds,
                        speedMultiplier = multiplier ?: 0.0,
                        totalCount = totalCount,
                        pricingFailed = pricingFailed,
                        finalPrice = quote?.finalPrice
                    )
                    TextButton(
                        onClick = { runCatching { uriHandler.openUri(SERVICE_GUIDE_URL) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("초고속 삭제 상세 안내 보기") }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("진행 방법 선택", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("초고속 삭제", "2Captcha").forEachIndexed { index, title ->
                                val chosen = useTwoCaptcha == (index == 1)
                                OutlinedButton(
                                    onClick = { useTwoCaptcha = index == 1 },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = chosen },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) uiColors.primary else uiColors.outline),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (chosen) uiColors.primary.copy(alpha = 0.08f) else uiColors.card)
                                ) { Text(title, textAlign = TextAlign.Center) }
                            }
                        }
                        if (!useTwoCaptcha) {
                            Text(
                                if (onOpenCredits != null) "삭제 개수 충전 → '초고속 삭제' 켜고 삭제 시작 → 앱을 꺼도 서버에서 진행"
                                else "견적 붙여넣기 → 상담 → 결제 → 로그인 정보 전달 → 삭제 작업 시작",
                                style = MaterialTheme.typography.bodySmall,
                                color = uiColors.textSecondary
                            )
                        }
                    }
                }

                ProxyCleanerBottomBar(
                    uiColors = uiColors,
                    showScrollAction = scrollState.canScrollForward,
                    loading = !useTwoCaptcha && pricing == null && !pricingFailed,
                    pricingFailed = !useTwoCaptcha && pricingFailed,
                    applicationUrlReady = useTwoCaptcha || onOpenCredits != null || applicationUrlReady,
                    creditsFlow = onOpenCredits != null,
                    purchaseActivity = purchaseActivity,
                    useTwoCaptcha = useTwoCaptcha,
                    totalCount = totalCount,
                    onScrollToBottom = {
                        coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    },
                    onApply = {
                        if (useTwoCaptcha) {
                            onOpenTwoCaptchaSetup()
                        } else if (onOpenCredits != null) {
                            onOpenCredits(postCount, commentCount)
                        } else if (quote != null) {
                        clipboard.setText(
                            AnnotatedString(buildProxyCleanerApplicationMessage(
                                totalCount = quote.totalCount,
                                finalPrice = quote.finalPrice
                            ))
                        )
                        pricing?.kakaoOpenChatUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProxyCleanerTopBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("삭제 방법 비교", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "방법별 시간과 비용 비교",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "닫기")
        }
    }
}

@Composable
private fun HookSection(uiColors: UiColors, hourlyCount: Int?, multiplier: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "신경 쓸 필요 없는 초고속 삭제",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = uiColors.card),
            border = BorderStroke(1.dp, uiColors.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BenefitLine("✓", "캡챠 자동 해결", "매번 화면을 켜 직접 풀 필요 없이 자동 진행")
                BenefitLine(
                    "⚡",
                    if (multiplier == null) "삭제 속도 확인 중"
                    else if (multiplier > 1) "평균 ${multiplier.toString().removeSuffix(".0")}배 빠른 처리"
                    else "서버에서 자동 처리",
                    if (hourlyCount == null) "최신 속도를 불러오고 있습니다"
                    else "시간당 약 ${formatNumber(hourlyCount)}개 처리"
                )
                BenefitLine("🔋", "기기 사용 불필요", "배터리 소모, 발열, PC 전기세 걱정 끝")
            }
        }
    }
}

internal fun hourlyDeletionCount(deletionsPerSecond: Double): Int =
    (deletionsPerSecond * 3_600).roundToInt()

internal fun speedMultiplier(hourlyCount: Int, directHourlyCount: Int): Double =
    ((hourlyCount / directHourlyCount.coerceAtLeast(1).toDouble()) * 10).roundToInt() / 10.0

@Composable
private fun BenefitLine(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, modifier = Modifier.width(24.dp), style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EstimateInputCard(
    uiColors: UiColors,
    postCountText: String,
    commentCountText: String,
    onPostCountChange: (String) -> Unit,
    onCommentCountChange: (String) -> Unit,
    totalCount: Int,
    galleryScopeText: String
) {
    SectionCard(uiColors) {
        Text("삭제할 수량", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "계정 전체 수량 · 삭제할 만큼 수정",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountField(postCountText, onPostCountChange, "게시글 수", Modifier.weight(1f))
            CountField(commentCountText, onCommentCountChange, "댓글 수", Modifier.weight(1f))
        }
        Text(
            "총 ${formatNumber(totalCount)}개 삭제",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            galleryScopeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MethodComparisonCard(
    uiColors: UiColors,
    directSeconds: Long,
    proxySeconds: Long?,
    speedMultiplier: Double,
    totalCount: Int,
    pricingFailed: Boolean,
    finalPrice: Int?
) {
    val missing = if (pricingFailed) "불러오지 못함" else "확인 중…"
    SectionCard(uiColors) {
        Text("시간·비용 비교", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("초고속 삭제", "2Captcha").forEach { name ->
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        HorizontalDivider(color = uiColors.outline)
        ComparisonRow("예상 소요 시간", proxySeconds?.let(::formatApproximateDuration) ?: missing, formatApproximateDuration(directSeconds),
            advantage = proxySeconds?.compareTo(directSeconds) ?: 0, advantageColor = uiColors.success,
            proxyCaption = if (proxySeconds != null && speedMultiplier > 1)
                "2Captcha보다 평균 ${speedMultiplier.toString().removeSuffix(".0")}배 빠름" else null)
        HorizontalDivider(color = uiColors.outline)
        ComparisonRow("결제금액", finalPrice?.let { "${formatNumber(it)}원" } ?: missing, "${formatNumber(twoCaptchaPayment(totalCount))}원",
            // 처음 내는 돈은 강조하지 않는다. 2Captcha는 최소 충전 금액을 먼저 내고 쓴 만큼 차감돼 실제 비용은 더 적다.
            twoCaptchaCaption = if (twoCaptchaEstimatedCost(totalCount) < twoCaptchaPayment(totalCount)) "최소 충전 금액" else null)
        HorizontalDivider(color = uiColors.outline)
        ComparisonRow("기기 사용", "앱을 끄고 진행", "앱에서 직접 실행", secondary = true,
            advantage = -1, advantageColor = uiColors.success)
        HorizontalDivider(color = uiColors.outline)
        ComparisonRow("캡챠 해결", "자동", "자동", secondary = true, valueColor = uiColors.success, boldValues = true)
    }
}

@Composable
private fun ComparisonRow(label: String, proxy: String, twoCaptcha: String, secondary: Boolean = false,
    advantage: Int = 0, advantageColor: Color = Color.Unspecified, valueColor: Color = Color.Unspecified,
    boldValues: Boolean = false, proxyCaption: String? = null, twoCaptchaCaption: String? = null) {
    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top) {
            listOf(proxy, twoCaptcha).forEachIndexed { index, value ->
                val preferred = if (index == 0) advantage < 0 else advantage > 0
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, modifier = Modifier.semantics {
                        if (preferred) stateDescription = "더 유리한 조건"
                    }, color = if (preferred) advantageColor else valueColor,
                        fontWeight = if (preferred || boldValues) FontWeight.Bold else null,
                        textAlign = TextAlign.Center,
                        style = if (secondary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge)
                    if (index == 0 && proxyCaption != null) Text(proxyCaption,
                        style = MaterialTheme.typography.bodySmall, color = advantageColor,
                        textAlign = TextAlign.Center)
                    if (index == 1 && twoCaptchaCaption != null) Text(twoCaptchaCaption,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

internal fun twoCaptchaEstimatedCost(totalCount: Int): Int =
    ((totalCount.coerceAtLeast(0).toLong() + 199) / 200 * 4).toInt()

internal fun twoCaptchaPayment(totalCount: Int): Int = maxOf(4_500, twoCaptchaEstimatedCost(totalCount))

internal fun formatApproximateDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0분"
    if (totalSeconds < 60L) return "1분 미만"
    val roundedMinutes = if (totalSeconds < 3_600L) {
        (totalSeconds + 30L) / 60L
    } else {
        (totalSeconds + 900L) / 1_800L * 30L
    }
    val hours = roundedMinutes / 60L
    val minutes = roundedMinutes % 60L
    if (hours >= 24L) return buildList {
        add("${hours / 24}일")
        if (hours % 24 != 0L) add("${hours % 24}시간")
        if (minutes != 0L) add("${minutes}분")
    }.joinToString(" ")
    return when {
        hours == 0L -> "${minutes}분"
        minutes == 0L -> "${hours}시간"
        else -> "${hours}시간 ${minutes}분"
    }
}

@Composable
private fun ProxyCleanerBottomBar(
    uiColors: UiColors,
    showScrollAction: Boolean,
    loading: Boolean,
    pricingFailed: Boolean = false,
    applicationUrlReady: Boolean,
    totalCount: Int,
    useTwoCaptcha: Boolean,
    creditsFlow: Boolean = false,
    purchaseActivity: PurchaseActivity? = null,
    onScrollToBottom: () -> Unit,
    onApply: () -> Unit
) {
    Surface(color = uiColors.card, shadowElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val message = when {
                pricingFailed -> "가격을 불러오지 못했어요. 인터넷 연결을 확인하고 다시 열어 주세요."
                loading -> "최신 가격 정보를 확인하고 있습니다."
                totalCount == 0 -> "삭제할 게시글 또는 댓글 수를 입력해 주세요."
                !applicationUrlReady -> "카카오톡 상담 링크가 아직 설정되지 않았습니다."
                useTwoCaptcha -> null
                creditsFlow -> CREDITS_FLOW_MESSAGE
                else -> "보통 10분 내 답장"
            }
            // 충전 흐름의 기본 안내(본문에도 있는 진행 순서) 자리에는, 결정하는 순간 실제 충전 활동을 보여 준다.
            // 보여 줄 활동이 없으면 원래 안내를 그대로 둔다.
            val activityLine = purchaseActivity?.takeIf { creditsFlow && message == CREDITS_FLOW_MESSAGE && it.hasSummary }
            if (activityLine != null) {
                PurchaseActivityLine(uiColors, activityLine, Modifier.fillMaxWidth())
            } else if (message != null) {
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (!useTwoCaptcha && applicationUrlReady) FontWeight.Bold else null,
                    color = when {
                        pricingFailed || (!loading && totalCount > 0 && !applicationUrlReady) -> uiColors.danger
                        !useTwoCaptcha && applicationUrlReady -> uiColors.success
                        else -> uiColors.textSecondary
                    }
                )
            }
            Button(
                onClick = if (showScrollAction) onScrollToBottom else onApply,
                enabled = showScrollAction || (!loading && !pricingFailed && totalCount > 0 && applicationUrlReady),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (showScrollAction) {
                    Text("시간·비용 비교하기 ↓", fontWeight = FontWeight.Bold)
                } else if (loading) {
                    BusyIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (useTwoCaptcha) "2Captcha 설정하기"
                        else if (creditsFlow) "삭제 개수 충전하기"
                        else if (applicationUrlReady) "카카오톡 상담하기"
                        else "상담 링크 준비 중",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    uiColors: UiColors,
    borderColor: androidx.compose.ui.graphics.Color = uiColors.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun CountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(9)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

internal fun String.isConfiguredKakaoOpenChatUrl(): Boolean =
    startsWith("https://open.kakao.com/") && trimEnd('/') != "https://open.kakao.com"

internal fun buildProxyCleanerApplicationMessage(
    totalCount: Int,
    finalPrice: Int
): String {
    return buildString {
        appendLine("잔고 충전하기")
        appendLine("총 삭제 대상: ${totalCount}개")
        appendLine("총 견적 금액: ${finalPrice}원")
        append("결제 방식: 오픈채팅 송금하기")
    }
}

internal fun buildGalleryScopeText(
    gallList: Map<String, String>,
    selectedGalleries: List<String>
): String {
    val selectedSet = selectedGalleries.toSet()
    val selected = gallList.filterKeys(selectedSet::contains)
    if (gallList.isEmpty() || selected.size == gallList.size) return "작업 범위: 전체 갤러리에서 삭제"
    if (selected.isEmpty()) return "작업 범위: 선택된 갤러리 없음"

    val excluded = gallList.filterKeys { it !in selectedSet }
    val (description, targets, label) = if (selected.size <= excluded.size) {
        Triple("선택한 ${selected.size}개 갤러리에서만 삭제", selected, "삭제할 갤러리")
    } else {
        Triple("전체 갤러리 중 ${excluded.size}개 갤러리 제외하고 삭제", excluded, "제외할 갤러리")
    }
    val names = targets.values.joinToString(", ")
    return "작업 범위: $description\n$label: $names"
}

private const val CREDITS_FLOW_MESSAGE = "충전 후 앱에서 바로 시작할 수 있습니다"

private const val SERVICE_GUIDE_URL =
    "https://github.com/dccleaner3/dccleaner/releases#service"

private fun formatNumber(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

internal fun formatNumber(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
