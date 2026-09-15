package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

internal enum class DeleteFilterType(val label: String, val numeric: Boolean = false) {
    RECOMMEND("추천수", true),
    COMMENT_COUNT("댓글수", true),
    VIEW_COUNT("조회수", true),
    POST_TITLE_REGEX("글 제목 정규식"),
    COMMENT_CONTENT_REGEX("댓글 내용 정규식"),
    AGE_DAYS("작성 날짜", true)
}

internal data class DeleteFilterOption(val type: DeleteFilterType, val value: String)

@Composable
internal fun DeleteFilterOptionEditor(
    uiColors: UiColors,
    allowedTypes: List<DeleteFilterType>,
    activeOptions: List<DeleteFilterOption>,
    summary: String? = null,
    onApply: (DeleteFilterType, String) -> Unit,
    onRemove: (DeleteFilterType) -> Unit
) {
    var editingType by remember { mutableStateOf<DeleteFilterType?>(null) }
    var draftValue by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (activeOptions.isEmpty()) {
            Text(
                "추가된 삭제 조건이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            activeOptions.forEach { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingType = option.type
                            draftValue = option.value
                            error = null
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = uiColors.primary.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, uiColors.primary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            option.type.icon(),
                            contentDescription = null,
                            tint = uiColors.primary,
                            modifier = Modifier.padding(end = 10.dp).size(30.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(option.type.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                option.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemove(option.type) }) {
                            Icon(Icons.Default.Close, contentDescription = "조건 삭제")
                        }
                    }
                }
            }
        }

        if (summary != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = uiColors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val availableTypes = allowedTypes.filter { type ->
            activeOptions.none { it.type == type } || editingType == type
        }
        if (editingType == null && availableTypes.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    editingType = availableTypes.first()
                    draftValue = ""
                    error = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("삭제 조건 추가")
            }
        }

        editingType?.let { selectedType ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, uiColors.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Box {
                        OutlinedButton(
                            onClick = { menuExpanded = true },
                            enabled = activeOptions.none { it.type == selectedType },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(selectedType.icon(), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("조건 유형: ${selectedType.label}")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            availableTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label) },
                                    leadingIcon = { Icon(type.icon(), contentDescription = null) },
                                    onClick = {
                                        editingType = type
                                        draftValue = activeOptions.firstOrNull { it.type == type }?.value.orEmpty()
                                        error = null
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftValue,
                        onValueChange = {
                            draftValue = if (selectedType.numeric) it.filter(Char::isDigit) else it
                            error = null
                        },
                        label = { Text(selectedType.inputLabel()) },
                        placeholder = { Text(selectedType.placeholder()) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { message -> { Text(message) } },
                        leadingIcon = {
                            Icon(selectedType.icon(), contentDescription = null, tint = uiColors.primary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = dccleanerOutlinedTextFieldColors(uiColors)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingType = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("취소")
                        }
                        Button(
                            onClick = {
                                if (draftValue.isBlank()) {
                                    error = "값을 입력해 주세요"
                                } else {
                                    onApply(selectedType, draftValue)
                                    editingType = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary)
                        ) {
                            Text("적용")
                        }
                    }
                }
            }
        }
    }
}

private fun DeleteFilterType.icon(): ImageVector = when (this) {
    DeleteFilterType.RECOMMEND -> Icons.Default.ThumbUp
    DeleteFilterType.COMMENT_COUNT -> Icons.Default.Info
    DeleteFilterType.VIEW_COUNT -> Icons.Default.Visibility
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> Icons.Default.Search
    DeleteFilterType.AGE_DAYS -> Icons.Default.DateRange
}

private fun DeleteFilterOption.description(): String = when (type) {
    DeleteFilterType.RECOMMEND -> "${value}개 미만이면 삭제 조건 충족"
    DeleteFilterType.COMMENT_COUNT -> "${value}개 미만이면 삭제 조건 충족"
    DeleteFilterType.VIEW_COUNT -> "${value}회 미만이면 삭제 조건 충족"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX ->
        "정규식과 일치하는 항목만 삭제 · $value"
    DeleteFilterType.AGE_DAYS -> "${value}일 이상 지난 항목만 삭제"
}

private fun DeleteFilterType.inputLabel(): String = when (this) {
    DeleteFilterType.RECOMMEND -> "삭제 기준 추천 수"
    DeleteFilterType.COMMENT_COUNT -> "삭제 기준 댓글 수"
    DeleteFilterType.VIEW_COUNT -> "삭제 기준 조회 수"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> "정규식 패턴"
    DeleteFilterType.AGE_DAYS -> "최소 경과 일수"
}

private fun DeleteFilterType.placeholder(): String = when (this) {
    DeleteFilterType.RECOMMEND, DeleteFilterType.COMMENT_COUNT -> "예: 2"
    DeleteFilterType.VIEW_COUNT -> "예: 100"
    DeleteFilterType.POST_TITLE_REGEX, DeleteFilterType.COMMENT_CONTENT_REGEX -> "예: /^.{0,2}$/i"
    DeleteFilterType.AGE_DAYS -> "예: 5"
}
