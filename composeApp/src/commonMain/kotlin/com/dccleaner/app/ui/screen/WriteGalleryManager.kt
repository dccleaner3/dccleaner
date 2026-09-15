package com.dccleaner.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerOutlinedTextFieldColors

@Composable
internal fun WriteGalleryManager(
    uiColors: UiColors,
    writeUrls: List<String>,
    selectedWriteUrls: Set<String>,
    onSelectedWriteUrlsChange: (Set<String>) -> Unit,
    onAddUrl: (String) -> String?,
    onDeleteUrls: (Set<String>) -> Unit,
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
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = uiColors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "글쓰기 갤러리 (${selectedWriteUrls.size}/${writeUrls.size})",
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
                        "갤러리 등록 및 관리",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = uiColors.primary
                    )
                    Text(
                        writeUrls.firstOrNull()?.let(::writeGalleryDisplayId)
                            ?: "등록된 갤러리가 없습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "글쓰기 갤러리 관리 열기",
                    tint = uiColors.primary
                )
            }
        }
    }

    if (showSheet) {
        WriteGalleryManagerSheet(
            uiColors = uiColors,
            writeUrls = writeUrls,
            selectedWriteUrls = selectedWriteUrls,
            onSelectedWriteUrlsChange = onSelectedWriteUrlsChange,
            onAddUrl = onAddUrl,
            onDeleteUrls = onDeleteUrls,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteGalleryManagerSheet(
    uiColors: UiColors,
    writeUrls: List<String>,
    selectedWriteUrls: Set<String>,
    onSelectedWriteUrlsChange: (Set<String>) -> Unit,
    onAddUrl: (String) -> String?,
    onDeleteUrls: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var deleteSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    val validDeleteSelection = remember(writeUrls, deleteSelection) {
        deleteSelection.intersect(writeUrls.toSet())
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
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
                        "글쓰기 갤러리 관리",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "글쓰기 대상 ${selectedWriteUrls.size}개 / 전체 ${writeUrls.size}개",
                        style = MaterialTheme.typography.bodyMedium,
                        color = uiColors.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        inputError = null
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("갤러리 링크") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = {
                                urlInput = ""
                                inputError = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "링크 지우기")
                            }
                        }
                    },
                    supportingText = inputError?.let { message ->
                        { Text(message) }
                    },
                    isError = inputError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = dccleanerOutlinedTextFieldColors(uiColors)
                )
                Button(
                    onClick = {
                        val error = onAddUrl(urlInput)
                        inputError = error
                        if (error == null) urlInput = ""
                    },
                    modifier = Modifier.height(56.dp),
                    enabled = urlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = uiColors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("등록")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (validDeleteSelection.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onDeleteUrls(validDeleteSelection)
                            deleteSelection = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("삭제 (${validDeleteSelection.size})")
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = uiColors.outline)
            Spacer(Modifier.height(12.dp))

            if (writeUrls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "위 입력창에 갤러리 또는 write 링크를 등록해 주세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(writeUrls, key = { it }) { url ->
                        val isSelected = url in selectedWriteUrls
                        val isMarkedForDelete = url in validDeleteSelection
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectedWriteUrlsChange(
                                        if (isSelected) selectedWriteUrls - url else selectedWriteUrls + url
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    uiColors.primary.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (isSelected) BorderStroke(1.dp, uiColors.primary) else null,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    writeGalleryDisplayId(url),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        onSelectedWriteUrlsChange(
                                            if (isSelected) selectedWriteUrls - url else selectedWriteUrls + url
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = uiColors.primary)
                                )
                                IconButton(onClick = {
                                    deleteSelection = if (isMarkedForDelete) {
                                        deleteSelection - url
                                    } else {
                                        deleteSelection + url
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "삭제할 갤러리로 선택",
                                        tint = if (isMarkedForDelete) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
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

private fun writeGalleryDisplayId(url: String): String =
    url.substringBefore('?').trimEnd('/').substringAfterLast('/')
