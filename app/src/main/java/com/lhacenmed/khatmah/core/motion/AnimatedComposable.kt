package com.lhacenmed.khatmah.core.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.fadeThroughComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = {
        fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(220, delayMillis = 90)
                )
    },
    exitTransition = {
        fadeOut(animationSpec = tween(90))
    },
    popEnterTransition = {
        fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(220, delayMillis = 90)
                )
    },
    popExitTransition = {
        fadeOut(animationSpec = tween(90))
    },
    content = content
)

// ── Shared constants ──────────────────────────────────────────────────────────

const val DURATION_ENTER = 400
const val DURATION_EXIT  = 200
const val initialOffset  = 0.10f

private val slideTween = tween<IntOffset>(
    durationMillis = DURATION_ENTER,
    easing         = emphasizeEasing,
)
private val fadeTween = tween<Float>(durationMillis = DURATION_EXIT)

// ── Shared-axis slide + fade (used by onboarding linear flow) ─────────────────

fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    deepLinks = deepLinks,
    enterTransition = {
        materialSharedAxisXIn(initialOffsetX = { (it * initialOffset).toInt() })
    },
    exitTransition = {
        materialSharedAxisXOut(targetOffsetX = { -(it * initialOffset).toInt() })
    },
    popEnterTransition = {
        materialSharedAxisXIn(initialOffsetX = { -(it * initialOffset).toInt() })
    },
    popExitTransition = {
        materialSharedAxisXOut(targetOffsetX = { (it * initialOffset).toInt() })
    },
    content = content
)

// ── Predictive-back-aware page transition ─────────────────────────────────────

/**
 * Push forward → only the entering page animates ([pushEnter] / [pushExit]).
 * Pop backward  → only the exiting page animates ([buildPopExit] / [popEnter]).
 * Predictive back gesture → [PredictiveBackContainer] handles scale + corners;
 * the previous destination is already at rest ([popEnter] = None).
 *
 * [gestureCommitShift] — a [MutableFloatState] written by [PredictiveBackContainer]
 * via [onCommitShift] at gesture commit time, holding the horizontal shift in dp.
 * This function is called in composable context (inside NavHost), so the State
 * object is captured by closure. The non-composable [popExitTransition] lambda
 * reads [MutableFloatState.floatValue] and resets it to 0f so tap-back navigations
 * that follow always get the zero-offset transition.
 *
 * [screenDensity] — dp-to-px multiplier captured by closure for the same reason.
 */
@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.pageComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    gestureCommitShift: MutableFloatState,
    screenDensity: Float,
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { pushEnter },
    exitTransition     = { pushExit  },
    popEnterTransition = { popEnter  },
    popExitTransition  = {
        val shiftDp = gestureCommitShift.floatValue
        gestureCommitShift.floatValue = 0f   // consume — next pop starts clean
        buildPopExit(commitShiftDp = shiftDp, density = screenDensity)
    },
    content            = content,
)
