package com.dccleaner.app.ui.card

import com.dccleaner.app.util.formatDurationMillis
import com.dccleaner.app.model.*
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DaewangconCard(
    uiColors: UiColors,
    onStartDaewangcon: () -> Unit,
    isDaewangconRunning: Boolean
) {
    val postIntervalWait = formatDurationMillis(
        DaewangconDefaults.POST_INTERVAL_DELAY_MILLIS
    )
    val commentIntervalWait = formatDurationMillis(
        DaewangconDefaults.COMMENT_INTERVAL_DELAY_MILLIS
    )
    val postBatchWait = formatDurationMillis(
        DaewangconDefaults.POST_BATCH_DELAY_MILLIS
    )
    val commentBatchWait = formatDurationMillis(
        DaewangconDefaults.COMMENT_BATCH_DELAY_MILLIS
    )
    val commentIntervalDescription =
        if (DaewangconDefaults.COMMENT_INTERVAL_DELAY_MILLIS > 0L) {
            "$commentIntervalWait 간격으로"
        } else {
            "대기 없이"
        }
    val primaryColor = uiColors.primary
    val cardColor = uiColors.card

    Column {
        // 설정 카드
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, uiColors.outline),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "대왕콘 얻기 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "디시 서버에서 오늘 작성량과 달성 기준을 확인해 부족분만 자동 작성합니다\n" +
                        "글은 $postIntervalWait 간격으로, 댓글은 $commentIntervalDescription 작성하며, " +
                        "글 ${DaewangconDefaults.POST_BATCH_SIZE}개마다 $postBatchWait, " +
                        "댓글 ${DaewangconDefaults.COMMENT_BATCH_SIZE}개마다 " +
                        "$commentBatchWait 대기합니다\n" +
                        "소요 시간은 서버 달성 기준과 현재 작성량에 따라 달라집니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

            }
        }

        Spacer(Modifier.height(20.dp))

        // 시작/중지 버튼
        if (!isDaewangconRunning) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = onStartDaewangcon,
                enabled = true,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF191300),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "대왕콘 작업 시작",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF191300)
                )
            }
        } else {
            Text(
                "진행 중인 작업을 전체 화면에서 표시하고 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
