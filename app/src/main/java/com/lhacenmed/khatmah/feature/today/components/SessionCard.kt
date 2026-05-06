package com.lhacenmed.khatmah.feature.today.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.WarshFamily
import com.lhacenmed.khatmah.feature.today.TodayViewModel

/** Returns at most [maxWords] space-separated words from [text], appending "…" if truncated. */
private fun truncateAya(text: String, maxWords: Int = 4): String {
    val words = text.trim().split(' ')
    return if (words.size <= maxWords) text else words.take(maxWords).joinToString(" ") + "…"
}

@Composable
internal fun SessionCard(
    state:      TodayViewModel.UiState.Active,
    onMarkRead: () -> Unit,
    onRead:     () -> Unit,
) {
    val sess = state.session
    val e    = sess.entity

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.today_starts_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = stringResource(R.string.today_juz, sess.juzNum),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            // First aya of the session, truncated to max 4 words.
            if (sess.firstAyaText.isNotBlank()) {
                Text(
                    text      = truncateAya(sess.firstAyaText),
                    style     = TextStyle(
                        fontFamily    = WarshFamily,
                        fontSize      = 26.sp,
                        lineHeight    = 42.sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.primary,
                    modifier  = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Start / end rows
            SessionRangeRow(
                suraPrefix = stringResource(R.string.today_sura_prefix),
                suraName   = sess.startSuraName,
                ayaNum     = e.startAya,
                pageText   = stringResource(R.string.today_page, e.startPage),
            )
            Spacer(Modifier.height(4.dp))
            SessionRangeRow(
                suraPrefix = stringResource(R.string.today_to_prefix),
                suraName   = sess.endSuraName,
                ayaNum     = e.endAya,
                pageText   = stringResource(R.string.today_page, e.endPage),
            )

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_start_reading), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick  = onMarkRead,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFCA28),
                        contentColor   = Color(0xFF1B1B1B),
                    ),
                ) {
                    Text(stringResource(R.string.today_mark_read), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Mirrors [SessionCard] exactly — static labels and buttons shown real,
 * dynamic fields (juz, aya text, sura names, page numbers) replaced with
 * shimmer blocks sized to match their expected rendered dimensions.
 */
@Composable
internal fun SkeletonCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.today_starts_from),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.today_juz_prefix),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SkeletonBox(Modifier.size(width = 10.dp, height = 14.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Invisible text drives exact height; skeleton overlays it.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text     = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ",
                    style    = TextStyle(fontFamily = WarshFamily, fontSize = 26.sp, lineHeight = 42.sp),
                    modifier = Modifier.alpha(0f),
                )
                SkeletonBox(Modifier.size(width = 220.dp, height = 25.dp))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            SkeletonRangeRow(suraPrefix = stringResource(R.string.today_sura_prefix))
            Spacer(Modifier.height(4.dp))
            SkeletonRangeRow(suraPrefix = stringResource(R.string.today_to_prefix))

            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_start_reading), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFFFFCA28).copy(alpha = 0.38f),
                        disabledContentColor   = Color(0xFF1B1B1B).copy(alpha = 0.38f),
                    ),
                ) {
                    Text(stringResource(R.string.today_mark_read), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Shared sub-rows ───────────────────────────────────────────────────────────

@Composable
private fun SessionRangeRow(
    suraPrefix: String,
    suraName:   String,
    ayaNum:     Int,
    pageText:   String,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(suraPrefix, style = MaterialTheme.typography.bodyMedium)
            Text(suraName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.today_separator), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.today_aya_prefix), style = MaterialTheme.typography.bodyMedium)
            Text("$ayaNum", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(pageText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SkeletonRangeRow(suraPrefix: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(suraPrefix, style = MaterialTheme.typography.bodyMedium)
            SkeletonBox(Modifier.size(width = 35.dp, height = 14.dp))
            Text(stringResource(R.string.today_separator), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.today_aya_prefix), style = MaterialTheme.typography.bodyMedium)
            SkeletonBox(Modifier.size(width = 24.dp, height = 14.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = stringResource(R.string.today_page_prefix),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SkeletonBox(Modifier.size(width = 24.dp, height = 14.dp))
        }
    }
}