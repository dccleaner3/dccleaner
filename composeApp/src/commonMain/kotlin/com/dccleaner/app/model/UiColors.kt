package com.dccleaner.app.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class UiColors(
    val primary: Color,
    val background: Color,
    val card: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val textSecondary: Color,
    val success: Color,
    val warning: Color,
    val warningText: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val headerStart: Color,
    val headerEnd: Color
)

fun dccleanerUiColors(darkTheme: Boolean): UiColors =
    if (darkTheme) {
        UiColors(
            primary = Color(0xFF80B6FF),
            background = Color(0xFF111114),
            card = Color(0xFF1C1C20),
            surfaceVariant = Color(0xFF28282D),
            outline = Color(0xFF38383D),
            textSecondary = Color(0xFFAEB4BC),
            success = Color(0xFF66BB6A),
            warning = Color(0xFFFFB74D),
            warningText = Color(0xFFFFB74D),
            onWarning = Color(0xFF2B1600),
            danger = Color(0xFFFF6B5E),
            onDanger = Color(0xFF2B0A00),
            headerStart = Color(0xFF2C6BCF),
            headerEnd = Color(0xFF1D4E97)
        )
    } else {
        UiColors(
            primary = Color(0xFF0066CC),
            background = Color(0xFFF2F2F7),
            card = Color.White,
            surfaceVariant = Color(0xFFF2F2F7),
            outline = Color(0xFFE1E1E6),
            textSecondary = Color(0xFF676B72),
            success = Color(0xFF2E7D32),
            warning = Color(0xFFFF9800),
            warningText = Color(0xFF9A4D00),
            onWarning = Color(0xFF2B1600),
            danger = Color(0xFFD32F2F),
            onDanger = Color.White,
            headerStart = Color(0xFF0066CC),
            headerEnd = Color(0xFF0F5FAE)
        )
    }
