package com.dccleaner.app.ui.cleaner

import androidx.compose.foundation.layout.PaddingValues

import com.dccleaner.app.ui.screen.SheetTopSpacer
import com.dccleaner.app.ui.screen.sheetHeight
import com.dccleaner.app.model.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySelectCard(
    uiColors: UiColors,
    gallList: Map<String, String>,
    deleteType: String,
    onDeleteTypeChange: (String) -> Unit,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit
) {
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),

        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("posting" to "게시글", "comment" to "댓글").forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = deleteType == type,
                        onClick = { onDeleteTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            GallListSelectUI(
                gallList = gallList,
                primaryColor = primaryColor,
                uiColors = uiColors,
                deleteType = deleteType,
                selected = selected,
                onSelectedChange = onSelectedChange
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GallListSelectUI(
    gallList: Map<String, String>,
    primaryColor: Color,
    uiColors: UiColors,
    deleteType: String,
    selected: List<String>,
    onSelectedChange: (List<String>) -> Unit
) {
    var showGallerySheet by remember { mutableStateOf(false) }
    val selectedSet = remember(selected) { selected.toSet() }
    val selectedNames = remember(gallList, selectedSet) {
        gallList.filterKeys { it in selectedSet }.values.toList()
    }

    Column {
        if (gallList.isEmpty()) {
            Text(
                "선택한 타입의 갤러리가 없어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        OutlinedButton(
            onClick = { showGallerySheet = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, uiColors.outline),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    "${selectedNames.size}개 선택 · 전체 ${gallList.size}개",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor
                )
                Text(
                    selectedNames.take(3).joinToString(", ").ifEmpty { "선택된 갤러리가 없어요" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "갤러리 선택 열기",
                tint = primaryColor
            )
        }
    }

    if (showGallerySheet) {
        GallerySelectBottomSheet(
            gallList = gallList,
            selected = selected,
            primaryColor = primaryColor,
            uiColors = uiColors,
            onSelectedChange = onSelectedChange,
            onDismiss = { showGallerySheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GallerySelectBottomSheet(
    gallList: Map<String, String>,
    selected: List<String>,
    primaryColor: Color,
    uiColors: UiColors,
    onSelectedChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    val selectedSet = remember(selected) { selected.toSet() }
    val filteredGalleries = remember(gallList, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) gallList.toList()
        else gallList.filter { (id, name) ->
            name.contains(query, ignoreCase = true) || id.contains(query, ignoreCase = true)
        }.toList()
    }
    val allSelected = gallList.isNotEmpty() && gallList.keys.all { it in selectedSet }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = SheetTopSpacer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sheetHeight()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "갤러리 선택",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${selectedSet.count { it in gallList }}개 선택됨 / 전체 ${gallList.size}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryColor
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("갤러리 이름 또는 ID 검색") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = dccleanerOutlinedTextFieldColors(uiColors)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (searchQuery.isEmpty()) "전체 ${gallList.size}개" else "검색 결과 ${filteredGalleries.size}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onSelectedChange(if (allSelected) emptyList() else gallList.keys.toList())
                    }
                ) {
                    Text(if (allSelected) "전체 선택 해제" else "전체 선택", color = primaryColor)
                }
            }

            HorizontalDivider(color = uiColors.outline)

            if (filteredGalleries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("검색 결과가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredGalleries, key = { it.first }) { (gno, name) ->
                        val isSelected = gno in selectedSet
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = isSelected, role = Role.Checkbox) {
                                    onSelectedChange(
                                        if (it) selected + gno else selected - gno
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    primaryColor.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (isSelected) BorderStroke(1.dp, primaryColor) else null,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("선택 완료 (${selectedSet.count { it in gallList }})")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
