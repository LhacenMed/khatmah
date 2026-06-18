package com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.theme.applyOpacity
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanPrefs
import com.lhacenmed.khatmah.feature.prayer.notification.AdhanSound
import com.lhacenmed.khatmah.shared.util.AdhanSoundFiles

@Composable
fun AdhanRemindersScreen() {
    val nav    = LocalNavigator.current
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
                onBack     = { nav.back() },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceSubtitle(text = stringResource(R.string.adhan_reminders_section_title))

            prayerNames.forEachIndexed { index, nameRes ->
                val config = configs.getOrNull(index) ?: return@forEachIndexed
                val isOn = config.isEnabled
                val subtitle = soundSubtitle(config.sound)

                PreferenceItem(
                    title = stringResource(nameRes),
                    onClick = { nav.go(Dest.AdhanSoundSelection(index)) },
                    leadingIcon = {
                        val icon = when {
                            !isOn -> Icons.Outlined.NotificationsOff
                            config.sound is AdhanSound.Silent -> Icons.AutoMirrored.Outlined.VolumeOff
                            else -> Icons.Filled.Notifications
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 8.dp, end = 16.dp)
                                .size(24.dp),
                            tint = if (isOn && config.sound !is AdhanSound.Silent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    trailingIcon = {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.applyOpacity(isOn)
                        )
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            Text(
                text = stringResource(R.string.adhan_reminders_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    is AdhanSound.Asset  -> AdhanSoundFiles.getDisplayName(sound.filename)
    is AdhanSound.Custom -> sound.displayName
}
