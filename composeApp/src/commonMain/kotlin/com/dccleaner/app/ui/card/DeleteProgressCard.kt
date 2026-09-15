package com.dccleaner.app.ui.card

import com.dccleaner.app.model.*
import com.dccleaner.app.util.formatDuration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dccleaner.app.ui.dialog.ProxyCleanerEntryButton

@Composable
fun DeleteProgressCard(
    uiColors: UiColors,
    deleteType: String,
    isCompleted: Boolean,
    isDeleting: Boolean,
    totalPosts: Int,
    deletedPosts: Int,
    currentProgress: Float,
    estimatedTimeLeft: Long,
    nextCaptchaEstimatedTimeLeft: Long,
    isTwoCaptchaConfigured: Boolean,
    currentGallery: String,
    deleteLog: List<String>,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit,
    onOpenProxyCleaner: () -> Unit
) {
    val primaryColor = uiColors.primary
    val backgroundColor = uiColors.surfaceVariant
    val cardColor = uiColors.card

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.8f)
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = uiColors.danger,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isCompleted) "삭제 완료"
                        else if (isDeleting) "${if (deleteType == "posting") "글" else "댓글"} 삭제 진행중"
                        else "${if (deleteType == "posting") "글" else "댓글"} 삭제",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isDeleting) {
                    IconButton(
                        onClick = onClose
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))


            if (totalPosts > 0 || isDeleting) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (totalPosts > 0) "처리 완료: $deletedPosts/$totalPosts 갤러리" else "갤러리 수집중...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${(currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = primaryColor
                    )

                    Spacer(Modifier.height(16.dp))

                    if (!isCompleted) {
                        if (estimatedTimeLeft < 0) {
                            Text(
                                "현재 갤러리 삭제 예상 남은 시간: 알 수 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (estimatedTimeLeft > 0) {
                            Text(
                                "현재 갤러리 삭제 예상 남은 시간: 약 ${formatDuration(estimatedTimeLeft)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "현재 갤러리 삭제 예상 남은 시간: 계산중...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isTwoCaptchaConfigured) {
                            Text(
                                if (nextCaptchaEstimatedTimeLeft > 0) {
                                    "다음 캡챠 발생 예상 시간: 약 ${formatDuration(nextCaptchaEstimatedTimeLeft)} 후"
                                } else if (nextCaptchaEstimatedTimeLeft < 0) {
                                    "다음 캡챠 발생 예상 시간: 곧 표시될 수 있음"
                                } else {
                                    "다음 캡챠 발생 예상 시간: 계산중..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = uiColors.warningText
                            )
                        }
                    }

                    if (currentGallery.isNotEmpty() && !isCompleted) {
                        Text(
                            "현재 처리중: $currentGallery",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isDeleting && totalPosts == 0) {
                Column {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = primaryColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "갤러리 정보를 수집중입니다...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))


            Text(
                "삭제 로그",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true // 최신 로그가 아래에
                ) {
                    items(deleteLog.size) { idx ->
                        val reversedIdx = deleteLog.size - 1 - idx
                        DeleteLogLine(
                            log = deleteLog[reversedIdx],
                            showProgress = isDeleting,
                            separateFromPrevious = reversedIdx > 0 &&
                                deleteLog[reversedIdx].isDeleteGalleryStartLog() &&
                                deleteLog[reversedIdx - 1].isDeleteGalleryCompletionLog()
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))


            if (isCompleted) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("완료", fontWeight = FontWeight.SemiBold)
                }
            } else if (!isDeleting) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("확인", fontWeight = FontWeight.SemiBold)
                }
            } else {
                ProxyCleanerEntryButton(
                    text = "⚡ 대리 클리너 (앱 끄고 2배 빠르게)",
                    uiColors = uiColors,
                    onClick = onOpenProxyCleaner
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = uiColors.warning,
                        contentColor = uiColors.onWarning
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("정지", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
