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
            SettingsSectionHeader(stringResource(R.string.prayer_settings_qibla))

            ListItem(
                headlineContent = { Text(stringResource(R.string.prayer_settings_qibla)) },
                modifier        = Modifier.clickable { nav.navigate(Route.QIBLA) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            HorizontalDivider()

            // ── Reminders section ─────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.prayer_settings_reminders))

            ListItem(
                headlineContent = { Text(stringResource(R.string.prayer_settings_adhan_reminders)) },
                modifier        = Modifier.clickable { nav.navigate(Route.ADHAN_REMINDERS) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            HorizontalDivider()

            // ── Location section ──────────────────────────────────────────────
            SettingsSectionHeader(stringResource(R.string.prayer_settings_location))

            if (loc != null) {
                ListItem(
                    headlineContent   = {
                        Text(loc.cityName.ifBlank { stringResource(R.string.prayers_city_unknown) })
                    },
                    supportingContent = {
                        Text(
                            "%.4f°, %.4f°".format(loc.lat, loc.lng),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            } else {
                ListItem(headlineContent = { Text(stringResource(R.string.prayers_city_unknown)) })
            }

            // Auto-detect Location
            ListItem(
                headlineContent = { Text(stringResource(R.string.prayer_settings_auto_location)) },
                modifier        = Modifier.clickable { nav.navigate(Route.ONBOARDING_LOCATION) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            // Manual Search Location
            ListItem(
                headlineContent = { Text(stringResource(R.string.prayer_settings_manual_location)) },
                modifier        = Modifier.clickable {
                    nav.navigate(Route.ONBOARDING_COUNTRY_SELECT.replace("{fromSettings}", "true"))
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                },
            )

            HorizontalDivider()

            // ── Prayer Times Calculation section ──────────────────────────────
            SettingsSectionHeader(stringResource(R.string.prayer_settings_calc_section))

            // Automatic Settings toggle
            ListItem(
                headlineContent = { Text(stringResource(R.string.prayer_settings_auto)) },
                trailingContent = {
                    Switch(
                        checked         = settings.autoSettings,
                        onCheckedChange = { on ->
                            val updated = if (on) {
                                settings.copy(autoSettings = true)
                            } else {
                                // Seed manual values from current effective so
                                // switching off doesn't reset to stale stored values.
                                settings.copy(
                                    autoSettings = false,
                                    method       = effective.method,
                                    juristic     = effective.juristic,
                                )
                            }
                            PrayerSettings.save(context, updated)
                        },
                    )
                },
            )

            // Calculation Method
            NavSettingsItem(
                title    = stringResource(R.string.prayer_settings_calc_method),
                subtitle = effective.method.displayName,
                enabled  = !settings.autoSettings,
                onClick  = { nav.navigate(Route.PRAYER_CALC_METHOD) },
            )

            // Juristic Method
            NavSettingsItem(
                title    = stringResource(R.string.prayer_settings_juristic),
                subtitle = stringResource(
                    if (effective.juristic == JuristicMethod.HANAFI) R.string.juristic_hanafi
                    else R.string.juristic_shafi
                ),
                enabled  = !settings.autoSettings,
                onClick  = { nav.navigate(Route.PRAYER_JURISTIC) },
            )

            // Daylight Saving Time
            NavSettingsItem(
                title    = stringResource(R.string.prayer_settings_dst),
                subtitle = stringResource(
                    when (settings.dstMode) {
                        DstMode.AUTOMATIC -> R.string.dst_automatic
                        DstMode.PLUS_ONE  -> R.string.dst_plus_one
                        DstMode.MINUS_ONE -> R.string.dst_minus_one
                    }
                ),
                enabled  = !settings.autoSettings,
                onClick  = { nav.navigate(Route.PRAYER_DST) },
            )

            // Manual Corrections
            NavSettingsItem(
                title    = stringResource(R.string.prayer_settings_corrections),
                subtitle = if (settings.corrections.isAllZero) stringResource(R.string.corrections_all_default)
                else stringResource(R.string.corrections_customized),
                enabled  = !settings.autoSettings,
                onClick  = { nav.navigate(Route.PRAYER_MANUAL_CORRECTIONS) },
            )

            // Higher Latitude
            NavSettingsItem(
                title    = stringResource(R.string.prayer_settings_higher_lat),
                subtitle = stringResource(
                    when (settings.higherLatMode) {
                        HigherLatMode.NONE             -> R.string.higher_lat_none
                        HigherLatMode.MIDDLE_OF_NIGHT  -> R.string.higher_lat_middle
                        HigherLatMode.SEVENTH_OF_NIGHT -> R.string.higher_lat_seventh
                        HigherLatMode.ANGLE_BASED      -> R.string.higher_lat_angle
                    }
                ),
                enabled  = !settings.autoSettings,
                onClick  = { nav.navigate(Route.PRAYER_HIGHER_LAT) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
internal fun NavSettingsItem(
    title:    String,
    subtitle: String,
    enabled:  Boolean,
    onClick:  () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent   = {
            Text(
                text  = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        },
        supportingContent = {
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        },
        trailingContent   = {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}