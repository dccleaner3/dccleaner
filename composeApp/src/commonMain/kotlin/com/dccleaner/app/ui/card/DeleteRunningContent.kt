package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.model.deleteLogDisplayText
import com.dccleaner.app.ui.theme.BusyBar

@Composable
fun DeleteRunningContent(
    uiColors: UiColors,
    loginId: String,
    currentGallery: String,
    progress: Float,
    deletedCount: Int,
    totalCount: Int,
    latestLog: String?,
    itemCounts: com.dccleaner.app.model.DeleteItemCounts,
    onStop: () -> Unit
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val solvingCaptcha = latestLog?.contains("2Captcha 캡챠 해결 중") == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),

        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (solvingCaptcha) "캡챠 해결 중" else "삭제 작업 진행 중",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text("${itemCounts.deleted}개 삭제 · ${itemCounts.skipped}개 제외", style = MaterialTheme.typography.titleLarge)
            if (loginId.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("계정: $loginId", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                currentGallery.ifBlank { "갤러리 정보를 불러오는 중입니다" },
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            if (solvingCaptcha) {
                BusyBar(modifier = Modifier.fillMaxWidth(), color = uiColors.primary)
            } else {
                LinearProgressIndicator(
                    progress = { safeProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (solvingCaptcha) latestLog.deleteLogDisplayText().substringAfter("] ")
                    else if (totalCount > 0) "$deletedCount/$totalCount 갤러리" else "수집 중",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!solvingCaptcha) {
                    Text(
                        "${(safeProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = uiColors.primary
                    )
                }
            }
            latestLog?.takeIf { it.isNotBlank() && !solvingCaptcha }?.let { log ->
                Spacer(Modifier.height(16.dp))
                Text(
                    log.deleteLogDisplayText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(shape = RoundedCornerShape(12.dp),
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = uiColors.danger)
            ) {
                Text("삭제 작업 중단")
            }
        }
    }
}
