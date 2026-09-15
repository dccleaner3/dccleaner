package com.dccleaner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dccleaner.app.model.dccleanerUiColors

private val DarkUiColors = dccleanerUiColors(darkTheme = true)
private val LightUiColors = dccleanerUiColors(darkTheme = false)

private val DarkColorScheme = darkColorScheme(
    primary = DarkUiColors.primary,
    secondary = DarkUiColors.primary,
    tertiary = DarkUiColors.primary,
    background = DarkUiColors.background,
    surface = DarkUiColors.card,
    surfaceVariant = DarkUiColors.surfaceVariant,
    outline = DarkUiColors.outline,
    onPrimary = Color(0xFF0B1118),
    onSecondary = Color(0xFF0B1118),
    onTertiary = Color(0xFF0B1118),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = DarkUiColors.textSecondary,
    error = DarkUiColors.danger,
    onError = DarkUiColors.onDanger,
    errorContainer = Color(0xFF4A1A10),
    onErrorContainer = Color(0xFFFFC9BC)
)

private val LightColorScheme = lightColorScheme(
    primary = LightUiColors.primary,
    secondary = LightUiColors.primary,
    tertiary = LightUiColors.primary,
    background = LightUiColors.background,
    surface = LightUiColors.card,
    surfaceVariant = LightUiColors.surfaceVariant,
    outline = LightUiColors.outline,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = LightUiColors.textSecondary,
    error = LightUiColors.danger,
    onError = LightUiColors.onDanger
)

@Composable
fun DccleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
