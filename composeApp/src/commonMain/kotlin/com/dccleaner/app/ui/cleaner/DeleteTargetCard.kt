package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.screen.SheetDoneBar
import com.dccleaner.app.ui.screen.SheetHeader
import com.dccleaner.app.ui.screen.SheetLayout
import com.dccleaner.app.ui.screen.SheetSectionTitle
import com.dccleaner.app.ui.screen.SheetTopSpacer

internal fun deleteTypeLabel(type: String) = if (type == "posting") "게시글" else "댓글"

/** 지울 대상 한 줄의 요약: 고른 갤러리 수와 적용 조건. */
data class DeleteTargetSummary(val selectedCount: Int, val totalCount: Int, val conditions: List<String>)

/**
 * 무엇을 지울지: 게시글·댓글 두 줄. 줄을 누르면 켜고 끄고, 켠 줄의 [설정 ›]을 누르면 그 종류의 갤러리·조건 시트가 열린다.
 * 둘 다 고르면 아래에 순서 한 줄이 생긴다. 처음에는 아무것도 안 골라져 있다.
 */
@Composable
fun DeleteTargetCard(
    uiColors: UiColors,
    checked: (String) -> Boolean,
    summaryOf: (String) -> DeleteTargetSummary,
    onCheckedChange: (type: String, checked: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    commentFirst: Boolean,
    onSwapOrder: () -> Unit,
    modifier: Modifier = Modifier,
    /** 초고속 삭제를 글부터 하려 하면 댓글 먼저가 더 빠르다고 알린다. */
    adviseCommentFirst: Boolean = false
) {
    val both = checked("posting") && checked("comment")
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            listOf("posting", "comment").forEachIndexed { index, type ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = uiColors.outline)
                TargetRow(
                    uiColors = uiColors,
                    label = deleteTypeLabel(type),
                    checked = checked(type),
                    summary = summaryOf(type),
                    onCheckedChange = { onCheckedChange(type, it) },
                    onOpen = { onOpen(type) }
                )
            }
            if (both) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = uiColors.outline)
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (commentFirst) "댓글부터 지우고 이어서 게시글을 지워요" else "게시글부터 지우고 이어서 댓글을 지워요",
                        style = MaterialTheme.typography.bodySmall,
                        color = uiColors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onSwapOrder) {
                        Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("순서 바꾸기")
                    }
                }
                if (adviseCommentFirst) Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .background(uiColors.primary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("초고속 삭제는 댓글부터 지우면 최대 3배 빨라요", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = uiColors.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSwapOrder) { Text("댓글 먼저로") }
                }
            }
        }
    }
}

@Composable
private fun TargetRow(
    uiColors: UiColors,
    label: String,
    checked: Boolean,
    summary: DeleteTargetSummary,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    // 지울 갤러리가 하나도 없는 종류는 고를 수 없다(골라도 시작할 때 막힌다). 이미 골라 둔 건 끌 수 있게 둔다.
    val available = summary.totalCount > 0 || checked
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (available) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽(체크·이름·요약)은 누를 때마다 켜고 끄기만 한다. 설정은 오른쪽 버튼으로 따로 연다.
        Row(
            Modifier
                .weight(1f)
                .toggleable(value = checked, enabled = available, role = Role.Checkbox, onValueChange = onCheckedChange)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = available,
                colors = CheckboxDefaults.colors(checkedColor = uiColors.primary),
                modifier = Modifier.padding(12.dp)
            )
            Column(Modifier.padding(start = 4.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        checked -> summaryText(summary)
                        !available -> "지울 ${label}이 없어요"
                        else -> "지우지 않아요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = uiColors.textSecondary,
                    // 큰 글씨에서도 조건이 잘리지 않게 두 줄까지 보여 준다.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 켠 종류만 설정할 수 있다. 구분선으로 켜고 끄는 영역과 떼어 잘못 누르지 않게 한다.
        if (checked) {
            Box(Modifier.width(1.dp).height(32.dp).background(uiColors.outline))
            TextButton(
                onClick = onOpen,
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp)
                    .semantics { contentDescription = "$label 설정 열기" }
            ) {
                Text("설정")
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun summaryText(summary: DeleteTargetSummary): String {
    val galleries = when {
        summary.totalCount == 0 -> "갤러리 없음"
        summary.selectedCount == summary.totalCount -> "갤러리 전체 ${summary.totalCount}개"
        else -> "갤러리 ${summary.selectedCount}개"
    }
    val conditions = summary.conditions.firstOrNull()?.let {
        it + if (summary.conditions.size > 1) " 외 ${summary.conditions.size - 1}개" else ""
    } ?: "조건 없음"
    return "$galleries · $conditions"
}

/** 한 종류의 갤러리와 조건을 고치는 시트. 내용은 기존 갤러리 선택·조건 카드를 그대로 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteTargetSheet(
    uiColors: UiColors,
    type: String,
    onDismiss: () -> Unit,
    galleries: @Composable () -> Unit,
    conditions: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, sheetGesturesEnabled = false, dragHandle = SheetTopSpacer) {
        SheetLayout(
            fixedTop = { SheetHeader("${deleteTypeLabel(type)} 삭제 설정", "어느 갤러리에서, 어떤 조건으로 지울지 정해요", onClose = onDismiss) },
            bottomBar = { SheetDoneBar(uiColors, onDone = onDismiss) }
        ) {
            SheetSectionTitle("갤러리")
            galleries()
            SheetSectionTitle("조건")
            conditions()
        }
    }
}
