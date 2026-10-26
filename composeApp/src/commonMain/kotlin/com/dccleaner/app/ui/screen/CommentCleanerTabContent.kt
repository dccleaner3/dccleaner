package com.dccleaner.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

/** 더보기 안의 "댓글 자동 정리" 화면. 저장소와 플랫폼 안내만 플랫폼마다 다르다. */
@Composable
expect fun CommentCleanerTabContent(
    running: Boolean,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    uiColors: UiColors,
    modifier: Modifier = Modifier
)
