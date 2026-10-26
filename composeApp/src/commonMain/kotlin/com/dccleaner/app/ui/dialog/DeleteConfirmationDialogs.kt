package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.ProxyCleanerPricing
import com.dccleaner.app.model.ProxyCleanerQuote
import com.dccleaner.app.model.calculateQuote
import com.dccleaner.app.model.formatWon
import com.dccleaner.app.ui.proxy.ULTRA_COLORS
import com.dccleaner.app.ui.proxy.UltraButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.semantics.Role
import com.dccleaner.app.ui.proxy.rememberUltraBrush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color

/** Extra checks shown when the deletion runs on the proxy cleaner server. */
data class ProxyStartSummary(
    val estimatedCount: Int,
    val estimateIsUpperBound: Boolean,
    /** Available deletion count. */
    val balance: Long?,
    val activeJob: Boolean,
    val starting: Boolean,
    val speedMultiplier: Double?,
    /** "최근 글부터 삭제" slows the server down, so it can't be combined with 초고속 삭제. */
    val newestFirstConflict: Boolean = false,
    val regexConflict: Boolean = false,
    val pricing: ProxyCleanerPricing? = null,
    /** 충전 탭에 채워 넣을 갤로그 글·댓글 수. */
    val chargePostCount: Int = 0,
    val chargeCommentCount: Int = 0
) {
    /** 충전 탭에 채우는 글·댓글 수 그대로의 예상 견적. 최소 결제 금액이 있어 개수가 더 늘 수 있다. */
    val chargeQuote: ProxyCleanerQuote?
        get() = pricing?.calculateQuote(chargePostCount, chargeCommentCount)
}

/** 초고속 삭제를 글부터 하려 할 때 권하는 방법: 댓글 삭제로 바꾸기(한 종류) 또는 순서를 댓글 먼저로(함께 지울 때). */
enum class CommentFirstAdviceMode { SWITCH, REORDER }

/** 글+댓글을 함께 지울 때 시작 확인 창의 한 단계. */
data class DeleteDialogPhase(val label: String, val galleryNames: List<String>, val filters: List<String>)

@Composable
private fun PhaseSummary(index: Int, phase: DeleteDialogPhase, uiColors: UiColors) {
    var expanded by remember { mutableStateOf(false) }
    val summary = phase.galleryNames.take(2).joinToString(" · ") +
        if (phase.galleryNames.size > 2) " 외 ${phase.galleryNames.size - 2}개" else ""
    Surface(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), color = uiColors.surfaceVariant,
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${index + 1}. ${phase.label} · 갤러리 ${phase.galleryNames.size}개", fontWeight = FontWeight.SemiBold)
                    if (!expanded) Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis, color = uiColors.textSecondary)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "${phase.label} 갤러리 접기" else "${phase.label} 갤러리 펼치기"
                )
            }
            if (expanded) phase.galleryNames.forEach { Text(it, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(8.dp))
            phase.filters.ifEmpty { listOf("추가 조건 없음") }.forEach { Text("• $it", color = uiColors.textSecondary) }
        }
    }
}

