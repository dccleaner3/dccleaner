package com.dccleaner.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dccleaner.app.model.UiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun dccleanerOutlinedTextFieldColors(uiColors: UiColors): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = uiColors.primary,
        focusedLabelColor = uiColors.primary,
        cursorColor = uiColors.primary,
        focusedContainerColor = uiColors.surfaceVariant,
        unfocusedContainerColor = uiColors.surfaceVariant,
        disabledContainerColor = uiColors.surfaceVariant.copy(alpha = 0.56f),
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = uiColors.outline.copy(alpha = 0.56f),
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

@Composable
fun dccleanerSwitchColors(uiColors: UiColors): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = uiColors.primary,
        checkedBorderColor = uiColors.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = uiColors.surfaceVariant,
        uncheckedBorderColor = uiColors.outline,
        disabledCheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
        disabledCheckedTrackColor = uiColors.primary.copy(alpha = 0.32f),
        disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = uiColors.surfaceVariant.copy(alpha = 0.42f),
        disabledUncheckedBorderColor = uiColors.outline.copy(alpha = 0.42f)
    )
