package com.dccleaner.app.ui.proxy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.platform.currentTimeMillis
import kotlinx.coroutines.delay

/** 서버 요청은 보통 이 안에 끝난다. 넘기면 막대를 0부터 다시 채운다. */
private const val REQUEST_CYCLE_MILLIS = 5_000
private const val SLOW_REQUEST_MILLIS = 10_000L
private const val FRAME_MILLIS = 16L

/**
 * 시계에 맞춰 직접 채운다. 이 막대는 장식이 아니라 남은 시간을 알려 주는 정보라서,
 * '애니메이션 줄이기'(애니메이션 배율 0)에서 즉시 끝나 버리는 animateTo 대신 snapTo로 갱신한다.
 */
private suspend fun Animatable<Float, *>.fillOver(durationMillis: Long, from: Float = 0f) {
    val start = currentTimeMillis() - (from * durationMillis).toLong()
    while (true) {
        val fraction = (currentTimeMillis() - start).toFloat() / durationMillis
        snapTo(fraction.coerceAtMost(1f))
        if (fraction >= 1f) return
        delay(FRAME_MILLIS)
    }
}

/**
 * 서버 요청 중에만 보이는 얇은 막대. 5초에 맞춰 차오르고, 응답이 없으면 0부터 반복한다.
 * 응답이 오면 끝까지 채운 뒤 사라진다. 자리를 항상 차지해 레이아웃이 흔들리지 않는다.
 */
@Composable
fun RequestProgressBar(active: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val progress = remember { Animatable(0f) }
    val alpha by animateFloatAsState(if (active) 1f else 0f,
        tween(if (active) 150 else 250, delayMillis = if (active) 0 else 200))
    LaunchedEffect(active) {
        if (!active) {
            progress.animateTo(1f, tween(200))
            return@LaunchedEffect
        }
        while (true) progress.fillOver(REQUEST_CYCLE_MILLIS.toLong())
    }
    ProgressTrack({ progress.value }, color,
        modifier.graphicsLayer { this.alpha = alpha }
            .semantics { if (active) progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate })
}

/** 다음 자동 동기화까지 남은 시간. 동기화 요청이 5초를 넘기면 요청 막대처럼 반복한다. */
@Composable
fun SyncProgressBar(waitStartedAt: Long, intervalMillis: Long, syncing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(waitStartedAt, intervalMillis, syncing) {
        if (syncing) {
            progress.snapTo(1f)
            delay(REQUEST_CYCLE_MILLIS.toLong())
            while (true) progress.fillOver(REQUEST_CYCLE_MILLIS.toLong())
        }
        val elapsed = (currentTimeMillis() - waitStartedAt).coerceIn(0L, intervalMillis)
        progress.fillOver(intervalMillis, from = elapsed / intervalMillis.toFloat())
    }
    // 작업 진행 막대보다 눈에 덜 띄게 얇고 옅게 그린다.
    ProgressTrack({ progress.value }, color.copy(alpha = 0.7f), modifier, thickness = 2.dp)
}

@Composable
private fun ProgressTrack(progress: () -> Float, color: Color, modifier: Modifier, thickness: Dp = 4.dp) {
    // 진행률은 그리기 단계에서만 읽어 매 프레임 재구성을 피한다.
    Box(modifier.fillMaxWidth().height(thickness).clip(RoundedCornerShape(thickness / 2))
        .background(color.copy(alpha = 0.15f))
        .drawBehind { drawRect(color, size = size.copy(width = size.width * progress().coerceIn(0f, 1f))) })
}

/** 서버 작업 화면 상단: 중단 요청 중이면 요청 막대, 아니면 다음 업데이트까지의 막대. 화면을 가리지 않는다. */
@Composable
fun ProxySyncHeader(
    uiColors: UiColors,
    cancelling: Boolean,
    syncing: Boolean,
    syncFailed: Boolean,
    waitStartedAt: Long,
    intervalMillis: Long,
    modifier: Modifier = Modifier
) {
    val (label, status, statusColor) = when {
        cancelling -> Triple("중단 요청을 보내고 있어요", "", uiColors.danger)
        syncFailed -> Triple("연결이 불안정해요", "다시 연결하는 중", uiColors.warningText)
        syncing -> Triple("서버와 동기화", "업데이트 중…", uiColors.textSecondary)
        else -> Triple("서버와 동기화", "${intervalMillis / 1_000}초마다 업데이트", uiColors.textSecondary)
    }
    Column(modifier.fillMaxWidth().padding(top = 12.dp).semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
        contentDescription = listOf(label, status).filter(String::isNotBlank).joinToString(", ")
    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = if (cancelling) uiColors.danger else uiColors.textSecondary)
            Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
        }
        if (cancelling) RequestProgressBar(true, color = uiColors.danger)
        else SyncProgressBar(waitStartedAt, intervalMillis, syncing,
            if (syncFailed) uiColors.warningText else ULTRA_COLORS[0])
    }
}

/** 초고속 삭제 시작 요청이 끝나 작업 화면으로 넘어가기 전까지 보여 준다. [stoppingLocal]이면 초고속으로 바꾸려고 직접 삭제를 끝내는 중이다. */
@Composable
fun ProxyStartingCard(uiColors: UiColors, stoppingLocal: Boolean = false) {
    val startedAt = remember { currentTimeMillis() }
    val slow by produceState(false) {
        delay((SLOW_REQUEST_MILLIS - (currentTimeMillis() - startedAt)).coerceAtLeast(0L))
        value = true
    }
    Surface(Modifier.fillMaxSize(), color = uiColors.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                Text(if (stoppingLocal) "직접 삭제를 멈추고 있어요" else "작업을 등록하고 있어요", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (stoppingLocal) "보내던 삭제 요청이 끝나면\n삭제 시작 화면을 열어요.\n초고속 삭제로 다시 시작해 주세요."
                    else if (slow) "응답이 늦어지고 있어요. 요청은 한 번만 접수되니 잠시만 기다려 주세요."
                    else "초고속 삭제를 서버에 맡기는 중이에요.\n보통 5초 안에 끝나요.",
                    style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(Modifier.height(24.dp))
                RequestProgressBar(true, color = ULTRA_COLORS[0])
            }
        }
    }
}
