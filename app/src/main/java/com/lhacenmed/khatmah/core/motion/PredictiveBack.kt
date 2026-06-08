package com.lhacenmed.khatmah.core.motion

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ── Tuning ────────────────────────────────────────────────────────────────────

private const val SCALE_TARGET    = 0.88f   // scale at full gesture progress (100%)
private const val CORNER_TARGET   = 28f     // corner radius (dp) shown whenever page is scaled
private const val SHIFT_TARGET_DP = 10f     // horizontal nudge toward swipe edge (dp)
private const val CANCEL_MS       = 300     // ms to spring back on cancel
private const val SCRIM_MAX_ALPHA = 0.20f   // max dim on the page below

// ── Shared state ──────────────────────────────────────────────────────────────

/**
 * Gesture progress exposed via [LocalBackGestureProgress].
 * Use [Modifier.backScrim] on the previous page's root to dim proportionally.
 */
@Stable
class BackGestureState {
    /** 0f = idle, 1f = fully progressed. */
    var progress: Float by mutableFloatStateOf(0f)
        internal set
    var isActive: Boolean by mutableStateOf(false)
        internal set
}

val LocalBackGestureProgress = staticCompositionLocalOf { BackGestureState() }

// ── Container ─────────────────────────────────────────────────────────────────

/**
 * Wraps a navigation destination and drives the predictive back gesture visuals.
 *
 * Gesture behavior:
 *  • Scale and shiftX track the finger via [Animatable.snapTo] (zero lag).
 *  • Corners are shown instantly whenever [scale] < 1f — no ramp animation.
 *
 * On commit (finger released past threshold):
 *  • [shiftX] is zeroed so it doesn't fight [popExit]'s directional slide.
 *  • [scale] is left at its current throw value so [popExit] continues naturally
 *    from that state rather than snapping back to 1f first.
 *  • [onBack] is called immediately after.
 *
 * On cancel (finger pulled back):
 *  • All values spring back to resting state.
 *
 * @param enabled Whether to intercept the system back.
 * @param onBack  Invoked on commit — should call `navController.popBackStack()`.
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope     = rememberCoroutineScope()
    val backState = remember { BackGestureState() }

    val scale  = remember { Animatable(1f) }
    val shiftX = remember { Animatable(0f) }   // dp, positive = right

    PredictiveBackHandler(enabled = enabled) { events: Flow<BackEventCompat> ->
        backState.isActive = true
        try {
            events.collect { event ->
                val p           = event.progress
                val targetShift = when (event.swipeEdge) {
                    BackEventCompat.EDGE_LEFT ->  SHIFT_TARGET_DP * p
                    else                     -> -SHIFT_TARGET_DP * p
                }
                scale.snapTo(1f - (1f - SCALE_TARGET) * p)
                shiftX.snapTo(targetShift)
                backState.progress = p
            }
            // Gesture committed — zero the nudge so popExit slides cleanly,
            // but preserve scale so the exit animation continues from this state.
            shiftX.snapTo(0f)
            backState.progress = 0f
            onBack()
        } catch (_: CancellationException) {
            // Gesture cancelled — spring everything back.
            scope.launch {
                val spec = tween<Float>(CANCEL_MS, easing = FastOutSlowInEasing)
                launch { scale.animateTo(1f, spec) }
                launch { shiftX.animateTo(0f, spec) }
                backState.progress = 0f
            }
        } finally {
            backState.isActive = false
        }
    }

    CompositionLocalProvider(LocalBackGestureProgress provides backState) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = scale.value
                    scaleX       = s
                    scaleY       = s
                    translationX = shiftX.value * density
                    // Corners appear instantly the moment the page is scaled — no ramp.
                    if (s < 1f) {
                        clip  = true
                        shape = RoundedCornerShape(CORNER_TARGET.dp)
                    } else {
                        clip  = false
                        shape = RectangleShape
                    }
                },
        ) {
            content()
        }
    }
}

// ── Scrim modifier ────────────────────────────────────────────────────────────

/**
 * Draws a translucent dark overlay proportional to the active back gesture progress.
 * Apply to the *previous* page's root so it dims while the top page scales away.
 * Reads [LocalBackGestureProgress] automatically.
 */
@Composable
fun Modifier.backScrim(): Modifier {
    val state = LocalBackGestureProgress.current
    return drawBehind {
        val alpha = SCRIM_MAX_ALPHA * state.progress
        if (alpha > 0f) drawRect(Color.Black.copy(alpha = alpha))
    }
}