package com.dccleaner.app.ui.card

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.platform.currentTimeMillis

@Composable
fun InterruptedDeleteTasksCard(
    uiColors: UiColors,
    tasks: List<DeleteTaskProgress>,
    resumeEnabled: Boolean,
    restoringTaskId: String?,
    focusedTaskId: String?,
    onResume: (DeleteTaskProgress) -> Unit,
    onDelete: (DeleteTaskProgress) -> Unit
) {
    if (tasks.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uiColors.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = uiColors.warningText)
                Text(
                    "중단된 삭제 작업",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))

            tasks.sortedWith(
                compareByDescending<DeleteTaskProgress> { it.id == focusedTaskId }
                    .thenByDescending { it.updatedAt }
            ).forEach { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.id == focusedTaskId) {
                            uiColors.warning.copy(alpha = 0.16f)
                        } else uiColors.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "${if (task.deleteType == "posting") "글" else "댓글"} 삭제 · " +
                                    "${task.completedGalleries}/${task.selectedGalleries.size} 갤러리",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${relativeTime(task.updatedAt)} · 누적 ${task.totalDeleted}개 삭제",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.currentGalleryName.isNotBlank()) {
                            Text(task.currentGalleryName, style = MaterialTheme.typography.bodySmall)
                        }
                        if (task.hasPersistedQueue || task.queueSize > 0) {
                            Text(
                                "저장된 삭제 큐 ${(task.queueSize - task.queueCursor).coerceAtLeast(0)}개 남음",
                                style = MaterialTheme.typography.bodySmall,
                                color = uiColors.primary
                            )
                        }
                        if (task.collectionTotalPages > 0) {
                            Text(
                                "수집 페이지 " +
                                        "${(task.collectionTotalPages - task.collectionNextPage).coerceAtLeast(0)}/" +
                                        "${task.collectionTotalPages} 저장됨",
                                style = MaterialTheme.typography.bodySmall,
                                color = uiColors.primary
                            )
                        }
                        Text(
                            taskStatus(task),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.captchaRequired) uiColors.danger else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onResume(task) },
                                enabled = resumeEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (task.id == restoringTaskId) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "설정 복원 중",
                                        modifier = Modifier.padding(start = 8.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, null)
                                    Text(
                                        if (task.state == DeleteTaskState.CAPTCHA_REQUIRED) {
                                            "캡챠 해결/이어하기"
                                        } else {
                                            "이어하기"
                                        },
                                        modifier = Modifier.padding(start = 6.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onDelete(task) },
                                enabled = resumeEnabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("기록 삭제", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun relativeTime(time: Long): String {
    val elapsedMillis = (currentTimeMillis() - time).coerceAtLeast(0L)
    val minutes = elapsedMillis / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "방금 전"
        minutes < 60L -> "${minutes}분 전"
        hours < 24L -> "${hours}시간 전"
        days < 30L -> "${days}일 전"
        else -> "${days / 30L}개월 전"
    }
}

private fun taskStatus(task: DeleteTaskProgress): String = when (task.state) {
    DeleteTaskState.CAPTCHA_REQUIRED -> "캡챠 해결이 필요합니다"
    DeleteTaskState.NETWORK_ERROR -> task.statusMessage.ifBlank { "네트워크 오류로 중단됨" }
    DeleteTaskState.SERVICE_TIMEOUT -> "Android 백그라운드 시간 제한으로 중단됨"
    DeleteTaskState.PAUSED_BY_USER -> "사용자가 중단함"
    DeleteTaskState.INTERRUPTED -> task.statusMessage.ifBlank { "앱 또는 서비스 종료로 중단됨" }
    DeleteTaskState.RUNNING -> "실행 중이거나 비정상 종료 후 확인 대기 중"
}
