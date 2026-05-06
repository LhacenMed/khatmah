package com.lhacenmed.khatmah.feature.prayer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.components.PreferenceSwitch
import com.lhacenmed.khatmah.core.ui.theme.applyOpacity
import com.lhacenmed.khatmah.feature.prayer.data.*
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsContent() {
    val nav       = LocalNavController.current
    val context   = LocalContext.current
    val scrollBeh = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings  by PrayerSettings.flow.collectAsState()
    val loc       = remember { OnboardingPrefs.location(context) }

    // Resolved (auto-detected when auto is on) values for display.
    val effective = remember(settings, loc) {
        settings.resolve(loc?.countryCode.orEmpty())
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBeh.nestedScrollConnection),
        topBar   = {
            LargeTopAppBar(
                title          = { Text(stringResource(R.string.prayer_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                scrollBehavior = scrollBeh,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            // ── Qibla section ─────────────────────────────────────────────────
            PreferenceSubtitle(text = stringResource(R.string.prayer_settings_qibla))

            PreferenceItem(
                title   = stringResource(R.string.prayer_settings_qibla),
                onClick = { nav.navigate(Route.QIBLA) },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            // ── Reminders section ─────────────────────────────────────────────
            PreferenceSubtitle(text = stringResource(R.string.prayer_settings_reminders))

            PreferenceItem(
                title   = stringResource(R.string.prayer_settings_adhan_reminders),
                onClick = { nav.navigate(Route.ADHAN_REMINDERS) },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            // ── Location section ──────────────────────────────────────────────
            PreferenceSubtitle(text = stringResource(R.string.prayer_settings_location))

            if (loc != null) {
                PreferenceItem(
                    title = loc.cityName.ifBlank { stringResource(R.string.prayers_city_unknown) },
                    trailingIcon = {
                        Text(
                            text = "%.4f°, %.4f°".format(loc.lat, loc.lng),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(true)
                        )
                    }
                )
            } else {
                PreferenceItem(title = stringResource(R.string.prayers_city_unknown))
            }

            // Auto-detect Location
            PreferenceItem(
                title   = stringResource(R.string.prayer_settings_auto_location),
                onClick = { nav.navigate(Route.ONBOARDING_LOCATION) },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            // Manual Search Location
            PreferenceItem(
                title   = stringResource(R.string.prayer_settings_manual_location),
                onClick = {
                    nav.navigate(Route.ONBOARDING_COUNTRY_SELECT.replace("{fromSettings}", "true"))
                },
                trailingIcon = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            // ── Prayer Times Calculation section ──────────────────────────────
            PreferenceSubtitle(text = stringResource(R.string.prayer_settings_calc_section))

            // Automatic Settings toggle
            PreferenceSwitch(
                title     = stringResource(R.string.prayer_settings_auto),
                isChecked = settings.autoSettings,
                onClick   = {
                    val on = !settings.autoSettings
                    val updated = if (on) {
                        settings.copy(autoSettings = true)
                    } else {
                        settings.copy(
                            autoSettings = false,
                            method       = effective.method,
                            juristic     = effective.juristic,
                        )
                    }
                    PrayerSettings.save(context, updated)
                },
            )

            // Calculation Method
            PreferenceItem(
                title       = stringResource(R.string.prayer_settings_calc_method),
                description = effective.method.displayName,
                enabled     = !settings.autoSettings,
                onClick     = { nav.navigate(Route.PRAYER_CALC_METHOD) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(!settings.autoSettings)
                    )
                },
            )

            // Juristic Method
            PreferenceItem(
                title       = stringResource(R.string.prayer_settings_juristic),
                description = stringResource(
                    if (effective.juristic == JuristicMethod.HANAFI) R.string.juristic_hanafi
                    else R.string.juristic_shafi
                ),
                enabled     = !settings.autoSettings,
                onClick     = { nav.navigate(Route.PRAYER_JURISTIC) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(!settings.autoSettings)
                    )
                },
            )

            // Daylight Saving Time
            PreferenceItem(
                title       = stringResource(R.string.prayer_settings_dst),
                description = stringResource(
                    when (settings.dstMode) {
                        DstMode.AUTOMATIC -> R.string.dst_automatic
                        DstMode.PLUS_ONE  -> R.string.dst_plus_one
                        DstMode.MINUS_ONE -> R.string.dst_minus_one
                    }
                ),
                enabled     = !settings.autoSettings,
                onClick     = { nav.navigate(Route.PRAYER_DST) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(!settings.autoSettings)
                    )
                },
            )

            // Manual Corrections
            PreferenceItem(
                title       = stringResource(R.string.prayer_settings_corrections),
                description = if (settings.corrections.isAllZero) stringResource(R.string.corrections_all_default)
                else stringResource(R.string.corrections_customized),
                enabled     = !settings.autoSettings,
                onClick     = { nav.navigate(Route.PRAYER_MANUAL_CORRECTIONS) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(!settings.autoSettings)
                    )
                },
            )

            // Higher Latitude
            PreferenceItem(
                title       = stringResource(R.string.prayer_settings_higher_lat),
                description = stringResource(
                    when (settings.higherLatMode) {
                        HigherLatMode.NONE             -> R.string.higher_lat_none
                        HigherLatMode.MIDDLE_OF_NIGHT  -> R.string.higher_lat_middle
                        HigherLatMode.SEVENTH_OF_NIGHT -> R.string.higher_lat_seventh
                        HigherLatMode.ANGLE_BASED      -> R.string.higher_lat_angle
                    }
                ),
                enabled     = !settings.autoSettings,
                onClick     = { nav.navigate(Route.PRAYER_HIGHER_LAT) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(!settings.autoSettings)
                    )
                },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
