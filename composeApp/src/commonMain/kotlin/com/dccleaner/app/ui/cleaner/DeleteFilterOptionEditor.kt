package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.screen.SheetDoneBar
import com.dccleaner.app.ui.screen.SheetHeader
import com.dccleaner.app.ui.screen.SheetLayout
import com.dccleaner.app.ui.screen.SheetTopSpacer
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

internal enum class DeleteFilterType(val label: String, val numeric: Boolean = false) {
    RECOMMEND("추천수", true),
    COMMENT_COUNT("댓글수", true),
    VIEW_COUNT("조회수", true),
    POST_TITLE_REGEX("제목 정규식"),
    COMMENT_CONTENT_REGEX("댓글 정규식"),
    AGE_DAYS("작성일", true)
}

/** 추천·댓글·조회 수 기준은 이 수 이상인 글을 남긴다. 0이면 모든 글이 남으니 1부터 받는다. */
internal val DeleteFilterType.keepsAtLeast: Boolean
    get() = this == DeleteFilterType.RECOMMEND || this == DeleteFilterType.COMMENT_COUNT || this == DeleteFilterType.VIEW_COUNT

internal data class DeleteFilterOption(val type: DeleteFilterType, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteFilterOptionEditor(
    uiColors: UiColors,
    allowedTypes: List<DeleteFilterType>,
    activeOptions: List<DeleteFilterOption>,
    summary: String? = null,
    onApply: (DeleteFilterType, String) -> Unit,
    onRemove: (DeleteFilterType) -> Unit
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingExisting by rememberSaveable { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf<DeleteFilterType?>(null) }
    var draftValue by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val availableTypes = allowedTypes.filter { type -> activeOptions.none { it.type == type } }

    Column {
        if (activeOptions.isEmpty()) {
            Text(
                "조건 없이 모두 지워요",
                style = MaterialTheme.typography.bodyMedium,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            activeOptions.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable {
                            editingExisting = true
                            selectedType = option.type
                            draftValue = option.value
                            error = null
                            sheetOpen = true
                        }
                        .heightIn(min = 56.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TypeIcon(option.type, uiColors.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(option.type.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            option.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = uiColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(option.type) }) {
                        Icon(Icons.Default.Close, contentDescription = "조건 삭제", tint = uiColors.textSecondary)
                    }
                }
            }
        }

        if (availableTypes.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable {
                        editingExisting = false
                        selectedType = availableTypes.first()
                        draftValue = ""
                        error = null
                        sheetOpen = true
                    }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp).background(uiColors.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = uiColors.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text("조건 추가", color = uiColors.primary, fontWeight = FontWeight.SemiBold)
            }
        }

        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                    .background(uiColors.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )
        }
    }

    val type = selectedType
    if (sheetOpen && type != null) {
        val close = { sheetOpen = false }
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
        ModalBottomSheet(
            onDismissRequest = close, sheetState = sheetState,
            sheetGesturesEnabled = false, dragHandle = SheetTopSpacer
        ) {
            SheetLayout(
                fixedTop = {
                    SheetHeader(
                        if (editingExisting) "조건 수정" else "조건 추가",
                        if (editingExisting) "값을 바꾸면 바로 반영돼요" else "어떤 것만 지울지 골라 주세요",
                        onClose = close
                    )
                },
                bottomBar = {
                    SheetDoneBar(uiColors) {
                        if (draftValue.isBlank()) {
                            error = "값을 입력해 주세요"
                        } else if (type.keepsAtLeast && draftValue.trimStart('0').isEmpty()) {
                            // 0개 이상 남기기는 모든 글을 남겨 아무것도 지우지 않는다.
                            error = "1 이상으로 입력해 주세요"
                        } else {
                            onApply(type, draftValue)
                            sheetOpen = false
                        }
                    }
                }
            ) {
                Spacer(Modifier.height(8.dp))
                (if (editingExisting) listOf(type) else availableTypes).forEach { option ->
                    val selected = option == type
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) uiColors.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .selectable(selected = selected, enabled = !editingExisting, role = Role.RadioButton) {
                                if (option != type) {
                                    selectedType = option
                                    draftValue = ""
                                    error = null
                                }
                            }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TypeIcon(option, uiColors.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else null)
                        Spacer(Modifier.weight(1f))
                        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = uiColors.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = {
                        draftValue = if (type.numeric) it.filter(Char::isDigit) else it
                        error = null
                    },
                    label = { Text(type.inputLabel()) },
                    placeholder = { Text(type.placeholder()) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: type.inputHint()) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
            }
        }
    }
}

@Composable
private fun TypeIcon(type: DeleteFilterType, tint: Color) {
    Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(type.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun DeleteFilterType.icon(): ImageVector = when (this) {
    DeleteFilterType.RECOMMEND -> Icons.Default.ThumbUp
    DeleteFilterType.COMMENT_COUNT -> Icons.AutoMirrored.Filled.Chat
    DeleteFilterType.VIEW_COUNT -> Icons.Default.Visibility
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> Icons.Default.Search
    DeleteFilterType.AGE_DAYS -> Icons.Default.DateRange
}

private fun DeleteFilterOption.description(): String = when (type) {
    DeleteFilterType.RECOMMEND -> "추천 ${value}개 미만인 글만 지워요"
    DeleteFilterType.COMMENT_COUNT -> "댓글 ${value}개 미만인 글만 지워요"
    DeleteFilterType.VIEW_COUNT -> "조회 ${value}회 미만인 글만 지워요"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> "‘$value’와 일치하는 것만 지워요"
    DeleteFilterType.AGE_DAYS -> "${value}일 지난 것만 지워요"
}

private fun DeleteFilterType.inputLabel(): String = when (this) {
    DeleteFilterType.RECOMMEND -> "추천 수"
    DeleteFilterType.COMMENT_COUNT -> "댓글 수"
    DeleteFilterType.VIEW_COUNT -> "조회 수"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> "정규식"
    DeleteFilterType.AGE_DAYS -> "지난 일수"
}

private fun DeleteFilterType.inputHint(): String = when (this) {
    DeleteFilterType.RECOMMEND -> "이 숫자보다 추천이 적은 글만 지워요"
    DeleteFilterType.COMMENT_COUNT -> "이 숫자보다 댓글이 적은 글만 지워요"
    DeleteFilterType.VIEW_COUNT -> "이 숫자보다 조회가 적은 글만 지워요"
    DeleteFilterType.POST_TITLE_REGEX -> "제목이 정규식과 일치하는 글만 지워요"
    DeleteFilterType.COMMENT_CONTENT_REGEX -> "내용이 정규식과 일치하는 댓글만 지워요"
    DeleteFilterType.AGE_DAYS -> "쓴 지 이만큼 지난 것만 지워요"
}

private fun DeleteFilterType.placeholder(): String = when (this) {
    DeleteFilterType.RECOMMEND, DeleteFilterType.COMMENT_COUNT -> "예: 2"
    DeleteFilterType.VIEW_COUNT -> "예: 100"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> "예: /^.{0,2}$/i"
    DeleteFilterType.AGE_DAYS -> "예: 5"
}
