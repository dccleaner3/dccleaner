package com.dccleaner.app.ui.proxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dccleaner.app.model.CreditPurchase
import com.dccleaner.app.model.PRIVACY_POLICY_URL
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.formatWon
import com.dccleaner.app.ui.dialog.formatNumber
import com.dccleaner.app.ui.theme.BusyIndicator

@Composable
fun ProxyConsentDialog(
    uiColors: UiColors,
    onAgree: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ProxySheetDialog(onDismiss) {
        IconBadge(Icons.Rounded.Shield, uiColors.primary)
        Spacer(Modifier.height(20.dp))
        Text("서버가 자동으로\n삭제를 진행해요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("계정 정보는 이렇게 보호돼요", color = uiColors.textSecondary)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ConsentRow(uiColors, Icons.Rounded.Lock, "AES-256 암호화로 보내요",
                "금융권에서도 쓰는 암호화 방식이에요.")
            ConsentRow(uiColors, Icons.Rounded.Bolt, "사람 손 없이 자동으로 처리돼요",
                "비밀번호는 로그에도 남기지 않아요.")
            ConsentRow(uiColors, Icons.Rounded.DeleteForever, "작업이 끝나면 바로 폐기돼요",
                "완료·실패·취소 모두 똑같아요.")
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("개인정보처리방침에 동의하고 진행해요 · ", style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
            Text(
                "보기",
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onOpenUrl(PRIVACY_POLICY_URL) }
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("동의하고 계속하기", uiColors, onClick = onAgree)
        SecondaryButton("다음에 할게요", uiColors, onDismiss)
    }
}

@Composable
fun ProxyPasswordDialog(
    uiColors: UiColors,
    userId: String,
    title: String = "DC 비밀번호를 입력해 주세요",
    confirmLabel: String = "초고속 삭제 시작",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val submit = { if (password.isNotEmpty()) onConfirm(password) }
    ProxySheetDialog(onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.background(uiColors.surfaceVariant, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Person, null, Modifier.size(16.dp), tint = uiColors.textSecondary)
                Spacer(Modifier.width(6.dp))
                Text(userId, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(" 계정으로 대신 로그인해요", color = uiColors.textSecondary)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("비밀번호") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = uiColors.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text("암호화해서 보내고, 작업이 끝나면 바로 폐기해요",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(confirmLabel, uiColors, enabled = password.isNotEmpty(), onClick = submit)
        SecondaryButton("취소", uiColors, onDismiss)
    }
}

/**
 * 오픈채팅에 보내는 충전 신청서(복사되는 내용). 운영자 폰이 신청 코드로 신청을 찾고, 개수·금액은 운영자가 눈으로 대조한다.
 * 코드가 없으면(이전 서버) null.
 */
internal fun CreditPurchase.applicationText(): String? =
    code?.let { "초고속 삭제 충전 신청 $it\n${formatNumber(creditedCount)}개 · ${formatWon(amountWon.toDouble())}" }

/** 충전 신청 단계. 운영자 폰이 오픈채팅에서 신청서를 확인해야 송금 단계로 넘어간다(확인 전에는 송금 안내를 보여 주지 않는다). */
enum class ChargeStage { SEND_REQUEST, TRANSFER, CHARGED }

/**
 * 충전하기를 누른 뒤 충전될 때까지 열어 두는 창. 신청서 보내기 → 송금 → 충전 완료를 한 단계씩 보여 준다.
 * 신청서는 이미 클립보드에 있고, 송금이 확인되기 전에는 아무것도 결제되지 않는다.
 */
@Composable
fun ChargeRequestDialog(
    uiColors: UiColors,
    amountWon: Int,
    creditedCount: Long,
    code: String?,
    stage: ChargeStage,
    onOpenKakao: () -> Unit,
    onDismiss: () -> Unit
) {
    ProxySheetDialog(onDismiss) {
        ChargeRequestContent(uiColors, amountWon, creditedCount, code, stage, onOpenKakao, onDismiss)
    }
}

@Composable
internal fun ColumnScope.ChargeRequestContent(
    uiColors: UiColors,
    amountWon: Int,
    creditedCount: Long,
    code: String?,
    stage: ChargeStage,
    onOpenKakao: () -> Unit,
    onDismiss: () -> Unit
) {
    val amount = formatWon(amountWon.toDouble())
    val count = "${formatNumber(creditedCount)}개"
    val done = stage == ChargeStage.CHARGED
    val accent by animateColorAsState(if (done) uiColors.success else uiColors.primary, tween(300), label = "chargeAccent")
    // 머리(아이콘·진행 막대)와 버튼은 그대로 두고, 단계별 안내만 바꿔 끼운다. 창 높이는 부드럽게 맞춰진다.
    Crossfade(stage, animationSpec = tween(250), label = "chargeIcon") { s ->
        IconBadge(when (s) {
            ChargeStage.SEND_REQUEST -> Icons.Rounded.ContentPaste
            ChargeStage.TRANSFER -> Icons.Rounded.Payments
            ChargeStage.CHARGED -> Icons.Rounded.Check
        }, if (s == ChargeStage.CHARGED) uiColors.success else uiColors.primary)
    }
    Spacer(Modifier.height(20.dp))
    ChargeProgress(uiColors, stage, accent)
    Spacer(Modifier.height(12.dp))
    AnimatedContent(
        stage,
        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(60)) using SizeTransform(clip = false) },
        label = "chargeStage"
    ) { s ->
        Column {
            Text("3단계 중 ${s.ordinal + 1}단계", style = MaterialTheme.typography.labelLarge,
                color = if (s == ChargeStage.CHARGED) uiColors.success else uiColors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                when (s) {
                    ChargeStage.SEND_REQUEST -> "신청서를 보내 주세요"
                    ChargeStage.TRANSFER -> "${amount}을 송금해 주세요"
                    ChargeStage.CHARGED -> "충전됐어요"
                },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                // 화면 읽기 프로그램이 단계가 바뀐 걸 알린다.
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (s) {
                    ChargeStage.SEND_REQUEST -> "$count 충전 신청서를 복사해 뒀어요.\n카카오톡 오픈채팅에 붙여넣어 보내 주세요."
                    ChargeStage.TRANSFER -> "$count 충전 신청을 확인했어요.\n오픈채팅방 왼쪽 아래 + 버튼을 누르고 송금에서 보내 주세요."
                    ChargeStage.CHARGED -> "${count}를 삭제할 수 있어요."
                },
                style = MaterialTheme.typography.bodyLarge, color = uiColors.textSecondary
            )
            if (s == ChargeStage.SEND_REQUEST && code != null) {
                Spacer(Modifier.height(16.dp))
                ChargeCodeRow(uiColors, code)
            }
            if (s != ChargeStage.CHARGED) {
                Spacer(Modifier.height(20.dp))
                ChargeWaitingRow(uiColors, s)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    if (done) PrimaryButton("확인", uiColors, onClick = onDismiss)
    else {
        PrimaryButton("카카오톡 열기", uiColors, onClick = onOpenKakao)
        SecondaryButton("닫기", uiColors, onDismiss)
        Crossfade(stage, animationSpec = tween(250), label = "chargeNote") { s ->
            Text(
                if (s == ChargeStage.SEND_REQUEST) "닫아도 충전 탭에서 이어서 할 수 있어요" else "신청은 24시간 동안 유효해요",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
            )
        }
    }
}

/** 세 칸 진행 막대. 다음 단계가 무엇인지는 알리지 않고 몇 단계째인지만 보여 준다. 글자(3단계 중 N단계)가 같은 내용을 읽어 준다. */
@Composable
private fun ChargeProgress(uiColors: UiColors, stage: ChargeStage, accent: Color) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val fill by animateColorAsState(if (i <= stage.ordinal) accent else accent.copy(alpha = 0.15f), tween(300), label = "chargeStep")
            Box(Modifier.weight(1f).height(4.dp).background(fill, RoundedCornerShape(2.dp)))
        }
    }
}

/** 이 창이 스스로 바뀐다는 걸 알린다. 한참 그대로면 무엇을 확인할지 알려 준다. */
@Composable
private fun ChargeWaitingRow(uiColors: UiColors, stage: ChargeStage) {
    var slow by remember(stage) { mutableStateOf(false) }
    LaunchedEffect(stage) { delay(CHARGE_SLOW_MILLIS); slow = true }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        BusyIndicator(Modifier.size(14.dp), color = uiColors.textSecondary, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                stage == ChargeStage.SEND_REQUEST && slow -> "확인이 늦어지고 있어요. 신청서를 보냈는지 오픈채팅을 확인해 주세요."
                stage == ChargeStage.SEND_REQUEST -> "신청서를 받으면 이 화면이 바로 바뀌어요"
                slow -> "확인이 늦어지고 있어요. 송금했다면 오픈채팅에 알려 주세요."
                else -> "송금을 받으면 이 화면이 바로 바뀌어요"
            },
            style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary
        )
    }
}

