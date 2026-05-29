package com.lhacenmed.khatmah.feature.today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo

/** Quick surah preview card embedded in TodayTab. Each surah is its own card row. */
@Composable
internal fun QuickIndexSection(
    surahs:            List<SurahInfo>,
    pageFor:           (suraNum: Int) -> Int,
    onContinueReading: () -> Unit,
    onSurahClick:      (suraNum: Int) -> Unit,
    onFullIndex:       () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text       = stringResource(R.string.today_quick_index),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        // Individual card per surah row
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            surahs.forEach { surah ->
                QuickSurahCard(
                    num     = surah.num,
                    name    = surah.name,
                    page    = pageFor(surah.num),
                    onClick = { onSurahClick(surah.num) },
                )
            }
        }

        // Action buttons row
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onContinueReading, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.today_continue_reading), maxLines = 1)
            }
            OutlinedButton(onClick = onFullIndex, modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.today_full_index), maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuickSurahCard(num: Int, name: String, page: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        ),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = toEasternArabic(num) + ".",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text       = "سورة $name",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text  = stringResource(R.string.today_page, page),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Converts an integer to its Eastern Arabic numeral string. */
internal fun toEasternArabic(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")