package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.calculateQuote
import com.dccleaner.app.platform.fetchProxyCleanerPricing
import com.dccleaner.app.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun ProxyCleanerEntryButton(
    text: String,
    uiColors: UiColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = uiColors.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ProxyCleanerQuoteDialog(
    uiColors: UiColors,
    initialPostCount: Int,
    initialCommentCount: Int,
    gallList: Map<String, String>,
    selectedGalleries: List<String>,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var pricing by remember { mutableStateOf<ProxyCleanerPricing?>(null) }
    var postCountText by remember(initialPostCount) { mutableStateOf(initialPostCount.toString()) }
    var commentCountText by remember(initialCommentCount) { mutableStateOf(initialCommentCount.toString()) }
    var selectedTimeMethod by remember { mutableStateOf(CleaningMethod.PROXY) }

    LaunchedEffect(Unit) {
        pricing = fetchProxyCleanerPricing()
    }

    val activePricing = pricing ?: ProxyCleanerPricing.Fallback
    val postCount = postCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val commentCount = commentCountText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val quote = activePricing.calculateQuote(postCount, commentCount)
    val directEstimatedSeconds = quote.totalCount.toLong() * 2L
    val applicationUrlReady = activePricing.kakaoOpenChatUrl.isConfiguredKakaoOpenChatUrl()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = uiColors.background) {
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
                    HookSection(uiColors)
                    EstimateInputCard(
                        uiColors = uiColors,
                        postCountText = postCountText,
                        commentCountText = commentCountText,
                        onPostCountChange = { postCountText = it },
                        onCommentCountChange = { commentCountText = it },
                        totalCount = quote.totalCount,
                        galleryScopeText = buildGalleryScopeText(gallList, selectedGalleries)
                    )
                    TimeComparisonCard(
                        uiColors = uiColors,
                        directSeconds = directEstimatedSeconds,
                        proxySeconds = quote.estimatedSeconds,
                        selectedMethod = selectedTimeMethod,
                        onMethodSelected = { selectedTimeMethod = it }
                    )
                    TrustAndDetailsCard(
                        uiColors = uiColors,
                        completedDeletionCount = activePricing.completedDeletionCount,
                        completedDeletionCountAsOf = activePricing.completedDeletionCountAsOf,
                        loading = pricing == null,
                        onOpenDetails = {
                            runCatching { uriHandler.openUri(SERVICE_GUIDE_URL) }
                        }
                    )
                    PriceCard(
                        uiColors = uiColors,
                        loading = pricing == null,
                        originalPrice = quote.originalPrice,
                        finalPrice = quote.finalPrice,
                        discountPercent = quote.discountPercent
                    )
                }

                ProxyCleanerBottomBar(
                    uiColors = uiColors,
                    showScrollAction = scrollState.canScrollForward,
                    loading = pricing == null,
                    applicationUrlReady = applicationUrlReady,
                    totalCount = quote.totalCount,
                    finalPrice = quote.finalPrice,
                    onScrollToBottom = {
                        coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                    },
                    onApply = {
                        clipboard.setText(
                            AnnotatedString(buildProxyCleanerApplicationMessage(
                                postCount = postCount,
                                commentCount = commentCount,
                                gallList = gallList,
                                selectedGalleries = selectedGalleries,
                                totalCount = quote.totalCount,
                                estimatedTime = formatDuration(quote.estimatedSeconds),
                                finalPrice = quote.finalPrice
                            ))
                        )
                        runCatching { uriHandler.openUri(activePricing.kakaoOpenChatUrl) }
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
            Text("대리 클리너", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "1회 작업 · 자동 견적 · 상담 후 결제",
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
private fun HookSection(uiColors: UiColors) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "신경 쓸 필요 없는 빠른 대리 클리너",
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
                BenefitLine("✓", "캡차 자동 해결", "매번 화면을 켜 직접 풀 필요 없이 자동 진행")
                BenefitLine("⚡", "2배 빠른 처리", "답답함 없이 서버에서 빠르게 처리")
                BenefitLine("🔋", "기기 사용 불필요", "배터리 소모, 발열, PC 전기세 걱정 끝")
            }
        }
    }
}

