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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import com.lhacenmed.khatmah.core.ui.components.dispatchSyntheticMotion
import kotlinx.coroutines.flow.MutableSharedFlow

// Pill dimensions — matches M3 NavigationBar indicator spec.
private val PillWidth          = 64.dp
private val PillHeight         = 32.dp
private val PillWidthCollapsed = 0.dp
private val PillShape          = RoundedCornerShape(percent = 50)

// Embedded anchor height.
//
// The slot's top edge sits navSurface.top + 8dp below the screen (Row's vertical
// padding). Centering an AnchorHeight view in the 0×0 container at Alignment.TopCenter
// places its top edge at: slotTop − AnchorHeight/2 = navSurface.top + 8dp − 24dp
//                                                   = navSurface.top − 16dp
// → 16dp above the nav bar Surface for all inset configurations.
private val AnchorHeight = 48.dp

/**
 * Material 3 style tab button with an embedded tooltip anchor.
 *
 * Anchor strategy — mirrors [IconButton]'s proven embed pattern instead of relying on
 * the external [TooltipAnchorRow] offset, which proved unreliable for M3's taller nav
 * bar dimensions:
 *  • A zero-size [Box] sits at [Alignment.TopCenter] of the root [BoxWithConstraints].
 *    Zero size means it contributes nothing to layout height.
 *  • An invisible [AndroidView] inside uses [requiredWidth] / [requiredHeight] to break
 *    the parent's 0dp constraints, spanning the full slot width and extending
 *    [AnchorHeight]/2 dp above the slot top — always above the nav bar Surface.
 *  • [outerSource] interactions are dispatched to this anchor first (before any suspend
 *    call), then forwarded to [pillSource] for the bounded ripple.
 *
 * Layout skeleton (identical to [NavButtonM2]):
 *  • [BoxWithConstraints] root — full slot width via [modifier]; [maxWidth] feeds the anchor.
 *  • matchParentSize [Box] + [combinedClickable] — sole touch owner ([outerSource]).
 *    pressPosition is relative to the full slot, matching the anchor's coordinate space.
 *  • Visual [Column] sibling — pill container + label; drives layout height.
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
    onClick: () -> Unit,
) {
    val outerSource = remember { MutableInteractionSource() }
    val pillSource  = remember { MutableInteractionSource() }
    val anchorRef   = remember { mutableStateOf<View?>(null) }
    val latestLabel by rememberUpdatedState(label)

    val density = LocalDensity.current
    val pillCenterX = with(density) { PillWidth.toPx()  / 2f }
    val pillCenterY = with(density) { PillHeight.toPx() / 2f }

    // ── Visual state ──────────────────────────────────────────────────────────
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
    // Identical pattern to NavButtonM2 — see its KDoc for rationale.
    val activePress   = remember { mutableStateOf<PressInteraction.Press?>(null) }
    val cancelRequest = remember { MutableSharedFlow<PressInteraction.Press>(extraBufferCapacity = 1) }

    // Collect outerSource: tooltip dispatch runs first (before any suspend call) so the
    // embedded anchor's long-press timer starts at the same instant as NavButtonM2's path.
    LaunchedEffect(outerSource) {
        var downTime        = 0L
        var activePillPress: PressInteraction.Press? = null

        outerSource.interactions.collect { interaction ->
            // 1. Tooltip forwarding — dispatched before pillSource.emit (suspend) so timing
            //    matches NavButtonM2 exactly. pressPosition.x is slot-relative (matchParentSize
            //    touch Box = full slot width = embedded anchor width).
            val anchor = anchorRef.value
            when (interaction) {
                is PressInteraction.Press -> {
                    downTime = SystemClock.uptimeMillis()
                    anchor?.dispatchSyntheticMotion(
                        MotionEvent.ACTION_DOWN, downTime, downTime,
                        interaction.pressPosition.x,
                    )
                }
                is PressInteraction.Release -> anchor?.dispatchSyntheticMotion(
                    MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
                is PressInteraction.Cancel -> anchor?.dispatchSyntheticMotion(
                    MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(),
                    interaction.press.pressPosition.x,
                )
            }

            // 2. Track active press for out-of-bounds cancel.
            when (interaction) {
                is PressInteraction.Press   -> activePress.value = interaction
                is PressInteraction.Release,
                is PressInteraction.Cancel  -> activePress.value = null
            }

            // 3. Forward to pillSource with centered offset so ripple always originates
            //    from the pill center regardless of where in the slot the user tapped.
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
        }
    }

    // Drain cancelRequest and emit Cancel to outerSource; the collector above then
    // propagates it to pillSource automatically.
    LaunchedEffect(cancelRequest) {
        cancelRequest.collect { press ->
            outerSource.emit(PressInteraction.Cancel(press))
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // BoxWithConstraints exposes maxWidth (= slot width) consumed by the embedded
    // anchor so its coordinate space matches the touch Box — identical to NavButtonM2.
    BoxWithConstraints(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val slotWidth = maxWidth

        // ── Touch target ── full slot width × column height ────────────────
        // onLongClick = {} suppresses onClick after 500 ms tooltip press, same as NavButtonM2.
        // pointerInput (Final pass) cancels the ripple when the finger exits slot bounds.
        CompositionLocalProvider(LocalHapticFeedback provides M3NoOpHaptic) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .combinedClickable(
                        interactionSource = outerSource,
                        indication        = null,
                        onLongClick       = {},
                        onClick           = onClick,
                    )
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
            )
        }

        // ── Visual layer ── pill container + label; drives layout height ───
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Outer Box: fixed PillWidth × PillHeight — the ripple container.
            // clip + indication bound the bounded ripple to the capsule shape.
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
                // Pill indicator — width springs 0 → PillWidth on selection.
                Box(
                    modifier = Modifier
                        .width(pillIndicatorWidth)
                        .height(PillHeight)
                        .clip(PillShape)
                        .background(pillBg),
                )
                Icon(
                    painter            = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(iconSize),
                )
            }

            Text(
                text  = label,
                color = iconTint,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
            )
        }

        // ── Embedded tooltip anchor ────────────────────────────────────────
        // Zero-size container at the slot's top-center contributes 0dp to layout height.
        // The AndroidView inside uses requiredWidth/Height to break the 0dp constraints:
        //   • requiredWidth(slotWidth)  → spans the full slot (matches touch Box width)
        //   • requiredHeight(AnchorHeight) → centered in 0dp Box → top at −AnchorHeight/2
        //     above this container, i.e. AnchorHeight/2 − 8dp above navSurface.top.
        // Tooltip fires above anchorRef.value.getLocationOnScreen()[1] → above the nav bar.
        Box(
            modifier         = Modifier
                .size(0.dp)
                .align(Alignment.TopCenter),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier
                    .requiredWidth(slotWidth)
                    .requiredHeight(AnchorHeight),
                factory  = { ctx ->
                    View(ctx).apply {
                        visibility = View.INVISIBLE
                        ViewCompat.setTooltipText(this, label)
                        anchorRef.value = this
                    }
                },
                update   = { view -> ViewCompat.setTooltipText(view, latestLabel) },
            )
        }
    }
}

// Suppresses combinedClickable's duplicate long-press haptic (same role as in NavButtonM2).
private val M3NoOpHaptic = object : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}