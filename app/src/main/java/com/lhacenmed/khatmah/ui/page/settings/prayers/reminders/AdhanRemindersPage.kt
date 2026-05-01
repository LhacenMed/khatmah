package com.lhacenmed.khatmah.ui.page.settings.prayers.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.AdhanSound
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.page.settings.prayers.SettingsSectionHeader

@Composable
fun AdhanRemindersPage() {
    val nav    = LocalNavController.current
    val configs by AdhanPrefs.flow.collectAsState()

    val prayerNames = listOf(
        R.string.prayer_fajr,
        R.string.prayer_sunrise,
        R.string.prayer_dhuhr,
        R.string.prayer_asr,
        R.string.prayer_maghrib,
        R.string.prayer_isha,
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.adhan_reminders_title),
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.adhan_reminders_section_title))

            prayerNames.forEachIndexed { index, nameRes ->
                val config  = configs.getOrNull(index) ?: return@forEachIndexed
                val isOn    = config.isEnabled
                val subtitle = soundSubtitle(config.sound)

                ListItem(
                    modifier          = Modifier.clickable {
                        nav.navigate(Route.adhanSoundSelection(index))
                    },
                    headlineContent   = { Text(stringResource(nameRes)) },
                    supportingContent = { Text(subtitle) },
                    trailingContent   = {
                        Icon(
                            imageVector = if (isOn) Icons.Filled.Notifications
                            else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (isOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Text(
                text     = stringResource(R.string.adhan_reminders_warning),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun soundSubtitle(sound: AdhanSound): String = when (sound) {
    is AdhanSound.Off    -> stringResource(R.string.adhan_status_off)
    is AdhanSound.Silent -> stringResource(R.string.adhan_sound_silent)
    is AdhanSound.Device -> stringResource(R.string.adhan_sound_device)
    is AdhanSound.Asset  -> sound.filename.removeSuffix(".mp3")
    is AdhanSound.Custom -> sound.displayName
}