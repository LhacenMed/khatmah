package com.lhacenmed.khatmah.ui.nav

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pressable tab button that fills its allocated slot width.
 *
 * Architecture:
 *  • BoxWithConstraints — root; exposes maxWidth so the ripple radius is derived
 *    from the actual slot width at composition time.
 *  • combinedClickable on a matchParentSize child — single touch owner. Uses a
 *    MutableInteractionSource shared with the ripple so press lifecycle is atomic.
 *  • ripple(bounded = false) — overflows the nav bar top edge. The parent Surface
 *    in BottomNavBar must NOT clip bounds.
 *  • LaunchedEffect — forwards synthetic MotionEvents to the invisible tooltip-anchor
 *    view above the nav bar; the system tooltip fires there, always above the bar.
 *  • NoOpHapticFeedback — suppresses combinedClickable's duplicate long-press haptic;
 *    the system tooltip on the anchor already fires its own at 500 ms.
 *
 * @param icon            Tab icon painter.
 * @param label           Tab label shown below the icon.
 * @param selected        Whether this tab is currently active.
 * @param selectedColor   Tint applied when selected.
 * @param unselectedColor Tint applied when not selected.
 * @param rippleColor     Press ripple colour (include alpha).
 * @param iconSize        Size of the icon glyph.
 * @param circleScale     Ripple radius as a fraction of slot width; parent must not clip.
 * @param anchorProvider  Resolves the invisible tooltip-anchor view lazily at touch time.
 * @param modifier        Slot width/weight provided by the caller.
 * @param onClick         Invoked on a normal tap.
 */
@Composable
fun NavButton(
    icon: Painter,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    rippleColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    circleScale: Float = 1.1f,
    anchorProvider: (() -> View?)? = null,
    onClick: () -> Unit,
) {
    val contentColor      = if (selected) selectedColor else unselectedColor
    val interactionSource = remember { MutableInteractionSource() }
    val latestAnchor      by rememberUpdatedState(anchorProvider)

    // ── Tooltip forwarding ────────────────────────────────────────────────────
    // Relay press lifecycle as synthetic MotionEvents to the invisible anchor view.
    // The system tooltip fires on the anchor, always appearing above the nav bar.
    // PressInteraction.Press is emitted synchronously with ACTION_DOWN, so the
    // anchor's long-press timer starts at exactly the same moment as the finger.
    LaunchedEffect(interactionSource) {
        var downTime = 0L
        interactionSource.interactions.collect { interaction ->
            val anchor = latestAnchor?.invoke() ?: return@collect
            when (interaction) {
                is PressInteraction.Press -> {
                    downTime = SystemClock.uptimeMillis()
                    anchor.dispatchSyntheticMotion(
                        MotionEvent.ACTION_DOWN, downTime, downTime,
                        interaction.pressPosition.x,
                    )
                }
                is PressInteraction.Release -> anchor.dispatchSyntheticMotion(
                    MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
                is PressInteraction.Cancel -> anchor.dispatchSyntheticMotion(
                    MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
            }
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // BoxWithConstraints exposes maxWidth (= slot width) so the ripple radius is
    // computed at composition time — no measurement callback or extra state needed.
    BoxWithConstraints(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val rippleRadius = maxWidth * circleScale / 2

        // ── Touch target ── full slot width × column height ────────────────
        // onLongClick = {} is intentional: tells the gesture detector a long-press
        // handler exists, so onClick is suppressed on the UP after 500 ms. Without
        // it, onClick fires on every UP regardless of press duration — selecting the
        // tab unintentionally after a tooltip press.
        CompositionLocalProvider(LocalHapticFeedback provides NoOpHapticFeedback) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = ripple(
                            bounded = false,
                            radius  = rippleRadius,
                            color   = rippleColor,
                        ),
                        onLongClick = {},
                        onClick     = onClick,
                    ),
            )
        }

        // ── Visual layer ── icon + label; drives the layout height ─────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painter           = icon,
                contentDescription = null,
                tint              = contentColor,
                modifier          = Modifier.size(iconSize),
            )
            Text(
                text  = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

// ─── Private helpers ──────────────────────────────────────────────────────────

/**
 * Suppresses the duplicate haptic that combinedClickable emits before onLongClick.
 * Scoped only to the touch-target Box; nothing else in the tree is affected.
 * The system tooltip on the anchor view fires its own haptic at 500 ms.
 */
private val NoOpHapticFeedback = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Sends a synthetic MotionEvent to this view and immediately recycles it.
 * y is fixed at 0f so the system tooltip always positions above the anchor,
 * regardless of where the finger sits within the nav bar.
 */
private fun View.dispatchSyntheticMotion(
    action: Int, downTime: Long, eventTime: Long, x: Float,
) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, 0f, 0)
    dispatchTouchEvent(event)
    event.recycle()
}