package com.dccleaner.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors

/**
 * 바텀시트 본문 높이. 시트 끌기를 끄고 손잡이 대신 [SheetTopSpacer]를 쓰므로 그만큼을 뺀 창 높이의 약 90%다.
 * 시트 끌기를 켜 두면 시트가 화면 꼭대기에 닿지 않은 상태에서 위로 쓸어 올릴 때
 * 안쪽 스크롤과 시트 끌기가 서로 넘겨받으며 위아래로 끝없이 흔들린다(Material3 ModalBottomSheet).
 * 그래서 모든 시트는 sheetGesturesEnabled = false로 열고, X·완료 버튼으로 닫는다.
 */
@Composable
internal fun Modifier.sheetHeight(): Modifier {
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    return height(windowHeight * 0.9f - 20.dp)
}

/** 끌 수 없는 시트이므로 손잡이 대신 같은 높이의 여백만 둔다. */
internal val SheetTopSpacer: @Composable () -> Unit = { Spacer(Modifier.height(20.dp)) }

/** 시트들이 같이 쓰는 틀: 위 고정 영역, 스크롤 본문, 아래 고정 완료 바. */
@Composable
internal fun SheetLayout(
    bottomBar: @Composable () -> Unit,
    fixedTop: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxWidth().sheetHeight().navigationBarsPadding().imePadding()) {
        if (fixedTop != null) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), content = fixedTop)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        bottomBar()
    }
}

@Composable
internal fun SheetHeader(title: String, subtitle: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "닫기") }
    }
}

@Composable
internal fun SheetSectionTitle(
    title: String,
    trailing: String? = null,
    onTrailingClick: () -> Unit = {},
    uiColors: UiColors? = null,
    trailingEnabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) {
            TextButton(onClick = onTrailingClick, enabled = trailingEnabled) {
                Text(trailing, color = if (trailingEnabled) uiColors?.primary ?: Color.Unspecified else Color.Unspecified)
            }
        }
    }
}

@Composable
internal fun SheetErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 8.dp))
}

/** 시트 맨 위의 켜기/끄기 줄. 줄 전체를 눌러도 바뀐다. */
@Composable
internal fun SheetSwitchRow(
    uiColors: UiColors,
    checked: Boolean,
    title: String,
    subtitle: String,
    onCheckedChange: (Boolean) -> Unit,
    subtitleColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = subtitleColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant })
        }
        Switch(checked = checked, onCheckedChange = null, colors = dccleanerSwitchColors(uiColors))
    }
}

/** 시트 아래에 고정된 요약과 완료 버튼. 요약이 없으면 버튼이 가로를 다 쓴다. */
@Composable
internal fun SheetDoneBar(uiColors: UiColors, summary: (@Composable RowScope.() -> Unit)? = null, onDone: () -> Unit) {
    HorizontalDivider(color = uiColors.outline)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (summary != null) Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { summary() }
        Button(
            onClick = onDone, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 52.dp).then(if (summary == null) Modifier.weight(1f) else Modifier),
            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)
        ) {
            Text("완료", modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}
