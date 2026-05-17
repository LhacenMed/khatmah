package com.lhacenmed.khatmah.feature.today.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
internal fun shimmerBrush(): Brush {
    val color      = MaterialTheme.colorScheme.onSurface
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue  = -300f,
        targetValue   = 1000f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )
    return Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.08f),
            color.copy(alpha = 0.18f),
            color.copy(alpha = 0.08f),
        ),
        start = Offset(offset, 0f),
        end   = Offset(offset + 300f, 0f),
    )
}

/** Single shimmer block with rounded corners. */
@Composable
internal fun SkeletonBox(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(shimmerBrush()))
}