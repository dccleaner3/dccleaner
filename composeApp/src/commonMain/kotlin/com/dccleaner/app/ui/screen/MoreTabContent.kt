package com.dccleaner.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dccleaner.app.model.UiColors
import com.dccleaner.app.ui.theme.dccleanerSwitchColors

/** 자주 쓰지 않는 기능과 앱 설정을 모은 탭. 댓글 자동 정리·방명록은 여기서 들어간다. */
@Composable
internal fun MoreTabContent(
    uiColors: UiColors,
    commentCleanerStatus: String?,
    guestbookStatus: String?,
    isDarkTheme: Boolean,
    currentVersion: String,
    onOpenCommentCleaner: () -> Unit,
    onOpenGuestbook: () -> Unit,
    rankingStatus: String?,
    onOpenRanking: () -> Unit,
    userPostsStatus: String?,
    onOpenUserPosts: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onOpenManual: () -> Unit,
    onAppInfo: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onLogout: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("기능", uiColors)
        MoreCard(uiColors) {
            MoreRow(uiColors, Icons.Outlined.AutoDelete, uiColors.primary, "댓글 자동 정리",
                "새로 쓴 내 글에 차단할 단어가 든 댓글이 달리면 지워요", badge = commentCleanerStatus, onClick = onOpenCommentCleaner)
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.Outlined.Forum, uiColors.success, "방명록",
                "여러 사람의 방명록에 한 번에 글을 남겨요", badge = guestbookStatus, onClick = onOpenGuestbook)
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.Outlined.Leaderboard, uiColors.primary, "갤창랭킹",
                "갤러리에서 누가 제일 활발했는지 순위를 만들어요", badge = rankingStatus, onClick = onOpenRanking)
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.Outlined.PersonSearch, uiColors.success, "반고닉 글 찾기",
                "식별코드나 IP로 한 사람이 갤러리에 쓴 글을 모아 봐요", badge = userPostsStatus, onClick = onOpenUserPosts)
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("앱 설정", uiColors)
        MoreCard(uiColors) {
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(value = isDarkTheme, role = Role.Switch, onValueChange = onDarkThemeChange)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.Outlined.DarkMode, uiColors.textSecondary)
                Spacer(Modifier.width(14.dp))
                Text("다크 모드", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isDarkTheme, onCheckedChange = null, colors = dccleanerSwitchColors(uiColors))
            }
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.AutoMirrored.Outlined.HelpOutline, uiColors.textSecondary, "도움말", onClick = onOpenManual)
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.Outlined.Info, uiColors.textSecondary, "앱 정보", trailing = currentVersion, onClick = onAppInfo)
            RowDivider(uiColors)
            MoreRow(uiColors, Icons.Outlined.Shield, uiColors.textSecondary, "개인정보처리방침", onClick = onOpenPrivacy)
        }

        Spacer(Modifier.height(12.dp))
        MoreCard(uiColors) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(Icons.AutoMirrored.Outlined.Logout, uiColors.danger)
                Spacer(Modifier.width(14.dp))
                Text("로그아웃", color = uiColors.danger, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String, uiColors: UiColors) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = uiColors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
private fun MoreCard(uiColors: UiColors, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = uiColors.card), shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun RowDivider(uiColors: UiColors) {
    // 아이콘 오른쪽부터 그어 같은 묶음 안의 줄임을 보여 준다.
    HorizontalDivider(Modifier.padding(start = 66.dp, end = 16.dp), color = uiColors.outline)
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MoreRow(
    uiColors: UiColors,
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String? = null,
    badge: String? = null,
    trailing: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // 큰 글씨에서 자리가 모자라면 배지가 아랫줄로 내려간다(배지가 '찾…'으로 잘리거나 제목이 단어 중간에서 끊기지 않게).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (description != null) FontWeight.SemiBold else null)
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = uiColors.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(uiColors.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                color = uiColors.textSecondary)
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyMedium, color = uiColors.textSecondary)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = uiColors.textSecondary)
    }
}
