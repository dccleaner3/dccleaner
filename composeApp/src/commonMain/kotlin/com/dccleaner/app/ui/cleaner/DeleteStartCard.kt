package com.dccleaner.app.ui.cleaner

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerSwitchColors
import com.dccleaner.app.ui.dialog.ProxyCleanerEntryButton

@Composable
fun DeleteStartCard(
    uiColors: UiColors,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    val cardColor = uiColors.card

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            DeleteStartControls(
                uiColors = uiColors,
                recordGuestbookLog = recordGuestbookLog,
                onRecordGuestbookLogChange = onRecordGuestbookLogChange,
                onShowDeleteDialog = onShowDeleteDialog,
                onOpenProxyCleaner = onOpenProxyCleaner
            )
        }
    }
}

@Composable
fun DeleteStartControls(
    uiColors: UiColors,
    recordGuestbookLog: Boolean,
    onRecordGuestbookLogChange: (Boolean) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "방명록에 가동 기록 남기기",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Switch(
            checked = recordGuestbookLog,
            onCheckedChange = onRecordGuestbookLogChange,
            colors = dccleanerSwitchColors(uiColors)
        )
    }
    Spacer(Modifier.height(16.dp))
    DeleteStartButton(
        primaryColor = uiColors.primary,
        onShowDeleteDialog = onShowDeleteDialog
    )
    Spacer(Modifier.height(10.dp))
    ProxyCleanerEntryButton(
        text = "⚡ 대리 클리너 견적 보기 (유료)",
        uiColors = uiColors,
        onClick = onOpenProxyCleaner
    )
}

@Composable
fun DeleteStartButton(
    primaryColor: androidx.compose.ui.graphics.Color,
    onShowDeleteDialog: () -> Unit
) {
    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        onClick = onShowDeleteDialog,
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
            "삭제 시작",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
