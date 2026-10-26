package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GuestbookConfirmDialog(
    uiColors: UiColors,
    userCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    messagePreview: String = ""
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${userCount}명에게 보낼까요?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (messagePreview.isNotBlank()) {
                    Text(
                        messagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(uiColors.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "보낸 뒤에는 되돌릴 수 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = uiColors.textSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("${userCount}명에게 보내기", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = uiColors.textSecondary)
            }
        },
        containerColor = uiColors.card,
        shape = RoundedCornerShape(16.dp)
    )
}
