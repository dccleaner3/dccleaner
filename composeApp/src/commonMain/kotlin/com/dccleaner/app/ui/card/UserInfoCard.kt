package com.dccleaner.app.ui.card

import com.dccleaner.app.model.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UserInfoCard(
    uiColors: UiColors,
    loginInfo: UserInfo,
    profileImage: ImageBitmap? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (profileImage != null) {
            Image(profileImage, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        } else {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = uiColors.textSecondary,
                modifier = Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(loginInfo.nickname, style = MaterialTheme.typography.titleMedium)
            Text("게시글 ${loginInfo.article_num} · 댓글 ${loginInfo.comment_num}",
                style = MaterialTheme.typography.bodySmall, color = uiColors.textSecondary)
        }
    }
}
