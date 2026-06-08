package com.lhacenmed.khatmah.core.ui.components.bottomnav

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.core.ui.components.dispatchSyntheticMotion
import kotlinx.coroutines.flow.MutableSharedFlow

// Pill dimensions — matches M3 NavigationBar indicator spec.
private val PillWidth          = 64.dp   // ripple container + expanded indicator width
private val PillHeight         = 32.dp
private val PillWidthCollapsed = 0.dp    // indicator starts from nothing when unselected
private val PillShape          = RoundedCornerShape(percent = 50)

/**
 * Material 3 style tab button.
 *
 * Three-layer architecture inside the pill area:
 *
 *  1. **Ripple container** — always [PillWidth] × [PillHeight], clipped to [PillShape].
 *     Carries [pillSource]'s bounded ripple. Never resizes — ripple is always a clean capsule.
 *
 *  2. **Pill indicator** — an animated [Box] inside the container, centered.
 *     Width springs from [PillWidthCollapsed] → [PillWidth] on selection.
 *     Color springs from transparent → [pillColor]. This is the visible selection indicator.
 *
 *  3. **Icon** — sits on top of both layers via [Box] stacking.
 *
 * Touch target ([outerSource] + [Column] + [combinedClickable]) covers the full slot
 * width. [indication = null] keeps all visual feedback inside the pill area.
 *
 * [outerSource] interactions are forwarded to [pillSource] with a centered offset so
 * the ripple always originates from the pill's center regardless of tap position.
 *
 * Tooltip forwarding, out-of-bounds cancel, and no-op haptic suppression mirror
 * [NavButton] exactly — see its KDoc for architecture notes.
 */
@Composable
fun NavButtonM3(
    icon: Painter,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    pillColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    anchorProvider: (() -> View?)? = null,
    onClick: () -> Unit,
) {
    // outerSource — full-slot touch target; owns tooltip forwarding + cancel tracking.
    // pillSource  — receives forwarded centered presses; drives the bounded ripple.
    val outerSource  = remember { MutableInteractionSource() }
    val pillSource   = remember { MutableInteractionSource() }
    val latestAnchor by rememberUpdatedState(anchorProvider)

    val density = LocalDensity.current
    // Pre-compute pill center in px so we can emit a centered PressInteraction.
    val pillCenterX = with(density) { PillWidth.toPx()  / 2f }
    val pillCenterY = with(density) { PillHeight.toPx() / 2f }

    // ── Visual state ──────────────────────────────────────────────────────────
    // pillIndicatorWidth: the colored background layer inside the fixed ripple container.
    // Animates independently of the ripple — pure selection indicator.
    val pillIndicatorWidth by animateDpAsState(
        targetValue   = if (selected) PillWidth else PillWidthCollapsed,
        animationSpec = spring(stiffness = 400f),
        label         = "pillIndicatorWidth",
    )
    val pillBg by animateColorAsState(
        targetValue   = if (selected) pillColor else Color.Transparent,
        animationSpec = spring(stiffness = 400f),
        label         = "pillBg",
    )
    val iconTint by animateColorAsState(
        targetValue   = if (selected) selectedColor else unselectedColor,
        animationSpec = spring(stiffness = 500f),
        label         = "iconTint",
    )

    // ── Press tracking (out-of-bounds cancel) ─────────────────────────────────
    val activePress   = remember { mutableStateOf<PressInteraction.Press?>(null) }
    val cancelRequest = remember { MutableSharedFlow<PressInteraction.Press>(extraBufferCapacity = 1) }

    // Forward outerSource interactions → pillSource as centered presses.
    // Also handles tooltip dispatch and tracks activePress for cancel logic.
    LaunchedEffect(outerSource) {
        var downTime        = 0L
        var activePillPress: PressInteraction.Press? = null

        outerSource.interactions.collect { interaction ->
            // Out-of-bounds cancel tracking
            when (interaction) {
                is PressInteraction.Press   -> activePress.value = interaction
                is PressInteraction.Release,
                is PressInteraction.Cancel  -> activePress.value = null
            }

            // Forward to pillSource with centered offset so ripple is always centered.
            when (interaction) {
                is PressInteraction.Press -> {
                    val p = PressInteraction.Press(Offset(pillCenterX, pillCenterY))
                    activePillPress = p
                    pillSource.emit(p)
                }
                is PressInteraction.Release -> {
                    activePillPress?.let { pillSource.emit(PressInteraction.Release(it)) }
                    activePillPress = null
                }
                is PressInteraction.Cancel -> {
                    activePillPress?.let { pillSource.emit(PressInteraction.Cancel(it)) }
                    activePillPress = null
                }
            }

            // Tooltip forwarding to invisible anchor view above the nav bar.
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

    // Drain cancelRequest and emit Cancel to outerSource; LaunchedEffect above
    // then propagates it to pillSource automatically.
    LaunchedEffect(cancelRequest) {
        cancelRequest.collect { press ->
            outerSource.emit(PressInteraction.Cancel(press))
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    CompositionLocalProvider(LocalHapticFeedback provides M3NoOpHaptic) {
        Column(
            modifier = modifier
                // Full-slot touch target — indication = null, all visual feedback in pill.
                .combinedClickable(
                    interactionSource = outerSource,
                    indication        = null,
                    onLongClick       = {},
                    onClick           = onClick,
                )
                // Out-of-bounds cancel — same pattern as NavButton.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Final).changes.forEach { change ->
                                val press = activePress.value
                                if (press != null && change.pressed) {
                                    val p = change.position
                                    if (p.x < 0f || p.x > size.width ||
                                        p.y < 0f || p.y > size.height) {
                                        cancelRequest.tryEmit(press)
                                        activePress.value = null
                                    }
                                }
                            }
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ── Pill area ─────────────────────────────────────────────────────
            // Outer Box: fixed PillWidth × PillHeight — the ripple container.
            // Always full size; clip + indication bound the ripple to the capsule.
            Box(
                modifier         = Modifier
                    .width(PillWidth)
                    .height(PillHeight)
                    .clip(PillShape)
                    .indication(
                        interactionSource = pillSource,
                        indication        = ripple(
                            bounded = true,
                            color   = selectedColor.copy(alpha = 0.3f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Pill indicator — animated width, centered inside the fixed container.
                // Width springs from 0 → PillWidth on selection; color animates in sync.
                Box(
                    modifier = Modifier
                        .width(pillIndicatorWidth)
                        .height(PillHeight)
                        .clip(PillShape)
                        .background(pillBg),
                )

                // Icon — always centered on top of both layers.
                Icon(
                    painter            = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(iconSize),
                )
            }

            // ── Label ─────────────────────────────────────────────────────────
            Text(
                text  = label,
                color = iconTint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }
    }
}

// Suppresses combinedClickable's duplicate long-press haptic (same role as in NavButton).
private val M3NoOpHaptic = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}