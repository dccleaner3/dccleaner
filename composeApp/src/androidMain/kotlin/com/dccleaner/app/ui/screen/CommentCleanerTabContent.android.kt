package com.dccleaner.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dccleaner.app.model.UiColors

@Composable
actual fun CommentCleanerTabContent(
    running: Boolean,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    uiColors: UiColors,
    modifier: Modifier
) {
    val context = LocalContext.current
    CommentCleanerScreen(
        state = rememberCommentMonitorState(remember(context) { androidCommentMonitorStorage(context) }),
        running = running,
        platformNote = "앱을 닫아도 알림을 띄워 두고 백그라운드에서 계속 지켜봐요",
        uiColors = uiColors,
        onStartCommentCleaner = onStartCommentCleaner,
        onStopCommentCleaner = onStopCommentCleaner,
        modifier = modifier
    )
}
