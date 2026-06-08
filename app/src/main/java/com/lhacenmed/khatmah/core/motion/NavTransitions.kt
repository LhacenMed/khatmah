package com.lhacenmed.khatmah.core.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally

// ── Push: navigate forward ─────────────────────────────────────────────────
// Only the entering page animates; the current page stays still.

/** Entering page slides in from the right and fades in. */
val pushEnter: EnterTransition = materialSharedAxisXIn(
    initialOffsetX = { (it * initialOffset).toInt() },
)

/** Current page is frozen while a new page pushes over it. */
val pushExit: ExitTransition = ExitTransition.None

// ── Pop: navigate backward ─────────────────────────────────────────────────
// Only the exiting page animates; the destination is already at rest.

/** Destination page is already at rest; no enter animation on pop. */
val popEnter: EnterTransition = EnterTransition.None

/**
 * Standard pop-exit for tap-back with no preceding gesture.
 * Exiting page slides to the right and fades out.
 */
val popExit: ExitTransition = buildPopExit(commitShiftDp = 0f, density = 0f)

/**
 * Builds a pop-exit transition whose slide target is adjusted by [commitShiftDp]
 * to eliminate the center-snap glitch after a predictive back gesture.
 *
 * Problem: [slideOutHorizontally]'s [targetOffsetX] is measured from the
 * composable's *layout* origin (x=0), but [PredictiveBackContainer]'s graphicsLayer
 * has already shifted the page visually by [commitShiftDp] dp. When the exit
 * animation starts from layout x=0, the page appears to snap from its shifted
 * visual position back to center before sliding.
 *
 * Fix: subtract [commitShiftDp] (in px) from [targetOffsetX] so the animation
 * starts from the page's actual visual position and slides out as one continuous
 * motion. When [commitShiftDp] is 0f, the result is identical to [popExit].
 */
fun buildPopExit(commitShiftDp: Float, density: Float): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(
            durationMillis = MotionConstants.DefaultMotionDuration,
            easing         = FastOutSlowInEasing,
        ),
        targetOffsetX = { fullWidth ->
            val basePx       = (fullWidth * initialOffset).toInt()
            val commitPx     = (commitShiftDp * density).toInt()
            basePx - commitPx
        },
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = (MotionConstants.DefaultMotionDuration * 0.35f).toInt(),
            easing         = FastOutLinearInEasing,
        ),
    )