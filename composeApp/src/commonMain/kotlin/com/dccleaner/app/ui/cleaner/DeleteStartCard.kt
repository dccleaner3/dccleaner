package com.dccleaner.app.ui.cleaner

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.proxy.UltraButton
import com.dccleaner.app.ui.screen.SheetSwitchRow

@Composable
fun DeleteStartControls(
    uiColors: UiColors,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    runOnServer: Boolean = false,
    /** 버튼 글자. 함께 지울 때는 "댓글 → 게시글 삭제 시작"처럼 순서를 보여 준다. */
    startLabel: String = "삭제 시작",
    startEnabled: Boolean = true,
    /** 초고속 삭제 버튼 글자. 함께 지울 때는 순서를 붙인다. */
    ultraLabel: String = "초고속 삭제 시작"
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetSwitchRow(
                uiColors = uiColors,
                checked = recordGuestbookLog,
                title = "방명록에 기록 남기기",
                subtitle = "끝나면 방명록에 남겨요",
                onCheckedChange = onRecordGuestbookLogChange
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    if (runOnServer) {
        UltraButton(if (startEnabled) ultraLabel else startLabel, onShowDeleteDialog, Modifier.fillMaxWidth(), enabled = startEnabled)
    } else {
        DeleteStartButton(primaryColor = uiColors.primary, onShowDeleteDialog = onShowDeleteDialog, label = startLabel, enabled = startEnabled)
    }
}

@Composable
fun DeleteStartButton(
    primaryColor: androidx.compose.ui.graphics.Color,
    onShowDeleteDialog: () -> Unit,
    label: String = "삭제 시작",
    enabled: Boolean = true
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        onClick = onShowDeleteDialog,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
