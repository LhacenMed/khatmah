package com.lhacenmed.khatmah.core.motion

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

fun PathInterpolator.toEasing(): Easing {
    return Easing { f -> this.getInterpolation(f) }
}

private val path = Path().apply {
    moveTo(0f, 0f)
    cubicTo(0.07f, 0f, 0.14f, 0.08f, 0.18f, 0.45f)
    cubicTo(0.22f, 0.75f, 0.28f, 1f, 1f, 1f)
}

private val emphasizePathInterpolator = PathInterpolator(path)
val emphasizeEasing = emphasizePathInterpolator.toEasing()

private val emphasizeEasingVariant = CubicBezierEasing(0.25f, 0f, 0.1f, 1f)
private val emphasizedDecelerate = CubicBezierEasing(0.1f, 0.75f, 0.2f, 1f)
private val emphasizedAccelerate = CubicBezierEasing(0.35f, 0f, 0.9f, 1f)
private val standardDecelerate = CubicBezierEasing(0f, 0f, 0.1f, 1f)
private val motionEasingStandard = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)

private val tweenSpec = tween<Float>(
    durationMillis = DURATION_ENTER,
    easing = motionEasingStandard
)