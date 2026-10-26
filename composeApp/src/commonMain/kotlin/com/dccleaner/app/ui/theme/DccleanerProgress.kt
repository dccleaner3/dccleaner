package com.dccleaner.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlin.math.PI
import kotlin.math.cos

/*
 * Android의 '애니메이션 삭제'(동작 줄이기)를 켜면 애니메이션 배율이 0이 되어 Material의 무한 로딩 표시가 첫 장면에서 멈춘다.
 * 로딩 표시는 "아직 일하고 있다"는 뜻이라 멈추면 앱이 멈춘 것처럼 보이므로, 배율 대신 화면 프레임 시각으로 직접 그린다.
 * 장식용 움직임(초고속 삭제 그라데이션, 화면 전환 등)은 그대로 설정을 따른다.
 */

/**
 * 화면 프레임 시각(초). 애니메이션 배율의 영향을 받지 않는다. 그리기 단계에서만 읽어 매 프레임 재구성을 피한다.
 * 무한 애니메이션용 프레임 함수라 UI 테스트는 이 반복을 기다리지 않는다.
 */
@Composable
private fun rememberFrameSeconds(): State<Float> = produceState(0f) {
    val start = withInfiniteAnimationFrameNanos { it }
    while (true) withInfiniteAnimationFrameNanos { value = (it - start) / 1_000_000_000f }
}

/**
 * 무한 로딩 원. Material CircularProgressIndicator(진행률 없음)와 같은 자리에 쓴다(기본 지름 40dp).
 * 진행률 있는 Material 원을 빌리면 화면 읽기 프로그램에 바뀌는 %가 함께 알려져 직접 그린다.
 */
@Composable
fun BusyIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth
) {
    val time = rememberFrameSeconds()
    Canvas(modifier.size(40.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val stroke = strokeWidth.toPx()
        // 호가 늘었다 줄었다 하며 돈다(Material 무한 표시와 비슷한 속도).
        val sweep = 360f * (0.1f + 0.6f * (0.5f - 0.5f * cos(2 * PI * time.value / 1.4).toFloat()))
        drawArc(color, startAngle = time.value * 300f % 360f - 90f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** 무한 진행 막대. Material [androidx.compose.material3.LinearProgressIndicator](진행률 없음)와 같은 자리에 쓴다. */
@Composable
fun BusyBar(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor
) {
    val time = rememberFrameSeconds()
    val height = 4.dp
    val radius = with(LocalDensity.current) { (height / 2).toPx() }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .drawBehind {
                // 막대 폭의 40% 조각이 왼쪽 밖에서 오른쪽 밖으로 1.6초마다 지나간다.
                val segment = size.width * 0.4f
                val x = (time.value / 1.6f % 1f) * (size.width + segment) - segment
                drawRoundRect(color, Offset(x, 0f), Size(segment, size.height), CornerRadius(radius))
            }
    )
}
