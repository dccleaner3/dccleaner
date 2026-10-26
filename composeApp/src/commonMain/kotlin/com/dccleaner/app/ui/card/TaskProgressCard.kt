package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

@Composable
fun TaskProgressDialog(content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        content = content
    )
}

@Composable
fun TaskProgressCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    primaryColor: Color,
    backgroundColor: Color,
    cardColor: Color,
    outlineColor: Color = Color.Transparent,
    logTitle: String,
    logs: List<String>,
    canClose: Boolean,
    onClose: () -> Unit,
    progressContent: @Composable () -> Unit,
    actionContent: @Composable () -> Unit
) {
    var showLogs by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (canClose) IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "닫기") }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = cardColor), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) { progressContent() }
                    }
                }
                item {
                    logs.lastOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (logs.isNotEmpty()) TextButton(onClick = { showLogs = !showLogs }) { Text(if (showLogs) "$logTitle 접기" else "$logTitle 보기") }
                }
                if (showLogs) items(logs.size) { index ->
                    Text(logs[logs.lastIndex - index], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actionContent()
        }
    }
}
