package com.dccleaner.app.ui.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.RemoteBannerConfig
import com.dccleaner.app.model.UiColors

@Composable
fun RemoteBannerCard(
    banner: RemoteBannerConfig,
    uiColors: UiColors,
    onAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultBackground = when (banner.type) {
        "warning" -> uiColors.warning.copy(alpha = 0.18f)
        "ad" -> uiColors.primary.copy(alpha = 0.12f)
        else -> uiColors.surfaceVariant
    }
    val background = parseHexColor(banner.backgroundColor) ?: defaultBackground
    val contentColor = parseHexColor(banner.textColor) ?: MaterialTheme.colorScheme.onSurface
    var expanded by remember(banner.id) { mutableStateOf(false) }
    var textOverflows by remember(banner.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(1.dp, uiColors.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Campaign,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = banner.text,
                    modifier = Modifier.weight(1f),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (!expanded) textOverflows = result.hasVisualOverflow
                    }
                )
                if (banner.dismissible) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "배너 닫기", tint = contentColor)
                    }
                }
            }
            if (textOverflows || expanded || banner.actionUrl != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (textOverflows || expanded) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(
                                text = if (expanded) "접기" else "펼치기",
                                color = contentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    banner.actionUrl?.let { actionUrl ->
                        TextButton(onClick = { onAction(actionUrl) }) {
                            Text(
                                text = banner.actionText ?: "자세히",
                                color = contentColor,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseHexColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6 && hex.length != 8) return null
    val parsed = hex.toULongOrNull(16) ?: return null
    val argb = if (hex.length == 6) 0xFF000000uL or parsed else parsed
    return Color(argb.toUInt().toInt())
}
