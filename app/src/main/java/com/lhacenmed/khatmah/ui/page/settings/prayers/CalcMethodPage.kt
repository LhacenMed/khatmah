package com.lhacenmed.khatmah.ui.page.settings.prayers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.CalcMethod
import com.lhacenmed.khatmah.data.prayer.IshaMode
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.ui.nav.LocalNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcMethodContent() {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val settings by PrayerSettings.flow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_calc_method)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {

            // Live preview — updates as the user taps different methods.
            PrayerTimesPreviewBar(settings = settings)
            HorizontalDivider()

            LazyColumn(
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                items(CalcMethod.entries, key = { it.methodId }) { method ->
                    val selected = settings.method == method
                    ListItem(
                        headlineContent   = { Text(method.displayName) },
                        supportingContent = {
                            Text(
                                text  = ishaSubtitle(method),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent   = {
                            RadioButton(
                                selected = selected,
                                onClick  = {
                                    if (!selected) PrayerSettings.save(context, settings.copy(method = method))
                                },
                            )
                        },
                        modifier = Modifier.clickable {
                            if (!selected) PrayerSettings.save(context, settings.copy(method = method))
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
    }
}

/** One-line subtitle: "Fajr: 18.0° / Isha: 17.0°" or "Fajr: 18.5° / Isha: 90 min after Maghrib". */
@Composable
private fun ishaSubtitle(method: CalcMethod): String {
    val ishaStr = when (val m = method.ishaMode) {
        is IshaMode.Angle        -> stringResource(R.string.calc_method_isha_angle, m.degrees)
        is IshaMode.FixedMinutes -> stringResource(R.string.calc_method_isha_mins, m.minutes)
    }
    return stringResource(R.string.calc_method_subtitle, method.fajrAngle, ishaStr)
}