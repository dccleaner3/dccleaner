package com.dccleaner.app.ui.proxy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal val ULTRA_COLORS = listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFB45309), Color(0xFF7C3AED))
private const val ULTRA_SWEEP = 600f

/** Slowly sweeping violet→pink→amber gradient that marks the server-side "초고속 삭제" mode. */
@Composable
fun rememberUltraBrush(): Brush {
    val offset by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = ULTRA_SWEEP,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Restart)
    )
    return Brush.linearGradient(
        ULTRA_COLORS,
        start = Offset(offset - ULTRA_SWEEP, 0f),
        end = Offset(offset, 0f),
        tileMode = TileMode.Repeated
    )
}

@Composable
fun UltraButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(rememberUltraBrush(), shape),
        shape = shape,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White
        )
    ) {
        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
