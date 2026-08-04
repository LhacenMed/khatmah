package com.lhacenmed.khatmah.feature.quran.ui.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel.KhatmahState

/**
 * The khatmah in one line, pinned above the bottom bar: what the current wird is and how far the
 * khatmah has come. Tapping it opens the wird (or the new-khatmah flow when there is none) —
 * the feature stays one tap away without competing with reading for space.
 */
@Composable
internal fun KhatmahStrip(state: KhatmahState, onClick: () -> Unit) {
    val title: String
    val subtitle: String
    val progress: Float

    when (state) {
        is KhatmahState.Active -> {
            title    = stringResource(R.string.today_khatmah_title)
            subtitle = stringResource(
                R.string.khatmah_strip_wird,
                state.juz,
                (state.khatmah.totalDays - state.readCount).coerceAtLeast(0),
            )
            progress = if (state.khatmah.totalDays > 0)
                state.readCount.toFloat() / state.khatmah.totalDays else 0f
        }
        is KhatmahState.Done -> {
            title    = stringResource(R.string.today_khatmah_completed)
            subtitle = stringResource(R.string.today_new_khatmah)
            progress = 1f
        }
        else -> {  // None — Loading never reaches here (the tab hides the strip until resolved)
            title    = stringResource(R.string.today_no_khatmah)
            subtitle = stringResource(R.string.today_create)
            progress = 0f
        }
    }

    val animProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label         = "khatmah_progress",
    )

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        ),
    ) {
        Column {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier         = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_book),
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.size(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    Text(
                        text     = subtitle,
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    painter            = painterResource(R.drawable.ic_chevron_forward),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(18.dp),
                )
            }

            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
    }
}
