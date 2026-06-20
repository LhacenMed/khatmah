package com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.prayer.data.JuristicMethod
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.ui.components.PrayerTimesPreviewBar

// Body only — the title + back arrow come from ScreenHostActivity (see Dest.Juristic.titleRes).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuristicScreen() {
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        PrayerTimesPreviewBar(settings = settings)
        HorizontalDivider()

        val options = listOf(
            JuristicMethod.SHAFI_MALIKI_HANBALI to R.string.juristic_shafi,
            JuristicMethod.HANAFI               to R.string.juristic_hanafi,
        )

        options.forEach { (method, labelRes) ->
            val selected = settings.juristic == method
            ListItem(
                headlineContent = { Text(stringResource(labelRes)) },
                trailingContent = {
                    RadioButton(
                        selected = selected,
                        onClick  = {
                            if (!selected) PrayerSettings.save(context, settings.copy(juristic = method))
                        },
                    )
                },
                modifier = Modifier.clickable {
                    if (!selected) PrayerSettings.save(context, settings.copy(juristic = method))
                },
            )
            HorizontalDivider(
                modifier  = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