private const val CHARGE_SLOW_MILLIS = 3 * 60_000L

/** 휴대폰 채팅방에 손으로 칠 수 있는 짧은 신청 코드. 신청서를 붙여넣을 수 없을 때(PC에서 신청 등) 이것만 보내면 된다. */
@Composable
private fun ChargeCodeRow(uiColors: UiColors, code: String) {
    Column(
        Modifier.fillMaxWidth().background(uiColors.surfaceVariant, RoundedCornerShape(16.dp)).padding(16.dp)
            // 한 글자씩 읽어 줘야 받아 적을 수 있다.
            .clearAndSetSemantics { contentDescription = "붙여넣기가 안 되면 신청 코드 ${code.toList().joinToString(" ")} 만 보내도 돼요" }
    ) {
        Text("붙여넣기가 안 되면 이 코드만 보내도 돼요", style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 4.sp, color = uiColors.primary)
    }
}

@Composable
private fun ProxySheetDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 420.dp).fillMaxWidth()
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 16.dp),
                content = content)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(56.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(28.dp), tint = tint)
    }
}

@Composable
private fun ConsentRow(uiColors: UiColors, icon: ImageVector, title: String, body: String) {
    Row {
        Box(Modifier.size(36.dp).background(uiColors.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = uiColors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, uiColors: UiColors, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)
    ) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(text: String, uiColors: UiColors, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(text, color = uiColors.textSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ProxyCancelDialog(uiColors: UiColors, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초고속 삭제 작업 중단") },
        text = { Text("서버에서 몇 초 안에 작업을 멈추고, 사용하지 않은 개수는 다시 사용할 수 있어요.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("중단", color = uiColors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("계속 진행") } }
    )
}

/**
 * 직접 삭제 중에 충전되면 초고속 삭제로 바꿀지 묻는다. 바꾸면 직접 삭제를 끝내고 삭제 시작 창을 연다.
 * 충전 완료 창 바로 뒤에 뜨므로 같은 시트 모양을 쓴다.
 * [remainingCount]는 로그인 때 받은 글·댓글 수로 낸 추정치라 모르면(0) 줄을 숨긴다.
 */
@Composable
internal fun ProxyHandOffDialog(uiColors: UiColors, remainingCount: Long, balance: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ProxySheetDialog(onDismiss) {
        IconBadge(Icons.Rounded.Bolt, uiColors.primary)
        Spacer(Modifier.height(20.dp))
        // 동의 창처럼 두 줄로 나눠 큰 글씨에서도 단어 중간에 끊기지 않게 한다.
        Text("초고속 삭제로\n바꿀까요?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text("지금 하던 직접 삭제를 끝내고 삭제 시작 화면에서 초고속 삭제로 다시 시작해요. 이미 지운 글·댓글은 다시 지우지 않아요.",
            style = MaterialTheme.typography.bodyLarge, color = uiColors.textSecondary)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().background(uiColors.surfaceVariant, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (remainingCount > 0) HandOffCountRow("남은 대상", "약 ${formatNumber(remainingCount)}개", uiColors)
            HandOffCountRow("삭제 가능", "${formatNumber(balance)}개", uiColors)
        }
        if (balance < remainingCount) {
            Spacer(Modifier.height(12.dp))
            Text(
                "충전한 개수를 다 쓰면 서버 작업이 멈춰요. 남은 대상은 더 충전하거나 직접 삭제로 지울 수 있어요.",
                style = MaterialTheme.typography.bodyMedium, color = uiColors.warningText,
                modifier = Modifier.fillMaxWidth().background(uiColors.warning.copy(alpha = 0.14f), RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("초고속으로 바꾸기", uiColors, onClick = onConfirm)
        SecondaryButton("직접 삭제 계속", uiColors, onDismiss)
    }
}

@Composable
private fun HandOffCountRow(label: String, value: String, uiColors: UiColors) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = uiColors.textSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
