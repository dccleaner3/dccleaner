package com.dccleaner.app.ui.dialog

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ErrorDialog(
    uiColors: UiColors,
    title: String,
    errorMessage: String,
    onDismiss: () -> Unit
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "오류",
                tint = uiColors.danger,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            // 아이콘이 있으면 제목 자리가 가운데라, 두 줄 제목도 가운데로 맞춘다.
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("확인")
            }
        },
        containerColor = cardColor,
        shape = RoundedCornerShape(16.dp)
    )
}
