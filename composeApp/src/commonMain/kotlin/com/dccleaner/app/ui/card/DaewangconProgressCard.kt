package com.dccleaner.app.ui.card

import com.dccleaner.app.model.UiColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DaewangconProgressCard(
    uiColors: UiColors,
    isRunning: Boolean,
    isCompleted: Boolean,
    errorMessage: String?,
    progress: Float,
    logs: List<String>,
    postCount: Int,
    commentCount: Int,
    onClose: () -> Unit,
    onStop: () -> Unit
) {
    TaskProgressCard(
        title = when {
            isCompleted -> "대왕콘 작업 완료"
            isRunning -> "대왕콘 작업 진행중"
            errorMessage != null -> "대왕콘 작업 실패"
            else -> "대왕콘 작업 중지됨"
        },
        icon = Icons.Default.Star,
        iconTint = Color(0xFFFFD700),
        primaryColor = uiColors.primary,
        backgroundColor = uiColors.surfaceVariant,
        cardColor = uiColors.card,
        outlineColor = uiColors.outline,
        logTitle = "작업 로그",
        logs = logs,
        canClose = !isRunning,
        onClose = onClose,
        progressContent = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "글: ${postCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "댓글: ${commentCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "달성 기준은 디시 서버 설정을 사용합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.primary
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFD700)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = uiColors.primary
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = uiColors.danger
                    )
                }
            }
        },
        actionContent = {
            Button(
                onClick = if (isRunning) onStop else onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) uiColors.warning else uiColors.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRunning) "정지" else "확인", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
