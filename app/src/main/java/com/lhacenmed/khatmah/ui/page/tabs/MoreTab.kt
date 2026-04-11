package com.lhacenmed.khatmah.ui.page.tabs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
//import androidx.compose.material.icons.outlined.MosqueOutlined
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.PreferenceItem
import com.lhacenmed.khatmah.ui.component.PreferenceSubtitle
import com.lhacenmed.khatmah.ui.component.PreferenceSwitch
import com.lhacenmed.khatmah.ui.nav.NavScreen

val MoreTab = NavScreen(
    route    = Route.MORE,
    iconRes  = R.drawable.ic_profile,
    labelRes = R.string.more,
) { padding -> MoreScreen(padding) }

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
private fun MoreScreen(padding: PaddingValues) {
    // ── Alarm switch states ────────────────────────────────────────────────────
    // Persisted across recompositions; drives enabled state on paired time items.
    var dayAthkarOn     by rememberSaveable { mutableStateOf(true) }
    var nightAthkarOn   by rememberSaveable { mutableStateOf(true) }
    var alMulkAlarmOn   by rememberSaveable { mutableStateOf(false) }
    var alBaqarahAlarmOn by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier       = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {

        // ── Support Us ────────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_support_us)) }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_support_khatmah),
                icon  = Icons.Outlined.Favorite,
            )
        }

        // ── Current Khatmah ───────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_current_khatmah)) }
        item {
            PreferenceItem(
                title         = stringResource(R.string.more_previous_sessions),
                icon          = Icons.Outlined.SkipPrevious,
                trailingIcon  = { CountBadge(count = 13) },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_upcoming_sessions),
                icon         = Icons.Outlined.SkipNext,
                trailingIcon = { CountBadge(count = 16) },
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_bookmark),
                icon  = Icons.Outlined.Bookmark,
            )
        }

        // ── Quranic Sunnahs ───────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_quranic_sunnahs)) }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_surat_kahf),
                icon  = Icons.Outlined.MenuBook,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_surat_mulk),
                icon  = Icons.Outlined.MenuBook,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_surat_baqarah),
                icon  = Icons.Outlined.MenuBook,
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_settings)) }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_daily_alarm),
                icon  = Icons.Outlined.NotificationsActive,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_start_new_khatmah),
                icon  = Icons.Outlined.Add,
            )
        }

        // ── Prayer Times ──────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_prayer_times)) }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_prayer_times_settings),
                icon  = Icons.Outlined.Notifications, // TODO: Replace with prayer icon.
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_qibla_direction),
                icon  = Icons.Outlined.Explore,
            )
        }

        // ── Athkar Alarms ─────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_athkar_alarms)) }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_day_athkar_alarm),
                icon      = Icons.Outlined.WbSunny,
                isChecked = dayAthkarOn,
                onClick   = { dayAthkarOn = !dayAthkarOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_day_athkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = dayAthkarOn,
                trailingIcon = { TrailingTimeText(time = "07:00 AM", enabled = dayAthkarOn) },
            )
        }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_night_athkar_alarm),
                icon      = Icons.Outlined.DarkMode,
                isChecked = nightAthkarOn,
                onClick   = { nightAthkarOn = !nightAthkarOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_night_athkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = nightAthkarOn,
                trailingIcon = { TrailingTimeText(time = "05:30 PM", enabled = nightAthkarOn) },
            )
        }

        // ── Sunnahs Alarms ────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_sunnahs_alarms)) }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_al_mulk_alarm),
                icon      = Icons.Outlined.Notifications,
                isChecked = alMulkAlarmOn,
                onClick   = { alMulkAlarmOn = !alMulkAlarmOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_al_mulk_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = alMulkAlarmOn,
                trailingIcon = { TrailingTimeText(time = "09:00 PM", enabled = alMulkAlarmOn) },
            )
        }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_al_baqarah_alarm),
                icon      = Icons.Outlined.Notifications,
                isChecked = alBaqarahAlarmOn,
                onClick   = { alBaqarahAlarmOn = !alBaqarahAlarmOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_al_baqarah_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = alBaqarahAlarmOn,
                trailingIcon = { TrailingTimeText(time = "08:30 PM", enabled = alBaqarahAlarmOn) },
            )
        }

        // ── Khatmah App ───────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_khatmah_app)) }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_language),
                icon  = Icons.Outlined.Language,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_contact_us),
                icon  = Icons.Outlined.Email,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_twitter),
                icon  = Icons.Outlined.AlternateEmail,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_instagram),
                icon  = Icons.Outlined.CameraAlt,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_share_app),
                icon  = Icons.Outlined.Share,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_rate_khatmah),
                icon  = Icons.Outlined.StarBorder,
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

/**
 * Green pill badge displaying a count — mirrors the screenshot's session counters.
 */
@Composable
private fun CountBadge(count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text     = count.toString(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color    = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Trailing time label for alarm time rows; dims when the parent alarm is disabled.
 */
@Composable
private fun TrailingTimeText(time: String, enabled: Boolean) {
    Text(
        text  = time,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
            .copy(alpha = if (enabled) 1f else 0.38f),
    )
}