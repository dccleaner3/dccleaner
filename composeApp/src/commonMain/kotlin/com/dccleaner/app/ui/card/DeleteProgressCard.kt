package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.*
import com.dccleaner.app.util.formatDuration
import com.dccleaner.app.ui.dialog.ProxyCleanerEntryButton
import com.dccleaner.app.ui.proxy.rememberUltraBrush
import androidx.compose.foundation.background
import com.dccleaner.app.ui.theme.BusyBar

@Composable
fun DeleteProgressCard(
    uiColors: UiColors,
    deleteType: String,
    isCompleted: Boolean,
    isDeleting: Boolean,
    totalPosts: Int,
    deletedPosts: Int,
    currentProgress: Float,
    estimatedTimeLeft: Long,
    currentDeletionSpeedPerHour: Int,
    nextCaptchaEstimatedTimeLeft: Long,
    isTwoCaptchaConfigured: Boolean,
    deleteLog: List<String>,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit,
    onOpenProxyCleaner: () -> Unit,
    currentGallery: String = "",
    proxyMode: Boolean = false,
    proxyTitle: String? = null,
    cumulativeSpeedPerHour: Int = 0,
    resultSummary: List<String> = emptyList(),
    /** 함께 지울 때 단계 안내. 없으면 "게시글 정리"/"댓글 정리". */
    subtitle: String? = null,
    /** Pinned above the scrolling content, e.g. the server sync bar. */
    header: (@Composable () -> Unit)? = null,
    /** 초고속 삭제의 (삭제, 제외) 누적 개수. 삭제 개수가 곧 차감 개수라 큰 숫자로 보여 준다. */
    counts: Pair<Int, Int>? = null,
    stopLabel: String = "작업 중단",
    stopEnabled: Boolean = true,
    /** false면 중단 버튼을 숨긴다(읽기 전용 보기). */
    showStop: Boolean = true,
    /** 끝난 화면의 버튼 문구. 실패·취소는 '완료'가 어색하므로 바꿔 쓴다. */
    completeLabel: String = "완료",
    /** 작업 전체 남은 시간. 알 수 없으면 null. */
    overallTimeLeft: Long? = null
) {
    var showLogs by remember { mutableStateOf(false) }
    val completedGalleries = remember(deleteLog) { deleteLog.filter { it.isDeleteGalleryCompletionLog() } }
    val solvingCaptcha = deleteLog.lastOrNull()?.contains("2Captcha 캡챠 해결 중") == true
    Surface(modifier = Modifier.fillMaxSize(), color = uiColors.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            header?.invoke()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 24.dp)) {
                item {
                    Text(
                        proxyTitle ?: if (isCompleted) "삭제 완료" else if (solvingCaptcha) "캡챠 해결 중" else if (isDeleting) "삭제 중" else "삭제 작업",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(subtitle ?: if (deleteType == "posting") "게시글 정리" else "댓글 정리", color = uiColors.textSecondary)
                    // 직접 삭제는 캡챠를 누가 푸는지가 속도를 가른다. 초고속 띠와 같은 자리에 두고, 2Captcha 키를 넣으면 바로 바뀐다.
                    if (!proxyMode && isDeleting) {
                        Spacer(Modifier.height(10.dp))
                        val accent = if (isTwoCaptchaConfigured) uiColors.success else uiColors.textSecondary
                        Text(
                            if (isTwoCaptchaConfigured) "직접 삭제 · 캡챠는 2Captcha가 자동으로 풀어요" else "직접 삭제 · 캡챠가 나오면 직접 풀어야 해요",
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (proxyMode && isDeleting) {
                        Spacer(Modifier.height(10.dp))
                        // 상태는 제목이 말하므로 배너는 모드와 '앱을 꺼도 된다'만 짧게 알린다.
                        Text(
                            "⚡ 앱을 꺼도 서버가 계속 지워요",
                            modifier = Modifier
                                .background(rememberUltraBrush(), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (resultSummary.isNotEmpty()) item {
                    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            resultSummary.forEach { Text(it) }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 함께 지울 때 이 숫자는 지금 단계만의 진행이다.
                            Text(if (subtitle != null && !isCompleted) "이번 단계 진행" else "전체 작업 진행", color = uiColors.textSecondary)
                            Text("${(currentProgress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                            if (totalPosts > 0) {
                                if (!solvingCaptcha) Text("$deletedPosts / $totalPosts 갤러리 완료", style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
                                if (solvingCaptcha) BusyBar(modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(progress = { currentProgress }, modifier = Modifier.fillMaxWidth())
                            }
                            counts?.let { (deleted, skipped) ->
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    ProgressStat("삭제", deleted, Modifier.weight(1f))
                                    ProgressStat("제외", skipped, Modifier.weight(1f), uiColors.textSecondary)
                                }
                            }
                            if (currentGallery.isNotBlank() && !isCompleted) Text(currentGallery, style = MaterialTheme.typography.titleMedium)
                            // 현재 갤러리 진행 줄(또는 현재 상태)을 갤러리 이름 바로 아래에 붙인다.
                            if (!isCompleted) deleteLog.lastOrNull()?.let { DeleteLogLine(it, showProgress = isDeleting) }
                            if (!isCompleted) {
                                // 남은 시간은 평균 속도로 계산돼 온다. 초고속 삭제는 속도처럼 현재 속도 기준을 값으로, 평균 기준을 제목 옆에 둔다.
                                val recentTimeLeft = if (proxyMode && estimatedTimeLeft > 0 && cumulativeSpeedPerHour > 0 &&
                                    currentDeletionSpeedPerHour > 0 && currentDeletionSpeedPerHour != cumulativeSpeedPerHour) {
                                    (estimatedTimeLeft * cumulativeSpeedPerHour + currentDeletionSpeedPerHour - 1) / currentDeletionSpeedPerHour
                                } else estimatedTimeLeft
                                // 전체 남은 시간을 알면 그것만, 모르면 현재 갤러리 남은 시간을 보여 준다.
                                if (overallTimeLeft != null) ProgressMetricRow("전체 남은 시간", "약 ${formatDuration(overallTimeLeft)}")
                                else ProgressMetricRow(
                                    if (recentTimeLeft != estimatedTimeLeft) "현재 갤러리 남은 시간 · 평균 속도면 약 ${formatDuration(estimatedTimeLeft)}"
                                    else "현재 갤러리 남은 시간",
                                    when {
                                        estimatedTimeLeft < 0 -> "확인 불가"
                                        recentTimeLeft > 0 -> "약 ${formatDuration(recentTimeLeft)}"
                                        else -> "계산 중"
                                    }
                                )
                                // 초고속 삭제는 속도가 오르내려 최근 속도와 평균을 함께, 일반 삭제는 전체 속도 하나만 보여 준다.
                                ProgressMetricRow(
                                    if (proxyMode && cumulativeSpeedPerHour > 0) "삭제 속도 · 평균 ${formatCompactNumber(cumulativeSpeedPerHour)}개/시간"
                                    else "삭제 속도",
                                    if (currentDeletionSpeedPerHour <= 0) "계산 중" else "시간당 약 ${formatCompactNumber(currentDeletionSpeedPerHour)}개"
                                )
                                if (!isTwoCaptchaConfigured && !proxyMode) ProgressMetricRow("다음 캡챠", when {
                                    nextCaptchaEstimatedTimeLeft > 0 -> "약 ${formatDuration(nextCaptchaEstimatedTimeLeft)} 후"
                                    nextCaptchaEstimatedTimeLeft < 0 -> "곧 발생 가능"
                                    else -> "계산 중"
                                }, uiColors.warningText)
                            }
                            // 진행 중에는 위(갤러리 이름 아래)에서 보여 준다. 초고속 삭제는 끝나면 제목·요약과 겹치므로 숨긴다.
                            deleteLog.lastOrNull()?.takeIf { isCompleted && !proxyMode }
                                ?.let { DeleteLogLine(it, showProgress = isDeleting) }
                        }
                    }
                }
                if (isCompleted && completedGalleries.isNotEmpty()) {
                    item { Text("최근 갤러리 결과 · 삭제 / 제외", style = MaterialTheme.typography.titleMedium) }
                    items(completedGalleries.size) { index -> DeleteLogLine(completedGalleries[index], showProgress = false) }
                }
                // 서버는 상세 로그를 보내지 않으므로 초고속 삭제에서는 빈 로그 버튼을 두지 않는다.
                if (!proxyMode) item {
                    TextButton(onClick = { showLogs = !showLogs }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showLogs) "상세 로그 접기" else "상세 로그 보기")
                    }
                }
                if (showLogs) {
                    items(deleteLog.size) { index ->
                        DeleteLogLine(deleteLog[deleteLog.lastIndex - index], showProgress = isDeleting)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDeleting) {
                    if (showStop) OutlinedButton(onClick = onStop, enabled = stopEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(stopLabel, color = if (stopEnabled) uiColors.danger else uiColors.textSecondary)
                    }
                    if (proxyMode) {
                        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text("닫기 · 서버에서 계속 진행")
                        }
                    } else {
                        ProxyCleanerEntryButton(uiColors, onOpenProxyCleaner)
                    }
                } else {
                    Button(shape = RoundedCornerShape(12.dp), onClick = if (isCompleted) onComplete else onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (isCompleted) completeLabel else "닫기")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressStat(label: String, value: Int, modifier: Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatCompactNumber(value)}개", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ProgressMetricRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

private fun formatCompactNumber(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
