package com.lhacenmed.khatmah.ui.common

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.lhacenmed.khatmah.ui.common.motion.emphasizeEasing

// ── Cascade-style parallax transition constants ───────────────────────────────
// Matches HeightAnimatableViewFlipper: 350 ms, FastOutSlowIn, 25% parallax offset.
private const val DURATION_CASCADE  = 700
private const val PARALLAX_FRACTION = 0.25f

// ── Scrim overlay ─────────────────────────────────────────────────────────────
// Max opacity of the dim overlay drawn over the exiting (parallax-drifting) screen.
private const val SCRIM_MAX_ALPHA = 0.50f

// ── Legacy variant constants (used by non-primary composable helpers) ─────────
const val DURATION_ENTER = 700
const val DURATION_EXIT  = 400
const val initialOffset  = 0.10f

private val enterTween = tween<IntOffset>(durationMillis = DURATION_ENTER, easing = emphasizeEasing)
private val exitTween  = tween<IntOffset>(durationMillis = DURATION_ENTER, easing = emphasizeEasing)
private val fadeSpec   = tween<Float>(durationMillis = DURATION_EXIT)

/**
 * Primary page transition — cascade-style parallax slide with a dim scrim.
 *
 * Forward: entering screen slides in from the right (full width) over the exiting
 * screen, which drifts left at [PARALLAX_FRACTION] speed. A dark scrim fades in
 * over the exiting screen during the transition, reinforcing the layered depth.
 *
 * Back: mirrored; the scrim fades out as the returning screen eases back from its
 * parked parallax position, while the dismissed screen slides out to the right.
 *
 * Easing: [emphasizeEasing] on both axes for a smooth ease-in-out feel.
 */
fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    // New screen: slides in from the right, full width.
    enterTransition    = {
        slideInHorizontally(
            animationSpec  = tween(DURATION_CASCADE, easing = emphasizeEasing),
            initialOffsetX = { it },
        )
    },
    // Current screen: drifts left at PARALLAX_FRACTION — stays mostly visible under the new screen.
    exitTransition     = {
        slideOutHorizontally(
            animationSpec = tween(DURATION_CASCADE, easing = emphasizeEasing),
            targetOffsetX = { -(it * PARALLAX_FRACTION).toInt() },
        )
    },
    // Returning screen: eases in from its parked parallax position back to 0.
    popEnterTransition = {
        slideInHorizontally(
            animationSpec  = tween(DURATION_CASCADE, easing = emphasizeEasing),
            initialOffsetX = { -(it * PARALLAX_FRACTION).toInt() },
        )
    },
    // Dismissed screen: slides out to the right, full width.
    popExitTransition  = {
        slideOutHorizontally(
            animationSpec = tween(DURATION_CASCADE, easing = emphasizeEasing),
            targetOffsetX = { it },
        )
    },
    content = { entry ->
        // ── Scrim overlay ─────────────────────────────────────────────────────
        // Fades in over this screen as it exits (parallax drift left), and fades
        // out as it returns (popEnter from its parked position).
        //   PostExit  → SCRIM_MAX_ALPHA : fully dimmed after forward navigation
        //   Visible   → 0f             : no overlay while this screen is active
        //   PreEnter  → SCRIM_MAX_ALPHA : start dimmed so back-nav fades it out
        val scrimAlpha by transition.animateFloat(
            transitionSpec = { tween(DURATION_CASCADE, easing = emphasizeEasing) },
            label          = "scrim",
        ) { state ->
            when (state) {
                EnterExitState.PostExit -> SCRIM_MAX_ALPHA
                EnterExitState.Visible  -> 0f
                EnterExitState.PreEnter -> SCRIM_MAX_ALPHA
            }
        }

        Box(Modifier.fillMaxSize()) {
            this@composable.content(entry)
            // Overlay sits on top of content; alpha = 0 when idle, so no cost.
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
    },
)

/** Fade-through variant — scale + fade in, fade out. */
fun NavGraphBuilder.fadeThroughComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)) },
    exitTransition     = { fadeOut(tween(90)) },
    popEnterTransition = { fadeIn(tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)) },
    popExitTransition  = { fadeOut(tween(90)) },
    content            = content
)

/** Legacy slide variant — plain slide + fade without shared axis. */
fun NavGraphBuilder.animatedComposableLegacy(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { slideInHorizontally(enterTween,  initialOffsetX = {  (it * initialOffset).toInt() }) + fadeIn(fadeSpec) },
    exitTransition     = { slideOutHorizontally(exitTween,  targetOffsetX  = { -(it * initialOffset).toInt() }) + fadeOut(fadeSpec) },
    popEnterTransition = { slideInHorizontally(enterTween,  initialOffsetX = { -(it * initialOffset).toInt() }) + fadeIn(fadeSpec) },
    popExitTransition  = { slideOutHorizontally(exitTween,  targetOffsetX  = {  (it * initialOffset).toInt() }) + fadeOut(fadeSpec) },
    content            = content
)

/** Asymmetric variant — slides in, fades out; fades in, slides out on back. */
fun NavGraphBuilder.animatedComposableVariant(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { slideInHorizontally(enterTween, initialOffsetX = { (it * initialOffset).toInt() }) + fadeIn(fadeSpec) },
    exitTransition     = { fadeOut(fadeSpec) },
    popEnterTransition = { fadeIn(fadeSpec) },
    popExitTransition  = { slideOutHorizontally(exitTween, targetOffsetX  = { (it * initialOffset).toInt() }) + fadeOut(fadeSpec) },
    content            = content
)

/** Bottom-sheet-style vertical slide. */
fun NavGraphBuilder.slideInVerticallyComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { slideInVertically(enterTween,  initialOffsetY = {  it }) + fadeIn() },
    exitTransition     = { slideOutVertically() },
    popEnterTransition = { slideInVertically() },
    popExitTransition  = { slideOutVertically(enterTween, targetOffsetY  = {  it }) + fadeOut() },
    content            = content
)