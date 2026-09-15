package com.dccleaner.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dccleaner.app.model.UiColors

@Composable
expect fun WriteTabContent(
    onRequestCookies: () -> List<String>,
    commentCleanerRunning: Boolean,
    onStartCommentCleaner: (List<String>, Int, Int) -> Unit,
    onStopCommentCleaner: () -> Unit,
    uiColors: UiColors,
    modifier: Modifier = Modifier
)
