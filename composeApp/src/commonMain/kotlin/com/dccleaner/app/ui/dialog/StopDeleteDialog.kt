package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StopDeleteDialog(
    uiColors: UiColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cardColor = uiColors.card

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "경고",
                tint = uiColors.warningText
            )
        },
        title = {
            Text(
                "삭제 정지",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "정말로 정지하겠습니다",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = uiColors.warning,
                    contentColor = uiColors.onWarning
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("정지")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("취소")
            }
        },
        containerColor = cardColor,
        shape = RoundedCornerShape(16.dp)
    )
}
