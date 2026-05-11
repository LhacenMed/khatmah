package com.lhacenmed.khatmah.feature.khatmah.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.feature.khatmah.ui.components.ReminderTimeItem
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler

/**
 * "Daily Alarm" page — lets users enable/disable and reschedule
 * up to five khatmah reading reminders per day.
 * Slot IDs follow the "khatmah:{index}" pattern seeded by [ReminderPrefs].
 */
@Composable
fun DailyAlarmPage() {
    val nav     = LocalNavController.current
    val context = LocalContext.current

    val reminders by ReminderPrefs.flow.collectAsState()
    val slots = reminders
        .filter { it.id.startsWith("khatmah:") }
        .sortedBy { it.alarmCode }

    fun save(config: ReminderConfig) {
        ReminderPrefs.save(context, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ReminderScheduler.schedule(context, config)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.more_daily_alarm),
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text      = stringResource(R.string.daily_alarm_desc),
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            items(slots, key = { it.id }) { config ->
                ReminderTimeItem(
                    config       = config,
                    onToggle     = { enabled -> save(config.copy(enabled = enabled)) },
                    onTimeChange = { h, m   -> save(config.copy(timeHour = h, timeMinute = m)) },
                )
            }
        }
    }
}