package com.lhacenmed.khatmah.core.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally

// ── Push: navigate forward ─────────────────────────────────────────────────
// Only the entering page animates; the current page stays still.

/** Entering page slides in from the right and fades in. */
val pushEnter: EnterTransition = materialSharedAxisXIn(
    initialOffsetX = { (it * initialOffset).toInt() },
)

/** Current page is frozen while a new page pushes over it. */
val pushExit: ExitTransition = ExitTransition.None

// ── Pop: navigate backward (taps + predictive back gesture) ─────────────────
// Only the exiting page animates; the destination is revealed at rest behind it.

/**
 * Destination page is the real, live composition kept alive by NavHost — it is
 * simply revealed at rest behind the shrinking top page. No enter animation.
 */
val popEnter: EnterTransition = EnterTransition.None

/** Scale the exiting page settles at while the back gesture is held. */
const val POP_EXIT_SCALE = 0.90f

/** Fraction of progress that is scale-dominant before the slide ramps in. */
private const val SLIDE_LEAD = 0.35f

/**
 * Slide curve: near-flat through the early, scale-dominant part of the drag, then
 * eases out so the page travels off-screen — mostly as the gesture commits.
 */
private val slideEasing = Easing { t ->
    if (t <= SLIDE_LEAD) 0f
    else FastOutSlowInEasing.transform((t - SLIDE_LEAD) / (1f - SLIDE_LEAD))
}

/**
 * Exiting page scales down toward its center (no fade) and slides off to the
 * right, revealing the previous destination behind it — the "card sliding away"
 * look. Both effects are part of the **single seekable transition**, so:
 *
 *  • NavHost drives the fraction by the back-gesture progress (live preview),
 *    springing back on cancel;
 *  • on commit the motion continues smoothly from wherever the finger left it to
 *    fully off-screen — the page stays composed until the slide *completes*, so
 *    it never disappears mid-slide.
 *
 * [slideEasing] keeps the first [SLIDE_LEAD] of the drag scale-dominant, so a
 * short swipe reads as a scale that finishes its slide on release. Rounded
 * corners are layered on via [popExitCorners] in [pageComposable].
 */
val popExit: ExitTransition = scaleOut(
    targetScale   = POP_EXIT_SCALE,
    animationSpec = tween(
        durationMillis = MotionConstants.DefaultMotionDuration,
        easing         = FastOutSlowInEasing,
    ),
) + slideOutHorizontally(
    animationSpec = tween(
        durationMillis = MotionConstants.DefaultMotionDuration,
        easing         = slideEasing,
    ),
    targetOffsetX = { fullWidth -> fullWidth },
)
