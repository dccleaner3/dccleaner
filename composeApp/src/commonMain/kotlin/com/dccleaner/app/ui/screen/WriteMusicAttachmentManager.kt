package com.dccleaner.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors

internal data class MusicPlatformOption(
    val id: String,
    val name: String
)

@Composable
internal fun WriteMusicAttachmentManager(
    uiColors: UiColors,
    enabled: Boolean,
    platforms: List<MusicPlatformOption>,
    enabledPlatformIds: Set<String>,
    backgroundColorArgb: Int,
    fallbackImageUrl: String,
    musicBridgeInstalled: Boolean,
    musicBridgeReady: Boolean,
    musicBridgeCompatible: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPlatformEnabledChange: (String, Boolean) -> Unit,
    onBackgroundColorChange: (Int) -> Unit,
    onFallbackImageUrlChange: (String) -> Unit,
    onInstallMusicBridge: () -> Unit,
    onOpenMusicBridge: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Headphones,
                    contentDescription = null,
                    tint = uiColors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "현재 재생 중인 곡 첨부하기",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = uiColors.primary
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, uiColors.primary.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "음악 플랫폼 관리",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = uiColors.primary
                    )
                    Text(
                        if (!enabled) {
                            "기능이 꺼져 있습니다"
                        } else if (enabledPlatformIds.isEmpty()) {
                            "활성화된 플랫폼이 없습니다"
                        } else {
                            platforms.filter { it.id in enabledPlatformIds }
                                .joinToString { it.name }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "음악 플랫폼 관리 열기",
                    tint = uiColors.primary
                )
            }
        }
    }

    if (showSheet) {
        MusicPlatformManagerSheet(
            uiColors = uiColors,
            platforms = platforms,
            enabledPlatformIds = enabledPlatformIds,
            backgroundColorArgb = backgroundColorArgb,
            fallbackImageUrl = fallbackImageUrl,
            musicBridgeInstalled = musicBridgeInstalled,
            musicBridgeReady = musicBridgeReady,
            musicBridgeCompatible = musicBridgeCompatible,
            enabled = enabled,
            onEnabledChange = onEnabledChange,
            onPlatformEnabledChange = onPlatformEnabledChange,
            onBackgroundColorChange = onBackgroundColorChange,
            onFallbackImageUrlChange = onFallbackImageUrlChange,
            onInstallMusicBridge = onInstallMusicBridge,
            onOpenMusicBridge = onOpenMusicBridge,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPlatformManagerSheet(
    uiColors: UiColors,
    platforms: List<MusicPlatformOption>,
    enabledPlatformIds: Set<String>,
    backgroundColorArgb: Int,
    fallbackImageUrl: String,
    musicBridgeInstalled: Boolean,
    musicBridgeReady: Boolean,
    musicBridgeCompatible: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onPlatformEnabledChange: (String, Boolean) -> Unit,
    onBackgroundColorChange: (Int) -> Unit,
    onFallbackImageUrlChange: (String) -> Unit,
    onInstallMusicBridge: () -> Unit,
    onOpenMusicBridge: () -> Unit,
    onDismiss: () -> Unit
) {
    val backgroundPresets = remember {
        listOf(
            0xFF0D1117.toInt(),
            0xFF1E3A5F.toInt(),
            0xFF3B2754.toInt(),
            0xFF5A2333.toInt(),
            0xFF164E3B.toInt(),
            0xFFF3E8D3.toInt()
        )
    }
    var backgroundHex by remember(backgroundColorArgb) {
        mutableStateOf((backgroundColorArgb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0'))
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, end = 52.dp, bottom = 8.dp)
                        ) {
                            Text(
                                "음악 플랫폼 관리",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${enabledPlatformIds.size}개 활성화됨 / 전체 ${platforms.size}개",
                                style = MaterialTheme.typography.bodyMedium,
                                color = uiColors.primary
                            )
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (enabled) {
                                    uiColors.primary.copy(alpha = 0.09f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (enabled) BorderStroke(1.dp, uiColors.primary) else null,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEnabledChange(!enabled) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "재생 중인 곡 이미지 첨부",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (enabled) "글 작성 시 음악 이미지를 첨부합니다" else "현재 기능이 꺼져 있습니다",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange,
                                    colors = dccleanerSwitchColors(uiColors)
                                )
                            }
                        }
                    }
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (musicBridgeReady) {
                                uiColors.primary.copy(alpha = 0.09f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        !musicBridgeInstalled -> "음악 연동 앱 설치 필요"
                                        !musicBridgeCompatible -> "음악 연동 앱 업데이트 필요"
                                        musicBridgeReady -> "음악 연동 앱 연결됨"
                                        else -> "연동 앱에서 권한 설정 필요"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    when {
                                        !musicBridgeInstalled ->
                                            "현재 재생 곡은 별도의 선택형 연동 앱에서 가져옵니다."
                                        !musicBridgeCompatible ->
                                            "본앱과 통신할 수 있는 최신 연동 앱을 설치해 주세요."
                                        musicBridgeReady ->
                                            "재생 중인 곡의 제목과 앨범 아트를 가져올 수 있습니다."
                                        else ->
                                            "연동 앱을 열고 알림 접근 권한을 허용해 주세요."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = if (!musicBridgeInstalled || !musicBridgeCompatible) {
                                    onInstallMusicBridge
                                } else {
                                    onOpenMusicBridge
                                }
                            ) {
                                Text(
                                    when {
                                        !musicBridgeInstalled -> "설치"
                                        !musicBridgeCompatible -> "업데이트"
                                        musicBridgeReady -> "열기"
                                        else -> "설정"
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = uiColors.outline)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "이미지 배경색",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            backgroundPresets.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(preset), CircleShape)
                                        .then(
                                            if (preset == backgroundColorArgb) {
                                                Modifier.border(3.dp, uiColors.primary, CircleShape)
                                            } else {
                                                Modifier.border(1.dp, uiColors.outline, CircleShape)
                                            }
                                        )
                                        .clickable { onBackgroundColorChange(preset) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = backgroundHex,
                            onValueChange = { input ->
                                val normalized = input.uppercase()
                                    .filter { it.isDigit() || it in 'A'..'F' }
                                    .take(6)
                                backgroundHex = normalized
                                if (normalized.length == 6) {
                                    normalized.toIntOrNull(16)?.let { rgb ->
                                        onBackgroundColorChange(rgb or 0xFF000000.toInt())
                                    }
                                }
                            },
                            label = { Text("HEX 색상") },
                            prefix = { Text("#") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "재생 중인 곡이 없을 때 첨부할 이미지",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = fallbackImageUrl,
                            onValueChange = onFallbackImageUrlChange,
                            label = { Text("이미지 URL") },
                            placeholder = { Text("https://example.com/image.png") },
                            supportingText = { Text("재생 중인 곡이 없으면 이 이미지를 내려받아 첨부합니다.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = uiColors.outline)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "플랫폼 선택",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                items(platforms, key = { it.id }) { platform ->
                    val checked = platform.id in enabledPlatformIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlatformEnabledChange(platform.id, !checked) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (checked) {
                                uiColors.primary.copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (checked) BorderStroke(1.dp, uiColors.primary) else null,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                platform.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { onPlatformEnabledChange(platform.id, it) },
                                colors = dccleanerSwitchColors(uiColors)
                            )
                        }
                    }
                }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(1.dp, uiColors.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("완료", color = uiColors.primary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