@Composable
private fun BenefitLine(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            icon,
            modifier = Modifier.width(24.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
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
        Text("자동 견적 계산기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "현재 계정의 수량을 불러왔습니다. 실제 삭제할 수량에 맞게 수정할 수 있어요.",
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
private fun TimeComparisonCard(
    uiColors: UiColors,
    directSeconds: Long,
    proxySeconds: Long,
    selectedMethod: CleaningMethod,
    onMethodSelected: (CleaningMethod) -> Unit
) {
    SectionCard(uiColors) {
        Text("예상 소요 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeMethodOption(
                label = "직접 실행",
                selected = selectedMethod == CleaningMethod.DIRECT,
                uiColors = uiColors,
                onClick = { onMethodSelected(CleaningMethod.DIRECT) },
                modifier = Modifier.weight(1f)
            )
            TimeMethodOption(
                label = "대리 클리너",
                selected = selectedMethod == CleaningMethod.PROXY,
                uiColors = uiColors,
                onClick = { onMethodSelected(CleaningMethod.PROXY) },
                modifier = Modifier.weight(1f)
            )
        }
        val selectedSeconds = if (selectedMethod == CleaningMethod.DIRECT) directSeconds else proxySeconds
        val selectedLabel = if (selectedMethod == CleaningMethod.DIRECT) "직접 실행" else "대리 클리너"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(selectedLabel, color = uiColors.textSecondary)
            Text(
                "약 ${formatDuration(selectedSeconds)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (selectedMethod == CleaningMethod.PROXY) uiColors.success
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimeMethodOption(
    label: String,
    selected: Boolean,
    uiColors: UiColors,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) uiColors.primary.copy(alpha = 0.12f) else uiColors.surfaceVariant
        ),
        border = BorderStroke(1.dp, if (selected) uiColors.primary else uiColors.outline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = if (selected) uiColors.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PriceCard(
    uiColors: UiColors,
    loading: Boolean,
    originalPrice: Int,
    finalPrice: Int,
    discountPercent: Int
) {
    SectionCard(uiColors, borderColor = uiColors.primary) {
        Text("최종 견적", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("최신 가격 정보를 불러오는 중…")
            }
        } else {
            if (originalPrice > finalPrice) {
                Text(
                    "${formatNumber(originalPrice)}원",
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${formatNumber(finalPrice)}원",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = uiColors.primary
            )
            if (originalPrice > finalPrice) {
                Text(
                    "${formatNumber(originalPrice - finalPrice)}원 절약 · ${discountPercent}% 할인",
                    color = uiColors.success,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text("최소 결제 금액 적용", color = uiColors.textSecondary)
            }
            Text(
                "수량 구간별 단가와 최소 결제 금액이 자동으로 적용된 견적입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrustAndDetailsCard(
    uiColors: UiColors,
    completedDeletionCount: Long,
    completedDeletionCountAsOf: String,
    loading: Boolean,
    onOpenDetails: () -> Unit
) {
    SectionCard(uiColors) {
        Text(
            if (loading) "🔒 누적 삭제 실적 확인 중…"
            else "🔒 지금까지 누적 ${formatNumber(completedDeletionCount)}개 삭제 완료",
            fontWeight = FontWeight.ExtraBold
        )
        TextButton(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) {
            Text("📄 진행 프로세스 및 상세 안내 보기 >")
        }
        if (!loading) {
            Text(
                "${completedDeletionCountAsOf.replace('-', '.')} 기준",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProxyCleanerBottomBar(
    uiColors: UiColors,
    showScrollAction: Boolean,
    loading: Boolean,
    applicationUrlReady: Boolean,
    totalCount: Int,
    finalPrice: Int,
    onScrollToBottom: () -> Unit,
    onApply: () -> Unit
) {
    Surface(color = uiColors.card, shadowElevation = 10.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val message = when {
                showScrollAction -> "아래에서 최종 견적을 확인해 주세요."
                loading -> "최신 가격 정보를 확인하고 있습니다."
                totalCount == 0 -> "삭제할 게시글 또는 댓글 수를 입력해 주세요."
                !applicationUrlReady -> "카카오톡 상담 링크가 아직 설정되지 않았습니다."
                else -> "견적을 복사한 뒤 상담방에서 진행 방법과 결제를 안내합니다."
            }
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (!loading && totalCount > 0 && !applicationUrlReady) uiColors.danger else uiColors.textSecondary
            )
            Button(
                onClick = if (showScrollAction) onScrollToBottom else onApply,
                enabled = showScrollAction || (!loading && totalCount > 0 && applicationUrlReady),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (showScrollAction) {
                    Text("아래로 내려보기 ↓", fontWeight = FontWeight.Bold)
                } else if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (applicationUrlReady) "💬 ${formatNumber(finalPrice)}원 견적으로 상담하기"
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

private fun String.isConfiguredKakaoOpenChatUrl(): Boolean =
    startsWith("https://open.kakao.com/") && trimEnd('/') != "https://open.kakao.com"

private enum class CleaningMethod {
    DIRECT,
    PROXY
}

internal fun buildProxyCleanerApplicationMessage(
    postCount: Int,
    commentCount: Int,
    gallList: Map<String, String>,
    selectedGalleries: List<String>,
    totalCount: Int,
    estimatedTime: String,
    finalPrice: Int
): String {
    return buildString {
        appendLine("대리 클리너 신청")
        appendLine("게시글 수: $postCount")
        appendLine("댓글 수: $commentCount")
        appendLine(buildGalleryScopeText(gallList, selectedGalleries))
        appendLine("총 삭제 대상: ${totalCount}개")
        appendLine("예상 소요 시간: $estimatedTime")
        appendLine("총 견적 금액: ${finalPrice}원")
        append("결제 방식: 카카오톡 송금하기")
    }
}

internal fun buildGalleryScopeText(
    gallList: Map<String, String>,
    selectedGalleries: List<String>
): String {
    val selectedSet = selectedGalleries.toSet()
    val selected = gallList.filterKeys(selectedSet::contains)
    if (gallList.isEmpty() || selected.size == gallList.size) return "작업 범위: 모든 갤러리"
    if (selected.isEmpty()) return "작업 범위: 선택된 갤러리 없음"

    val excluded = gallList.filterKeys { it !in selectedSet }
    val (description, targets, label) = if (selected.size <= excluded.size) {
        Triple("전체 갤러리 중 ${selected.size}개 갤러리만 삭제", selected, "삭제할 갤러리")
    } else {
        Triple("전체 갤러리 중 ${excluded.size}개 갤러리 제외하고 삭제", excluded, "제외할 갤러리")
    }
    val names = targets.values.joinToString(", ")
    return "작업 범위: $description\n$label: $names"
}

private const val SERVICE_GUIDE_URL =
    "https://github.com/dccleaner3/dccleaner/releases#service"

private fun formatNumber(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

private fun formatNumber(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
