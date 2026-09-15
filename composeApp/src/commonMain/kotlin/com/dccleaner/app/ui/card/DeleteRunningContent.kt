package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

@Composable
fun DeleteRunningContent(
    uiColors: UiColors,
    loginId: String,
    currentGallery: String,
    progress: Float,
    deletedCount: Int,
    totalCount: Int,
    latestLog: String?,
    onStop: () -> Unit
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "삭제 작업 진행 중",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
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
            LinearProgressIndicator(
                progress = { safeProgress },
                modifier = Modifier.fillMaxWidth(),
                color = uiColors.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (totalCount > 0) "$deletedCount/$totalCount 갤러리" else "수집 중",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(safeProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.primary
                )
            }
            latestLog?.takeIf { it.isNotBlank() }?.let { log ->
                Spacer(Modifier.height(16.dp))
                Text(
                    log,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = uiColors.warning,
                    contentColor = uiColors.onWarning
                )
            ) {
                Text("삭제 작업 중단")
            }
        }
    }
}
