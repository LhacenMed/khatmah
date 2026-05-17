package com.lhacenmed.khatmah.feature.today.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R

@Composable
internal fun KhatmahStats(readCount: Int, totalCount: Int) {
    val rawProgress  = if (totalCount > 0) readCount.toFloat() / totalCount else 0f
    val animProgress by animateFloatAsState(
        targetValue   = rawProgress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label         = "khatmah_progress",
    )
    val remaining = (totalCount - readCount).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsTitle()
        LinearProgressIndicator(progress = { animProgress }, modifier = Modifier.fillMaxWidth())
        StatsFooter(left = "$readCount", right = "$remaining")
    }
}

/**
 * Mirrors [KhatmahStats] — static title shown real,
 * progress bar and count labels replaced with shimmer blocks.
 */
@Composable
internal fun SkeletonStats() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsTitle()
        // LinearProgressIndicator track height is 4dp
        SkeletonBox(Modifier.fillMaxWidth().height(4.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatsLabel(prefix = stringResource(R.string.today_previous_prefix)) {
                SkeletonBox(Modifier.size(width = 20.dp, height = 12.dp))
            }
            StatsLabel(prefix = stringResource(R.string.today_upcoming_prefix)) {
                SkeletonBox(Modifier.size(width = 20.dp, height = 12.dp))
            }
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun StatsTitle() {
    Text(
        text       = stringResource(R.string.today_khatmah_title),
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.fillMaxWidth(),
        textAlign  = TextAlign.Start,
    )
}

@Composable
private fun StatsFooter(left: String, right: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.today_previous_prefix), style = MaterialTheme.typography.bodySmall)
            Text(left,  style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.today_upcoming_prefix), style = MaterialTheme.typography.bodySmall)
            Text(right, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatsLabel(prefix: String, value: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(prefix, style = MaterialTheme.typography.bodySmall)
        value()
    }
}