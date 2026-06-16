package com.lhacenmed.khatmah.core.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

private const val POP_CORNER_DP = 28f  // corner radius at full pop-exit progress

/**
 * Push forward → only the entering page animates ([pushEnter] / [pushExit]).
 * Pop backward  → only the exiting page animates ([popExit] / [popEnter]).
 *
 * Predictive back gesture is handled **natively by NavHost**: it keeps the
 * previous destination's real composition alive and seeks [popExit] (scale +
 * slide) by the gesture progress, so the revealed page is live (never
 * re-composed) and the motion tracks the finger and springs back on cancel.
 *
 * [popExitCorners] layers gesture-tracked rounded corners on top of [popExit],
 * completing the "card sliding away" reveal.
 */
@OptIn(ExperimentalAnimationApi::class)
fun NavGraphBuilder.pageComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) = composable(
    route              = route,
    arguments          = arguments,
    deepLinks          = deepLinks,
    enterTransition    = { pushEnter },
    exitTransition     = { pushExit  },
    popEnterTransition = { popEnter  },
    popExitTransition  = { popExit   },
) { entry ->
    Box(modifier = Modifier.fillMaxSize().popExitCorners(this)) {
        content(entry)
    }
}

/**
 * Rounded-corner clip that ramps in with the pop-exit seek progress
 * (Visible → PostExit), so corners track the gesture live alongside [popExit]'s
 * scale + slide and reverse on cancel.
 *
 * The fraction is read inside the [graphicsLayer] draw lambda (a deferred read),
 * so corner updates trigger a cheap redraw rather than recomposition.
 */
@Composable
private fun Modifier.popExitCorners(scope: AnimatedVisibilityScope): Modifier {
    val fraction = scope.transition.animateFloat(label = "popExitCorner") { state ->
        if (state == EnterExitState.PostExit) 1f else 0f
    }
    return graphicsLayer {
        val f = fraction.value
        if (f > 0f) {
            clip  = true
            shape = RoundedCornerShape((POP_CORNER_DP * f).dp)
        }
    }
}
