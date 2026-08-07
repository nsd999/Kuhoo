package com.kuhoo.ui.canvas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

@Composable
fun ComposableViviMusicCanvas(
    modifier: Modifier = Modifier.fillMaxSize(),
    primaryColor: Color = Color(0xFF6366F1),
    secondaryColor: Color = Color(0xFFEC4899),
    tertiaryColor: Color = Color(0xFF8B5CF6)
) {
    val infiniteTransition = rememberInfiniteTransition()

    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        drawRect(color = Color(0xFF0F172A))

        val x1 = width * (0.5f + 0.3f * sin(anim1))
        val y1 = height * (0.4f + 0.2f * sin(anim2))

        val x2 = width * (0.3f + 0.4f * sin(anim2 + 1f))
        val y2 = height * (0.7f + 0.3f * sin(anim1 + 2f))

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryColor.copy(alpha = 0.6f), Color.Transparent),
                center = Offset(x1, y1),
                radius = (width.coerceAtLeast(height)) * 0.7f
            ),
            radius = (width.coerceAtLeast(height)) * 0.7f,
            center = Offset(x1, y1)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryColor.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(x2, y2),
                radius = (width.coerceAtLeast(height)) * 0.6f
            ),
            radius = (width.coerceAtLeast(height)) * 0.6f,
            center = Offset(x2, y2)
        )
    }
}
