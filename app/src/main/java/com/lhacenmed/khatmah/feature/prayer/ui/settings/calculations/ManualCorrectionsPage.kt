package com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.feature.prayer.data.ManualCorrections
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.ui.components.PrayerTimesPreviewBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualCorrectionsContent() {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()
    val corr     = settings.corrections

    // Local draft values — kept in sync with flow; saved on every tap.
    var fajr    by remember(corr) { mutableIntStateOf(corr.fajr) }
    var sunrise by remember(corr) { mutableIntStateOf(corr.sunrise) }
    var dhuhr   by remember(corr) { mutableIntStateOf(corr.dhuhr) }
    var asr     by remember(corr) { mutableIntStateOf(corr.asr) }
    var maghrib by remember(corr) { mutableIntStateOf(corr.maghrib) }
    var isha    by remember(corr) { mutableIntStateOf(corr.isha) }

    fun commit(newCorr: ManualCorrections) {
        PrayerSettings.save(context, settings.copy(corrections = newCorr))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_corrections)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding()),
        ) {
            // Live preview updates as corrections change.
            PrayerTimesPreviewBar(settings = settings)
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))

            CorrectionRow(
                label    = stringResource(R.string.prayer_fajr),
                value    = fajr,
                onChange = { v -> fajr = v; commit(ManualCorrections(v, sunrise, dhuhr, asr, maghrib, isha)) },
            )
            CorrectionRow(
                label    = stringResource(R.string.prayer_sunrise),
                value    = sunrise,
                onChange = { v -> sunrise = v; commit(ManualCorrections(fajr, v, dhuhr, asr, maghrib, isha)) },
            )
            CorrectionRow(
                label    = stringResource(R.string.prayer_dhuhr),
                value    = dhuhr,
                onChange = { v -> dhuhr = v; commit(ManualCorrections(fajr, sunrise, v, asr, maghrib, isha)) },
            )
            CorrectionRow(
                label    = stringResource(R.string.prayer_asr),
                value    = asr,
                onChange = { v -> asr = v; commit(ManualCorrections(fajr, sunrise, dhuhr, v, maghrib, isha)) },
            )
            CorrectionRow(
                label    = stringResource(R.string.prayer_maghrib),
                value    = maghrib,
                onChange = { v -> maghrib = v; commit(ManualCorrections(fajr, sunrise, dhuhr, asr, v, isha)) },
            )
            CorrectionRow(
                label    = stringResource(R.string.prayer_isha),
                value    = isha,
                onChange = { v -> isha = v; commit(ManualCorrections(fajr, sunrise, dhuhr, asr, maghrib, v)) },
            )

            // Reset button — disabled when all corrections are already zero.
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedButton(
                    onClick  = {
                        val zero = ManualCorrections()
                        fajr = 0; sunrise = 0; dhuhr = 0; asr = 0; maghrib = 0; isha = 0
                        commit(zero)
                    },
                    enabled  = !corr.isAllZero,
                ) {
                    Text(stringResource(R.string.corrections_reset))
                }
            }

            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

// ─── Correction row ───────────────────────────────────────────────────────────

@Composable
private fun CorrectionRow(
    label:    String,
    value:    Int,
    onChange: (Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick  = { if (value > -60) onChange(value - 1) },
                    enabled  = value > -60,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text      = if (value == 0) "0" else "%+d".format(value),
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.widthIn(min = 36.dp),
                )
                IconButton(
                    onClick  = { if (value < 60) onChange(value + 1) },
                    enabled  = value < 60,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text  = stringResource(R.string.corrections_mins),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}