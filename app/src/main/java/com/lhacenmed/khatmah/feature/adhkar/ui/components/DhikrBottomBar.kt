package com.lhacenmed.khatmah.feature.adhkar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr

// ── Repetition label ──────────────────────────────────────────────────────────

/**
 * Maps [count] to a human-readable repetition string drawn from string resources
 * so that Arabic and English values are resolved by the active locale automatically.
 */
@Composable
fun repLabel(count: Int): String = when (count) {
    1                     -> stringResource(R.string.rep_once)
    2                     -> stringResource(R.string.rep_twice)
    3                     -> stringResource(R.string.rep_three)
    7                     -> stringResource(R.string.rep_seven)
    10                    -> stringResource(R.string.rep_ten)
    33                    -> stringResource(R.string.rep_thirty_three)
    100                   -> stringResource(R.string.rep_hundred)
    in 11..99             -> stringResource(R.string.rep_n_times_mid,  count) // singular accusative (AR: مرةً)
    in 101..Int.MAX_VALUE -> stringResource(R.string.rep_n_times_high, count) // singular genitive  (AR: مرةٍ)
    else                  -> stringResource(R.string.rep_n_times,      count) // 4–10 plural        (AR: مرات)
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

/**
 * Sticky bottom bar placed in Scaffold's bottomBar slot so M3 extends its
 * Surface background behind the navigation bar (edge-to-edge).
 *
 * The soft fade between the scrollable body and this bar is handled externally
 * by a gradient scrim Box positioned at the bottom of the content area, so
 * this Surface presents no hard visual border at its top edge.
 *
 * Regular dhikr page:
 *  • Rep label at logical-start, circle at absolute screen center, share at logical-end.
 *    Centering is achieved with a [Box] overlay (CenterStart / Center / CenterEnd)
 *    so the circle position is independent of label and button widths.
 *  • [RepCircle] is always composed — alpha = 0 when repetitions == 1 — preventing
 *    any height shift as the user moves between dhikr with different rep counts.
 *
 * Completion page:
 *  • Rep row is hidden entirely.
 */
@Composable
fun DhikrBottomBar(
    dhikr:            Dhikr?,
    repCount:         Int,
    arcFraction:      Float,
    allDone:          Boolean,
    isCompletionPage: Boolean,
    onBack:           () -> Unit,
    onShare:          () -> Unit,
    onAction:         () -> Unit,
    onTap:            () -> Unit,
) {
    Surface(
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface,
        modifier        = Modifier.clickable(
            indication        = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick           = onTap,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Rep row ───────────────────────────────────────────────────
            // Box overlay: rep label pinned to CenterStart, circle to Center,
            // share button to CenterEnd — the circle is always at the exact
            // horizontal midpoint of the screen regardless of sibling widths.
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (isCompletionPage) 0f else 1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Repetition label — logical start (physical right in RTL).
                Text(
                    text     = repLabel(dhikr?.repetitions ?: 1),
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 12.dp),
                )

                // Animated arc — always present; invisible when rep count is 1
                // so the bar height never changes between dhikr pages.
                RepCircle(
                    fraction = arcFraction,
                    count    = repCount,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = if ((dhikr?.repetitions ?: 1) > 1) 1f else 0f
                        },
                )

                // Share button — logical end (physical left in RTL).
                IconButton(
                    onClick  = onShare,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.dhikr_share),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Primary action ────────────────────────────────────────────────
            Button(
                onClick  = when {
                    isCompletionPage -> onBack
                    else             -> onAction
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape    = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text       = stringResource(
                        when {
                            isCompletionPage -> R.string.dhikr_done
                            allDone          -> R.string.dhikr_next
                            else             -> R.string.dhikr_read
                        }
                    ),
                    style      = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Circular repetition counter ───────────────────────────────────────────────

/**
 * Animated arc drawn with [Canvas].
 *
 * The arc starts at the 12-o'clock position (-90°) and sweeps clockwise.
 * [fraction] is a pre-animated value supplied by the caller; this composable
 * is purely visual and performs no animation logic of its own.
 * [count] is the completed read count displayed in the centre.
 */
@Composable
private fun RepCircle(
    fraction: Float,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val track     = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier         = modifier.size(80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackStroke    = 2.5.dp.toPx()
            val progressStroke = 8.dp.toPx()
            // Both arcs share the same circle path — inset by the larger stroke
            // so nothing clips at the edges.
            val diameter = size.minDimension - progressStroke
            val topLeft  = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize  = Size(diameter, diameter)

            // Thin background track — same center, thinner stroke
            drawArc(
                color      = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = trackStroke, cap = StrokeCap.Round),
            )
            // Thick progress arc — same center, heavier stroke
            if (fraction > 0f) {
                drawArc(
                    color      = primary,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = progressStroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text  = count.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = textColor,
        )
    }
}