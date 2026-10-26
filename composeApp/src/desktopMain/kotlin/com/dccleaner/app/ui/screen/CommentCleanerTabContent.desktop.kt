package com.dccleaner.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

@Composable
actual fun CommentCleanerTabContent(
    running: Boolean,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    uiColors: UiColors,
    modifier: Modifier
) {
    CommentCleanerScreen(
        state = rememberCommentMonitorState(remember { desktopCommentMonitorStorage() }),
        running = running,
        platformNote = "PC에서는 이 앱이 켜져 있을 때만 동작해요",
        uiColors = uiColors,
        onStartCommentCleaner = onStartCommentCleaner,
        onStopCommentCleaner = onStopCommentCleaner,
        modifier = modifier
    )
}
