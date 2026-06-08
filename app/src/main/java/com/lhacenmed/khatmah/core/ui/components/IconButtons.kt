package com.lhacenmed.khatmah.core.ui.components

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * An icon button that shows a native Android tooltip on long-press and suppresses
 * the click action after the long-press gesture — matching the behavior of [com.lhacenmed.khatmah.core.ui.components.bottomnav.NavButton].
 *
 * Uses [combinedClickable] with a no-op [onLongClick] so:
 *  • The gesture detector knows a long-press handler exists → [onClick] is suppressed
 *    on the UP event that follows a long-press.
 *  • [PressInteraction.Cancel] is emitted when the long-press fires → forwarded as
 *    [MotionEvent.ACTION_CANCEL] to the invisible anchor → tooltip dismisses immediately,
 *    before any navigation or composition change can cancel the forwarding coroutine.
 *
 * Out-of-bounds cancel mirrors [com.lhacenmed.khatmah.core.ui.components.bottomnav.NavButton]: a [pointerInput] on the touch-target Box
 * monitors Final-pass events; if the pressed pointer exits the button's layout bounds,
 * a [PressInteraction.Cancel] is enqueued via [cancelRequest] so the ripple disappears
 * immediately — [combinedClickable] alone does not emit Cancel on out-of-bounds drag
 * after a long-press.
 *
 * Navigation dismiss: a [DisposableEffect] observes [LocalLifecycleOwner] for
 * [Lifecycle.Event.ON_PAUSE] — fired as the host [NavBackStackEntry] begins its exit
 * transition (including predictive-back gesture start) — and immediately dismisses the
 * native tooltip by calling [ViewCompat.setTooltipText] with null then restoring the
 * text. [MotionEvent.ACTION_CANCEL] alone is insufficient here: it routes through
 * [android.view.View.handleTooltipUp] which only posts a delayed hide runnable
 * (getLongPressTooltipHideTimeout = 1500 ms), letting the popup outlast the animation.
 * Passing null calls [android.view.View.hideTooltip] synchronously.
 *
 * The invisible anchor view uses [requiredHeight] / [requiredWidth] to override the
 * parent Box's measurement constraints — it can extend beyond the Box bounds without
 * affecting the Box size or the icon's position. [anchorExtraBottom] extends the anchor
 * downward so the tooltip appears just below the host bar rather than overlapping it;
 * for a standard 64 dp TopAppBar with a 48 dp button: 8 dp.
 *
 * @param onClick           Invoked on a normal tap (suppressed after a long-press).
 * @param tooltipText       Text shown by the system tooltip on long-press.
 * @param modifier          Applied to the outer [Box].
 * @param enabled           Enables/disables both interaction and ripple.
 * @param size              Touch target diameter and ripple bound radius. Default 48 dp.
 * @param anchorExtraBottom Extra height below [size] added to the invisible anchor only —
 *                          does not affect button layout or icon position.
 * @param content           Icon composable rendered inside the touch target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    tooltipText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    anchorExtraBottom: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val anchorRef         = remember { mutableStateOf<View?>(null) }
    val latestAnchor      by rememberUpdatedState(anchorRef.value)
    val latestTooltipText by rememberUpdatedState(tooltipText)

    // ── Active press tracking ─────────────────────────────────────────────────
    // Mirrors NavButton: track exactly which PressInteraction is live so we can
    // emit a matching Cancel when the pointer exits bounds.
    // cancelRequest bridges the non-suspend pointerInput scope to the suspend
    // interactionSource.emit — tryEmit enqueues, LaunchedEffect drains and cancels.
    val activePress   = remember { mutableStateOf<PressInteraction.Press?>(null) }
    val cancelRequest = remember { MutableSharedFlow<PressInteraction.Press>(extraBufferCapacity = 1) }

    // Shared downTime between the forwarding coroutine and the lifecycle observer.
    // Plain array avoids recomposition overhead — both sites run on the main thread.
    val downTimeRef = remember { longArrayOf(0L) }

    // ── Tooltip forwarding + press state tracking ─────────────────────────────
    // Relay PressInteraction lifecycle as synthetic MotionEvents to the invisible
    // anchor view. The critical path is Cancel: combinedClickable emits
    // PressInteraction.Cancel the moment the long-press gesture fires (500 ms),
    // which arrives here as ACTION_CANCEL and dismisses the tooltip immediately —
    // well before onClick / navigation could cancel this coroutine.
    // activePress is updated here so the pointerInput bounds-check always has
    // a reference to the exact interaction object needed for Cancel.
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            // Track active press for out-of-bounds cancel.
            when (interaction) {
                is PressInteraction.Press   -> activePress.value = interaction
                is PressInteraction.Release,
                is PressInteraction.Cancel  -> activePress.value = null
            }
            // Tooltip forwarding.
            val anchor = latestAnchor ?: return@collect
            when (interaction) {
                is PressInteraction.Press -> {
                    downTimeRef[0] = SystemClock.uptimeMillis()
                    anchor.dispatchSyntheticMotion(
                        MotionEvent.ACTION_DOWN, downTimeRef[0], downTimeRef[0],
                        interaction.pressPosition.x,
                    )
                }
                is PressInteraction.Release -> anchor.dispatchSyntheticMotion(
                    MotionEvent.ACTION_UP, downTimeRef[0], SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
                is PressInteraction.Cancel -> anchor.dispatchSyntheticMotion(
                    MotionEvent.ACTION_CANCEL, downTimeRef[0], SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
            }
        }
    }

    // Drain cancelRequest and emit the Cancel to the interactionSource.
    // Runs on the main coroutine — safe to call interactionSource.emit (suspend).
    LaunchedEffect(cancelRequest) {
        cancelRequest.collect { press ->
            interactionSource.emit(PressInteraction.Cancel(press))
        }
    }

    // ── Navigation dismiss ────────────────────────────────────────────────────
    // LocalLifecycleOwner resolves to the NavBackStackEntry inside a NavHost
    // composable. ON_PAUSE fires when the entry begins its exit transition —
    // popBackStack() for standard back, or gesture-start for predictive back.
    //
    // setTooltipText(null) calls View.hideTooltip() synchronously, closing the
    // popup immediately. ACTION_CANCEL alone is not sufficient: it routes through
    // View.handleTooltipUp() which only posts a delayed hide runnable (1500 ms),
    // letting the tooltip outlast the page exit animation. Restoring the text
    // right after keeps the view primed for predictive-back cancellation.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val anchor = anchorRef.value ?: return@LifecycleEventObserver
                ViewCompat.setTooltipText(anchor, null)
                ViewCompat.setTooltipText(anchor, latestTooltipText)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // Outer Box is exactly [size × size] — icon centers naturally inside it.
    // AndroidView uses requiredWidth/requiredHeight to break out of the Box's
    // measurement constraints: the anchor overflows downward by [anchorExtraBottom]
    // without contributing to the Box's intrinsic size or shifting the icon.
    Box(
        modifier         = modifier
            .padding(horizontal = 4.dp)
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        // NoOpHapticFeedback suppresses the duplicate haptic combinedClickable emits
        // before onLongClick — the system tooltip on the anchor fires its own at 500 ms.
        CompositionLocalProvider(LocalHapticFeedback provides NoOpHapticFeedback) {
            Box(
                modifier = Modifier
                    .size(size)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication        = ripple(bounded = false, radius = size / 2),
                        enabled           = enabled,
                        onLongClick       = {},   // suppresses onClick after long-press
                        onClick           = onClick,
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Final).changes.forEach { change ->
                                    val press = activePress.value
                                    if (press != null && change.pressed) {
                                        val p = change.position
                                        if (p.x < 0f || p.x > this.size.width ||
                                            p.y < 0f || p.y > this.size.height) {
                                            cancelRequest.tryEmit(press)
                                            activePress.value = null
                                        }
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        AndroidView(
            // requiredWidth/requiredHeight override parent Box constraints so the
            // anchor extends below the Box without affecting its measured size.
            modifier = Modifier
                .requiredWidth(size)
                .requiredHeight(size + anchorExtraBottom),
            factory  = { ctx ->
                View(ctx).apply {
                    visibility = View.INVISIBLE  // never intercepts real touches
                    ViewCompat.setTooltipText(this, tooltipText)
                    anchorRef.value = this
                }
            },
            update = { view ->
                // Keep text in sync if tooltipText recomposes (e.g. locale change).
                ViewCompat.setTooltipText(view, tooltipText)
            },
        )
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

/**
 * Suppresses the duplicate haptic that [combinedClickable] emits before [onLongClick].
 * Scoped only to the touch-target Box; nothing else in the tree is affected.
 * The system tooltip on the anchor view fires its own haptic at 500 ms.
 */
private val NoOpHapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}