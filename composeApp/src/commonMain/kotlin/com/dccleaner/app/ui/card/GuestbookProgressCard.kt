package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.dialog.GuestbookResultSummary

@Composable
fun GuestbookProgressCard(
    uiColors: UiColors,
    isSending: Boolean,
    progressDone: Int,
    progressTotal: Int,
    successCount: Int,
    failCount: Int,
    onClose: () -> Unit
) {
    val progress = if (progressTotal > 0) {
        progressDone.toFloat() / progressTotal.toFloat()
    } else {
        0f
    }

    TaskProgressCard(
        title = when {
            isSending -> "보내는 중"
            failCount == 0 -> "모두 보냈어요"
            else -> "${successCount}명에게 보냈어요"
        },
        icon = Icons.AutoMirrored.Filled.Send,
        iconTint = uiColors.primary,
        primaryColor = uiColors.primary,
        backgroundColor = uiColors.surfaceVariant,
        cardColor = uiColors.card,
        outlineColor = uiColors.outline,
        logTitle = "상세",
        logs = emptyList(),
        canClose = !isSending,
        onClose = onClose,
        progressContent = {
            Column {
                Text(
                    "$progressDone / ${progressTotal}명",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.primary
                )
                Spacer(Modifier.height(10.dp))
                GuestbookResultSummary(uiColors, successCount, failCount)
            }
        },
        actionContent = {
            Button(
                onClick = onClose,
                enabled = !isSending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("확인", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
