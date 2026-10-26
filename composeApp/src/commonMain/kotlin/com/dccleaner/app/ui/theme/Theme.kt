package com.dccleaner.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
    primaryContainer = DarkUiColors.surfaceVariant,
    onPrimaryContainer = DarkUiColors.primary,
    secondaryContainer = DarkUiColors.surfaceVariant,
    onSecondaryContainer = DarkUiColors.primary,
    tertiaryContainer = DarkUiColors.surfaceVariant,
    onTertiaryContainer = DarkUiColors.primary,
    surfaceContainerLowest = DarkUiColors.background,
    surfaceContainerLow = DarkUiColors.card,
    surfaceContainer = DarkUiColors.card,
    surfaceContainerHigh = DarkUiColors.card,
    surfaceContainerHighest = DarkUiColors.surfaceVariant,
    surfaceTint = DarkUiColors.primary,
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
    primaryContainer = LightUiColors.surfaceVariant,
    onPrimaryContainer = LightUiColors.primary,
    secondaryContainer = LightUiColors.surfaceVariant,
    onSecondaryContainer = LightUiColors.primary,
    tertiaryContainer = LightUiColors.surfaceVariant,
    onTertiaryContainer = LightUiColors.primary,
    surfaceContainerLowest = LightUiColors.background,
    surfaceContainerLow = LightUiColors.card,
    surfaceContainer = LightUiColors.card,
    surfaceContainerHigh = LightUiColors.card,
    surfaceContainerHighest = LightUiColors.surfaceVariant,
    surfaceTint = LightUiColors.primary,
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
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)),
        content = content
    )
}
