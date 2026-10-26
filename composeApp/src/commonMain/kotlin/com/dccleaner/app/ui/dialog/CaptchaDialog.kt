package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.UiColors

@Composable
fun CaptchaDialog(
    uiColors: UiColors,
    onOpenGallog: () -> Unit,
    onResolveCaptcha: () -> Unit,
    onOpenProxyCleaner: () -> Unit,
    totalItemCount: Long
) {
    val displayItemCount = remember { totalItemCount }
    val preferManual = displayItemCount <= 1_000
    var showManual by remember(preferManual) { mutableStateOf(preferManual) }
    // 직접 삭제 속도는 측정값과 상관없이 항상 시간당 1,800개로 본다.
    val hourlyRate = TWO_CAPTCHA_DELETIONS_PER_HOUR
    val estimatedSeconds = (displayItemCount.coerceAtLeast(0) * 3_600L + hourlyRate - 1) / hourlyRate
    val roundedDurationSeconds = if (estimatedSeconds >= 3_600) ((estimatedSeconds + 1_800) / 3_600) * 3_600 else estimatedSeconds
    val captchaCount = (displayItemCount.coerceAtLeast(0) + 199) / 200

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = uiColors.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("캡챠 해결 필요", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = onResolveCaptcha) {
                        Icon(Icons.Default.Close, contentDescription = "닫고 재개")
                    }
                }
                HorizontalDivider(color = uiColors.outline)

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(color = uiColors.card, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = uiColors.warningText)
                                Text("삭제 작업이 일시 중지됐습니다", style = MaterialTheme.typography.bodyMedium, color = uiColors.warningText)
                                if (displayItemCount > 0) {
                                    Text(
                                        "앞으로 약 ${formatApproximateDuration(roundedDurationSeconds)} 동안 캡챠를 약 ${formatNumber(captchaCount)}번 풀어야 해요.",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "남은 글·댓글 ${formatNumber(displayItemCount)}개를 모두 직접 삭제할 때의 예상치입니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = uiColors.textSecondary
                                    )
                                }
                            }
                        }
                        if (showManual) {
                            Surface(color = uiColors.card, shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("직접 해결하기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("PC 버전 갤로그에서 글·댓글 하나를 삭제한 뒤 돌아와 재개하세요.")
                                    Text("모바일 버전 갤로그에서는 해결할 수 없습니다.", color = uiColors.warningText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = uiColors.outline)
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showManual) {
                            Button(onClick = onOpenGallog, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("갤로그 열기")
                            }
                            OutlinedButton(onClick = onResolveCaptcha, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("해결 후 재개")
                            }
                            TextButton(onClick = { showManual = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("돌아가기")
                            }
                        } else {
                            Button(onClick = onOpenProxyCleaner, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("자동 해결 설정하기")
                            }
                            TextButton(onClick = { showManual = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("직접 해결하기")
                            }
                        }
                    }
                }
            }
        }
    }
}
