package com.lhacenmed.khatmah.core.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

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

/**
 * Exiting page slides out to the right and fades out.
 * [PredictiveBackContainer] calls [onBack] directly from the gesture's end state,
 * so this animation plays as a natural continuation of the throw.
 */
val popExit: ExitTransition = materialSharedAxisXOut(
    targetOffsetX = { (it * initialOffset).toInt() },
)

/** Destination page is already at rest; no enter animation on pop. */
val popEnter: EnterTransition = EnterTransition.None