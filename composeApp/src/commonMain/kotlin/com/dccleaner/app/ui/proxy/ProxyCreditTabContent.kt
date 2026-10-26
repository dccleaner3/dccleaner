package com.dccleaner.app.ui.proxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.dccleaner.app.model.CreditLedgerEntry
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.PRIVACY_POLICY_URL
import com.dccleaner.app.model.PurchaseActivity
import com.dccleaner.app.model.RecentCreditPurchase
import com.dccleaner.app.model.hasSummary
import com.dccleaner.app.model.summary
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.calculateQuote
import com.dccleaner.app.model.creditLedgerKindLabel
import com.dccleaner.app.model.formatTimeAgo
import com.dccleaner.app.model.formatWon
import com.dccleaner.app.platform.currentTimeMillis
import com.dccleaner.app.ui.dialog.formatNumber
import com.dccleaner.app.ui.dialog.isConfiguredKakaoOpenChatUrl
import kotlinx.coroutines.delay

@Composable
fun ProxyCreditTabContent(
    uiColors: UiColors,
    controller: ProxyCleanerController,
    initialPostCount: Int,
    initialCommentCount: Int,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenProgress: (() -> Unit)?,
    onCharge: (kakaoOpenChatUrl: String, requestedCount: Int) -> Unit,
    onShowPendingCharge: (CreditPurchase) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val activePricing = controller.pricing
    val kakaoReady = activePricing?.kakaoOpenChatUrl?.isConfiguredKakaoOpenChatUrl() == true
    var postCountText by remember(initialPostCount) { mutableStateOf(initialPostCount.toString()) }
    var commentCountText by remember(initialCommentCount) { mutableStateOf(initialCommentCount.toString()) }
    val postCount = postCountText.toIntOrNull() ?: 0
    val commentCount = commentCountText.toIntOrNull() ?: 0
    val quote = activePricing?.calculateQuote(postCount, commentCount)
    var showLedger by remember { mutableStateOf(false) }
    // 탭을 열 때 한 번만 최근 충전을 읽는다. 'n분 전'은 기기에서 흘러가니 다시 물을 필요가 없다.
    LaunchedEffect(controller) { controller.loadPurchaseActivity() }

    Column(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                focusManager.clearFocus()
            }
        },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BalanceCard(uiColors, loading = controller.isRefreshing) {
            Text("사용 가능한 삭제 개수", color = uiColors.textSecondary)
            Text(
                controller.balance?.let { "${formatNumber(it)}개" } ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    maxFontSize = MaterialTheme.typography.headlineLarge.fontSize
                ),
                fontWeight = FontWeight.ExtraBold
            )
            Text(if (controller.hasActiveJob) "진행 중인 작업이 삭제하는 만큼 줄어들어요. 여러 계정의 작업이 같은 잔고를 나눠 써요."
                else "글·댓글 1개 삭제할 때마다 1개씩 사용해요. 남은 개수는 다음에도, 다른 계정에도 쓸 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
            controller.loadError?.let { Text(it, color = uiColors.danger, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onRefresh, enabled = !controller.isRefreshing) {
                Text(if (controller.isRefreshing) "새로고침 중…" else "새로고침")
            }
        }
        // 신청 뒤 앱으로 돌아온 사람에게 지금 무엇을 기다리는지 보여 준다. 충전되면 컨트롤러가 비운다.
        controller.purchase?.let { pending ->
            BalanceCard(uiColors) {
                Text("충전 진행 중 · 3단계 중 ${if (controller.requestSeen) 2 else 1}단계",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // 신청서가 확인되기 전에는 송금 안내를 하지 않는다. 확인되면 화면 읽기 프로그램이 바뀐 안내를 읽어 준다.
                Text(
                    if (controller.requestSeen) "${formatNumber(pending.creditedCount)}개 충전 신청을 확인했어요. " +
                        "${formatWon(pending.amountWon.toDouble())}을 송금해 주세요."
                    else "${formatNumber(pending.creditedCount)}개 충전 신청서를 오픈채팅에 보내 주세요.",
                    color = uiColors.textSecondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                OutlinedButton(
                    onClick = { onShowPendingCharge(pending) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("이어서 하기") }
            }
        }
        BalanceCard(uiColors, loading = controller.isCreatingPurchase) {
            Text("삭제 개수 충전", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("삭제할 글·댓글 수를 필요한 만큼 조정해 주세요.",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountField(postCountText, { postCountText = it }, "게시글 수", Modifier.weight(1f))
                CountField(commentCountText, { commentCountText = it }, "댓글 수", Modifier.weight(1f))
            }
            if (activePricing == null || quote == null) {
                Text(
                    if (controller.pricingFailed) "가격을 불러오지 못했어요. 위의 새로고침을 눌러 다시 시도해 주세요."
                    else "가격을 불러오고 있어요…",
                    color = if (controller.pricingFailed) uiColors.danger else uiColors.textSecondary
                )
                return@BalanceCard
            }
            val confirmedPurchase = controller.purchase?.takeIf { it.requestedCount == quote.totalCount }
            val purchasedCount = confirmedPurchase?.creditedCount ?: quote.creditedCount
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("충전되는 삭제 개수", color = uiColors.textSecondary)
                Text("${formatNumber(purchasedCount)}개", fontWeight = FontWeight.Bold)
            }
            if (quote.discountPercent > 0) Text(
                "대량 할인 적용", color = uiColors.success, style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(if (confirmedPurchase == null) "예상 결제 금액" else "결제 금액", modifier = Modifier.align(Alignment.Bottom), color = uiColors.textSecondary)
                Text(
                    formatWon((confirmedPurchase?.amountWon ?: quote.finalPrice).toDouble()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                // 입력한 개수보다 많이 충전되는 이유를 그 자리에서 알려 준다.
                if (purchasedCount > quote.totalCount)
                    "최소 결제 ${formatWon(activePricing.minimumPrice.toDouble())}이라 ${formatNumber(purchasedCount)}개를 드려요 · 남은 개수는 다음에도 사용"
                else "최소 결제 ${formatWon(activePricing.minimumPrice.toDouble())} · 남은 개수는 다음에도 사용",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary
            )
            // 결정하는 버튼 바로 위에서, 실제로 충전한 사람이 있다는 것만 한 줄로 알린다.
            controller.purchaseActivity?.takeIf { it.hasSummary }?.let { PurchaseActivityLine(uiColors, it, Modifier.fillMaxWidth()) }
            Button(
                onClick = {
                    onCharge(
                        activePricing.kakaoOpenChatUrl,
                        quote.totalCount
                    )
                },
                enabled = quote.totalCount > 0 && kakaoReady && !controller.isCreatingPurchase,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)
            ) {
                Text(
                    if (controller.isCreatingPurchase) "최종 견적 확인 중…"
                    else if (quote.totalCount > 0) "${formatNumber(purchasedCount)}개 충전하기" else "충전하기",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Text(if (kakaoReady) "누르면 결제 금액을 확정하고 송금 방법을 알려 드려요"
                else "카카오톡 상담 링크가 아직 설정되지 않았습니다. 잠시 후 다시 시도해 주세요.",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        }
        // 요약 줄과 같은 기준으로, 활동이 뜸하면(며칠 전 한두 건뿐이면) 목록도 숨긴다. '0건'도 보여 주지 않는다.
        controller.purchaseActivity?.takeIf { it.hasSummary && it.recent.isNotEmpty() }?.let { RecentPurchasesCard(uiColors, it.recent) }
        if (controller.hasActiveJob && onOpenProgress != null) {
            UltraButton("진행 중인 초고속 삭제 보기", onOpenProgress, Modifier.fillMaxWidth())
        }

        if (controller.ledger.isNotEmpty()) BalanceCard(uiColors) {
            TextButton(onClick = { showLedger = !showLedger }) {
                Text(if (showLedger) "충전·사용 내역 접기" else "충전·사용 내역 보기")
            }
            if (showLedger) {
                controller.ledger.forEach { LedgerRow(uiColors, it) }
            }
        }

        TextButton(onClick = { onOpenUrl(PRIVACY_POLICY_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text("개인정보처리방침", color = uiColors.textSecondary)
        }
    }
}

@Composable
private fun CountField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(9)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
    )
}

@Composable
private fun LedgerRow(uiColors: UiColors, entry: CreditLedgerEntry) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(creditLedgerKindLabel(entry.kind), fontWeight = FontWeight.Medium)
            Text(
                listOf(entry.createdAt, entry.note).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary
            )
        }
        Text(
            (if (entry.delta > 0) "+" else "") + "${formatNumber(entry.delta)}개",
            color = if (entry.delta > 0) uiColors.success else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecentPurchasesCard(uiColors: UiColors, purchases: List<RecentCreditPurchase>) {
    val now by rememberTickingNow()
    BalanceCard(uiColors) {
        Text("최근 충전", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        // 아래의 '충전·사용 내역'(내 기록)과 헷갈리지 않게 누구의 기록인지 밝힌다.
        // 개수·금액이 왜 없는지 밝혀 숨기는 것처럼 보이지 않게 하고, 실제 지급된 건만이라는 기준을 알린다.
        // 좁은 화면에서 낱말 중간('누/가')에서 끊기지 않게 문장마다 줄을 나눈다.
        Text("모든 사용자 중 송금이 확인된 충전만 보여요\n누가 얼마를 충전했는지는 공개하지 않아요",
            style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        purchases.forEach { purchase ->
            val secondsAgo = (now - purchase.creditedAtMillis) / 1_000
            // 알림 목록처럼 아이콘·내용은 왼쪽, 시각은 오른쪽. 한 시간 안의 충전만 시각을 강조해 반복되는 줄에 차이를 준다.
            // 화면 읽기 프로그램이 '충전 완료, 3분 전'을 한 항목으로 읽게 묶는다.
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = uiColors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("충전 완료", Modifier.weight(1f))
                Text(formatTimeAgo(secondsAgo), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    color = if (secondsAgo < 3_600) uiColors.success else uiColors.textSecondary)
            }
        }
    }
}

/** 'n초 전'이 흘러가도록 보이는 동안만 1초마다 바뀌는 지금 시각. 지급 시각은 서버가 준 경과 초로 구해 기기 시계 오차가 없다. */
@Composable
private fun rememberTickingNow() = produceState(currentTimeMillis()) {
    while (true) {
        delay(1_000)
        value = currentTimeMillis()
    }
}

/**
 * 최근 실제 충전을 한 줄로 요약한다(견적 화면·충전 탭). 보여 줄 만큼 활동이 없으면 아무것도 그리지 않는다.
 * 숫자를 부풀리거나 재촉하지 않고, 사실만 조용히 알린다.
 */
@Composable
internal fun PurchaseActivityLine(uiColors: UiColors, activity: PurchaseActivity, modifier: Modifier = Modifier) {
    val now by rememberTickingNow()
    val parts = activity.summary(now) ?: return
    // 폭이 모자라면 '마지막 충전 n분 전'이 통째로 다음 줄로 가서, 글자 하나만 떨어지지 않는다.
    // 두 덩어리는 가운뎃점 대신 간격과 색으로 나눈다. 줄이 바뀌어도 둘째 줄이 기호로 시작하지 않는다.
    FlowRow(
        modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        // 점과 첫 문장은 한 덩어리라, 글자를 크게 키워도 점만 따로 한 줄에 남지 않는다.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(uiColors.success, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(parts.first(), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        parts.getOrNull(1)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BalanceCard(uiColors: UiColors, loading: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
            // 카드 윗변에 겹쳐 그려 요청 중에도 내용이 밀리지 않는다.
            RequestProgressBar(loading, Modifier.align(Alignment.TopCenter),
                color = uiColors.primary)
        }
    }
}