@Composable
fun StartDeletionDialog(
    uiColors: UiColors,
    deleteType: String,
    selectedGalleryCount: Int,
    selectedGalleryNames: List<String>,
    checkingSession: Boolean,
    questionPostsMayBeExcluded: Boolean,
    activeFilters: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    proxy: ProxyStartSummary? = null,
    onCharge: () -> Unit = {},
    useProxy: Boolean = false,
    onUseProxyChange: (Boolean) -> Unit = {},
    onDisableNewestFirst: () -> Unit = {},
    /** 계정에 남은 댓글 수. 초고속 삭제로 글부터 지우려 할 때 댓글 먼저 지우기를 권한다. */
    remainingCommentCount: Int = 0,
    onSwitchToComments: () -> Unit = {},
    /** 함께 지울 때 순서를 댓글 먼저로 바꾼다(창은 그대로). */
    onReorderCommentFirst: () -> Unit = {},
    /** 댓글부터 지우라고 권할지와 그 방법. null이면 권하지 않는다. */
    commentFirstAdvice: CommentFirstAdviceMode? = if (deleteType == "posting") CommentFirstAdviceMode.SWITCH else null,
    /** 글+댓글을 함께 지울 때 순서대로의 단계. 비어 있으면 한 종류만 지운다. */
    phases: List<DeleteDialogPhase> = emptyList()
) {
    val activeProxy = proxy?.takeIf { useProxy }
    val needsCharge = activeProxy != null && !activeProxy.activeJob && activeProxy.balance != null &&
        activeProxy.balance < 1L
    val proxyBlocked = proxy?.activeJob == true || proxy?.starting == true || (activeProxy != null && activeProxy.balance == null)
    val targetLabel = if (deleteType == "posting") "게시글" else "댓글"
    // 함께 지우면 설정 중이 아닌 종류에 켜져 있을 수도 있어 종류를 빼고 말한다.
    val newestFirstLabel = when {
        phases.isNotEmpty() -> "최근부터 삭제"
        deleteType == "posting" -> "최근 글부터 삭제"
        else -> "최근 댓글부터 삭제"
    }
    var galleriesExpanded by remember { mutableStateOf(false) }
    val gallerySummary = selectedGalleryNames.take(2).joinToString(" · ") +
        if (selectedGalleryNames.size > 2) " 외 ${selectedGalleryNames.size - 2}개" else ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(20.dp).heightIn(max = 760.dp),
            color = uiColors.card,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (phases.isNotEmpty()) {
                        item {
                            Text("${phases.joinToString("과 ") { it.label }}을 차례로 삭제할까요?",
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("지운 글·댓글은 되돌릴 수 없어요", color = uiColors.danger, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("${phases.first().label}을 다 지우면 이어서 ${phases.last().label}을 자동으로 지워요",
                                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
                        }
                        phases.forEachIndexed { index, phase -> item { PhaseSummary(index, phase, uiColors) } }
                        if (questionPostsMayBeExcluded) item {
                            Text("질문글 등 일부 글은 빠질 수 있어요", style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
                        }
                    } else item {
                        Text("${targetLabel}을 삭제할까요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("지운 글·댓글은 되돌릴 수 없어요", color = uiColors.danger, fontWeight = FontWeight.Medium)
                    }
                    if (phases.isEmpty()) item {
                        Surface(
                            onClick = { galleriesExpanded = !galleriesExpanded },
                            modifier = Modifier.fillMaxWidth(),
                            color = uiColors.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("삭제할 갤러리 · ${selectedGalleryCount}개", fontWeight = FontWeight.SemiBold)
                                    if (!galleriesExpanded) Text(
                                        gallerySummary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = uiColors.textSecondary
                                    )
                                }
                                Icon(
                                    if (galleriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (galleriesExpanded) "갤러리 목록 접기" else "갤러리 목록 펼치기"
                                )
                            }
                        }
                    }
                    if (phases.isEmpty() && galleriesExpanded) items(selectedGalleryNames) { galleryName ->
                        Text(galleryName, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        HorizontalDivider(color = uiColors.outline)
                    }
                    if (phases.isEmpty()) item {
                        Spacer(Modifier.height(4.dp))
                        Text("적용 조건", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        activeFilters.ifEmpty { listOf("추가 조건 없음") }.forEach { filter ->
                            Text("• $filter", color = uiColors.textSecondary)
                        }
                        if (questionPostsMayBeExcluded) {
                            Spacer(Modifier.height(8.dp))
                            Text("질문글 등 일부 글은 빠질 수 있어요", style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
                        }
                    }
                    if (proxy != null) item {
                        val shape = RoundedCornerShape(12.dp)
                        Surface(
                            color = if (useProxy) ULTRA_COLORS[0].copy(alpha = 0.08f) else uiColors.surfaceVariant,
                            shape = shape,
                            border = if (useProxy) BorderStroke(1.dp, rememberUltraBrush()) else null
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().toggleable(
                                        useProxy,
                                        enabled = !proxy.newestFirstConflict && !proxy.regexConflict,
                                        role = Role.Switch,
                                        onValueChange = onUseProxyChange
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("⚡ 초고속 삭제 사용", fontWeight = FontWeight.Bold)
                                        val speed = proxy.speedMultiplier?.takeIf { it > 1 }
                                            ?.let { "평균 ${it.toString().removeSuffix(".0")}배 빠르게" } ?: "더 빠르게"
                                        Text(
                                            when {
                                                proxy.regexConflict -> "정규식 조건은 직접 삭제에서만 사용할 수 있어요 · 조건을 해제한 뒤 켜 주세요"
                                                proxy.newestFirstConflict -> "${newestFirstLabel}를 켜면 쓸 수 없어요 · 속도가 느려져 지원하지 않아요"
                                                useProxy -> "캡챠 없이 $speed · 앱을 꺼도 서버에서 계속"
                                                else -> "켜면 캡챠 없이 $speed 삭제해요"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = uiColors.textSecondary
                                        )
                                    }
                                    Switch(
                                        checked = useProxy,
                                        onCheckedChange = null,
                                        enabled = !proxy.newestFirstConflict && !proxy.regexConflict,
                                        colors = SwitchDefaults.colors(checkedTrackColor = ULTRA_COLORS[0], checkedThumbColor = Color.White)
                                    )
                                }
                                if (proxy.newestFirstConflict && !proxy.regexConflict) TextButton(onClick = onDisableNewestFirst, contentPadding = PaddingValues(0.dp)) {
                                    Text("${newestFirstLabel} 끄고 초고속 삭제 쓰기")
                                }
                                if (useProxy) {
                                    val upTo = if (proxy.estimateIsUpperBound) "최대 " else ""
                                    val estimatedCount = proxy.estimatedCount.toLong()
                                    Text(
                                        "삭제 대상: $upTo${formatNumber(estimatedCount)}개 · 사용 가능 ${proxy.balance?.let { "${formatNumber(it)}개" } ?: "확인하지 못함"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = uiColors.textSecondary
                                    )
                                    // 잔고가 없으면 어차피 충전부터 해야 하니 순서 안내는 하지 않는다.
                                    if (commentFirstAdvice != null && remainingCommentCount > 0 && !proxy.activeJob &&
                                        (proxy.balance ?: 0L) > 0) CommentFirstAdvice(
                                        uiColors = uiColors,
                                        commentCount = remainingCommentCount,
                                        mode = commentFirstAdvice,
                                        onAction = if (commentFirstAdvice == CommentFirstAdviceMode.REORDER) onReorderCommentFirst else onSwitchToComments
                                    )
                                    val warning = when {
                                        proxy.activeJob -> "이 계정은 초고속 삭제가 진행 중이에요. 끝난 뒤 시작할 수 있어요."
                                        proxy.balance == null -> "삭제 가능한 개수를 확인하지 못했어요. 충전 탭에서 새로고침해 주세요."
                                        else -> null
                                    }
                                    if (warning != null) Text(warning, color = uiColors.danger, fontWeight = FontWeight.Medium)
                                    val partial = !proxy.activeJob && !needsCharge && proxy.balance != null && proxy.balance < estimatedCount
                                    val quote = proxy.chargeQuote
                                    // 결제 전에 금액을 먼저 보여 준다. 잔고가 없는 건 오류가 아니라 다음 단계다.
                                    if ((needsCharge || partial) && !proxy.activeJob) Surface(
                                        color = uiColors.card,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(
                                                if (needsCharge) "충전하면 바로 시작할 수 있어요"
                                                else "지금은 충전된 ${formatNumber(proxy.balance ?: 0L)}개까지만 삭제하고 멈춰요",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (quote != null) Text(
                                                "${formatNumber(quote.creditedCount)}개 충전 · ${formatWon(quote.finalPrice.toDouble())}",
                                                color = uiColors.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (partial) TextButton(onClick = onCharge, contentPadding = PaddingValues(0.dp)) {
                                                Text("충전하기")
                                            }
                                        }
                                    }
                                    Text(
                                        "지운 개수만큼만 차감돼요",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = uiColors.textSecondary
                                    )
                                }
                                if (!useProxy && proxy.activeJob) Text(
                                    "진행 중인 초고속 삭제가 끝나면 직접 삭제를 시작할 수 있어요", color = uiColors.danger
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = uiColors.outline)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (activeProxy != null && !needsCharge) UltraButton(
                        text = if (activeProxy.starting) "초고속 삭제 요청 중…" else "초고속 삭제 시작",
                        onClick = onConfirm,
                        enabled = !checkingSession && !proxyBlocked,
                        modifier = Modifier.fillMaxWidth()
                    ) else Button(
                        onClick = if (needsCharge) onCharge else onConfirm,
                        enabled = needsCharge || (!checkingSession && !proxyBlocked),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (needsCharge) uiColors.primary else uiColors.danger
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            when {
                                needsCharge -> "삭제 개수 충전하기"
                                checkingSession -> "로그인 상태 확인 중…"
                                else -> "삭제 시작"
                            }
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
                }
            }
        }
    }
}

/** 초고속 삭제를 댓글부터 할 때 줄어드는 시간(최대 배수). */
private const val COMMENT_FIRST_SPEEDUP = 3

/**
 * 글부터 지우려는 사람에게 순서에 따른 속도 차이를 보여 주고 댓글부터 지우도록 권한다.
 * 고르는 건 사용자 몫이라 아래 '초고속 삭제 시작'은 그대로 둔다.
 */
@Composable
private fun CommentFirstAdvice(
    uiColors: UiColors,
    commentCount: Int,
    mode: CommentFirstAdviceMode,
    onAction: () -> Unit
) {
    val ratio = COMMENT_FIRST_SPEEDUP
    Surface(
        color = uiColors.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, uiColors.primary.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("댓글부터 지우면 최대 ${ratio}배 빨라요", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = uiColors.primary)
            Text(
                "초고속 삭제는 댓글 → 글 순서로 지울 때 가장 빨리 끝나요.",
                style = MaterialTheme.typography.bodyMedium
            )
            SpeedBar("댓글 먼저", "가장 빠름", 1f, uiColors.primary, uiColors)
            SpeedBar("글 먼저", "최대 ${ratio}배 느림", 1f / ratio, uiColors.textSecondary, uiColors)
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (mode == CommentFirstAdviceMode.REORDER) "댓글 먼저 지우도록 순서 바꾸기"
                    else "댓글 ${formatNumber(commentCount.toLong())}개부터 지우기",
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (mode == CommentFirstAdviceMode.REORDER) "글은 댓글을 다 지운 뒤 같은 작업에서 이어서 지워요"
                else "댓글 삭제로 바꾼 뒤 갤러리를 골라 시작해요 · 글은 댓글이 끝난 다음에 지우면 돼요",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SpeedBar(label: String, value: String, fraction: Float, color: Color, uiColors: UiColors) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, color = color)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(uiColors.outline.copy(alpha = 0.35f), RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.05f, 1f)).height(8.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}
