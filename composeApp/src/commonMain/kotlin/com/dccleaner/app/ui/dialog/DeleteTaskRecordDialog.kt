package com.dccleaner.app.ui.dialog

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.UiColors

@Composable
fun DeleteTaskRecordDialog(
    uiColors: UiColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    taskCount: Int = 1
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = uiColors.danger
            )
        },
        title = { Text(if (taskCount > 1) "작업 기록 전체 삭제" else "작업 기록 삭제") },
        text = {
            Text(
                (if (taskCount > 1) "이어할 작업 ${taskCount}개를 모두 삭제할까요? " else "이 작업 기록을 삭제할까요? ") +
                    "저장된 삭제 큐와 진행상황이 함께 삭제되어 이어서 진행할 수 없습니다."
            )
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(12.dp),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.danger)
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = uiColors.primary)
            }
        }
    )
}
