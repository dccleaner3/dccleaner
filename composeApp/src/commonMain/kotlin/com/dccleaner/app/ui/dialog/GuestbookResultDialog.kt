package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun GuestbookResultDialog(
    uiColors: UiColors,
    successCount: Int,
    failCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (failCount == 0) "모두 보냈어요" else "${successCount}명에게 보냈어요",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = { GuestbookResultSummary(uiColors, successCount, failCount) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("확인", fontWeight = FontWeight.SemiBold)
            }
        },
        containerColor = uiColors.card,
        shape = RoundedCornerShape(16.dp)
    )
}

/** "성공 N명 · 실패 N명" — 실패가 있으면 실패 부분만 빨간색. */
@Composable
fun GuestbookResultSummary(uiColors: UiColors, successCount: Int, failCount: Int) {
    Text(
        buildAnnotatedString {
            append("성공 ${successCount}명 · ")
            if (failCount > 0) withStyle(SpanStyle(color = uiColors.danger)) { append("실패 ${failCount}명") }
            else append("실패 0명")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = uiColors.textSecondary
    )
}
