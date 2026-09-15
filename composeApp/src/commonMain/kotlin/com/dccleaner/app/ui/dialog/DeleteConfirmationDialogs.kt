package com.dccleaner.app.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dccleaner.app.model.DeletionPreview
import com.dccleaner.app.model.UiColors

@Composable
fun StartDeletionDialog(
    uiColors: UiColors,
    deleteType: String,
    selectedGalleryCount: Int,
    selectedGalleryNames: List<String>,
    preview: DeletionPreview?,
    previewLoading: Boolean,
    previewMessage: String,
    questionPostsMayBeExcluded: Boolean,
    activeFilters: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val targetLabel = if (deleteType == "posting") "게시글" else "댓글"

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = uiColors.card,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                color = uiColors.danger.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = uiColors.danger,
                    modifier = Modifier.padding(12.dp)
                )
            }
        },
        title = {
            Text(
                "삭제 작업을 시작할까요?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("삭제 대상", fontWeight = FontWeight.SemiBold)
                        Text("내 갤로그의 $targetLabel")
                        Text("삭제 예정 갤러리: ${selectedGalleryCount}개")
                        selectedGalleryNames.take(3).forEach { galleryName ->
                            Text(
                                "• $galleryName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedGalleryNames.size > 3) {
                            Text(
                                "외 ${selectedGalleryNames.size - 3}개",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "적용 조건: ${activeFilters.ifEmpty { listOf("추가 필터 없음") }.joinToString(" · ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("필터상 삭제 후보 미리보기", fontWeight = FontWeight.SemiBold)
                        Text(
                            "첫 번째 선택 갤러리의 첫 페이지에서 최대 5개만 표시합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        preview?.items.orEmpty().forEach { item ->
                            Text(
                                "• ${item.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (previewLoading || preview?.items.isNullOrEmpty()) {
                            Text(
                                previewMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = uiColors.primary
                            )
                        }
                        if (questionPostsMayBeExcluded) {
                            Text(
                                "질문글 등 서버에서 추가 확인을 요구하는 글은 실제 삭제 시 제외될 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = uiColors.danger.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "삭제된 글/댓글은 복구할 수 없습니다.",
                        modifier = Modifier.padding(12.dp),
                        color = uiColors.danger,
                        fontWeight = FontWeight.SemiBold
                    )
                }

            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = uiColors.danger),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("삭제 시작", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = uiColors.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("취소", maxLines = 1, softWrap = false)
            }
        }
    )
}
