package com.lhacenmed.khatmah.feature.khatmah.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.core.ui.components.showTimePicker

/**
 * Card row for a single reminder slot: time display (24 h) · gear icon · toggle.
 * The gear icon is only visible when [config] is enabled.
 * Tapping the gear opens the system-native time-picker dialog.
 * Used by [DailyAlarmPage]; extract the same composable wherever card-style reminder rows are needed.
 */
@Composable
fun ReminderTimeItem(
    config:       ReminderConfig,
    onToggle:     (Boolean) -> Unit,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier:     Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // Time label — start side (right in RTL)
            Text(
                text     = "%02d:%02d".format(config.timeHour, config.timeMinute),
                style    = MaterialTheme.typography.displaySmall,
                modifier = Modifier.weight(1f),
            )

            // Gear icon only when enabled
            if (config.enabled) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { showTimePicker(context, config.timeHour, config.timeMinute, onTimeChange) },
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Toggle — end side (left in RTL)
            Switch(
                checked         = config.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}