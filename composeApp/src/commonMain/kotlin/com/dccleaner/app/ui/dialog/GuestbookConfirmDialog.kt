package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GuestbookConfirmDialog(
    uiColors: UiColors,
    userCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "방명록 전송",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "총 ${userCount}명에게 방명록을 전송합니다.\n정말 전송하시겠습니까?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("전송")
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
