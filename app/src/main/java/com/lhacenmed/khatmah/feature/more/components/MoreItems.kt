package com.lhacenmed.khatmah.feature.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.components.PreferenceSwitch
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig

// ─── LazyListScope DSL ────────────────────────────────────────────────────────

fun LazyListScope.subtitle(@StringRes textRes: Int) =
    item { PreferenceSubtitle(text = stringResource(textRes)) }

fun LazyListScope.prefItem(
    @StringRes titleRes : Int,
    icon                : ImageVector,
    trailingIcon        : (@Composable () -> Unit)? = null,
    enabled             : Boolean                   = true,
    onClick             : (() -> Unit)?             = null,
) = item { PreferenceItem(title = stringResource(titleRes), icon = icon, trailingIcon = trailingIcon, enabled = enabled, onClick = onClick ?: {}) }

fun LazyListScope.prefItem(
    @StringRes titleRes : Int,
    @DrawableRes icon   : Int,
    trailingIcon        : (@Composable () -> Unit)? = null,
    enabled             : Boolean                   = true,
    onClick             : (() -> Unit)?             = null,
) = item { PreferenceItem(title = stringResource(titleRes), icon = icon, trailingIcon = trailingIcon, enabled = enabled, onClick = onClick ?: {}) }

/** One toggle row + one time-picker row for a reminder config. */
fun LazyListScope.reminderPair(
    @StringRes switchTitleRes : Int,
    @StringRes timeTitleRes   : Int,
    switchIcon                : ImageVector,
    config                    : ReminderConfig?,
    onToggle                  : () -> Unit,
    onPickTime                : () -> Unit,
) {
    item {
        PreferenceSwitch(
            title     = stringResource(switchTitleRes),
            icon      = switchIcon,
            isChecked = config?.enabled ?: false,
            onClick   = onToggle,
        )
    }
    item {
        PreferenceItem(
            title        = stringResource(timeTitleRes),
            icon         = Icons.Outlined.Schedule,
            enabled      = config?.enabled ?: false,
            trailingIcon = { ReminderTrailing(config) },
            onClick      = onPickTime,
        )
    }
}

// ─── Composables ──────────────────────────────────────────────────────────────

/**
 * Trailing content for a reminder time row: 24 h time label, dimmed when disabled.
 * Tapping the parent [PreferenceItem] opens the time picker.
 */
@Composable
internal fun ReminderTrailing(config: ReminderConfig?) =
    TrailingTimeText(
        time    = config?.let { "%02d:%02d".format(it.timeHour, it.timeMinute) } ?: "--:--",
        enabled = config?.enabled ?: false,
    )

/**
 * Green pill badge displaying a count — mirrors the screenshot's session counters.
 */
@Composable
internal fun CountBadge(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text       = count.toString(),
            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Trailing time label for alarm time rows; dims when the parent alarm is disabled.
 */
@Composable
internal fun TrailingTimeText(time: String, enabled: Boolean) =
    Text(
        text  = time,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
            .copy(alpha = if (enabled) 1f else 0.38f),
    )

/**
 * Trailing label for preference rows that show the current selection value.
 */
@Composable
internal fun TrailingLabelText(label: String) =
    Text(
        text  = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )