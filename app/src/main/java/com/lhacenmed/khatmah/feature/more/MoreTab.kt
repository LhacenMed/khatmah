package com.lhacenmed.khatmah.feature.more

import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.util.AppPrefs
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.PreferenceItem
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.core.ui.components.PreferenceSwitch
import com.lhacenmed.khatmah.core.ui.components.OptionSelectBottomSheet
import com.lhacenmed.khatmah.core.ui.components.SheetOption
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.nav.NavScreen
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.util.LocaleManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.lhacenmed.khatmah.feature.quran.data.WarshImageRepository
import com.lhacenmed.khatmah.feature.quran.data.WarshImageDownloadState

// Items within this distance from the top animate directly; farther ones jump-then-animate.
private const val SMOOTH_SCROLL_THRESHOLD = 4

val MoreTab = NavScreen(
    route    = Route.MORE,
    iconRes  = R.drawable.ic_profile,
    labelRes = R.string.more,
) { padding -> MoreScreen(padding) }

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScreen(padding: PaddingValues) {
    val context = LocalContext.current

    // ── Reminder state from ReminderPrefs ──────────────────────────────────────
    val reminders     by ReminderPrefs.flow.collectAsState()
    val morningConfig  = reminders.find { it.id == "adhkar:morning"      }
    val eveningConfig  = reminders.find { it.id == "adhkar:evening"      }
    val mulkConfig     = reminders.find { it.id == "sunnah:al_mulk"     }
    val baqarahConfig  = reminders.find { it.id == "sunnah:al_baqarah"  }

    // Persist a config update and immediately reschedule the alarm.
    fun saveReminder(config: ReminderConfig) {
        ReminderPrefs.save(context, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ReminderScheduler.schedule(context, config)
        }
    }

    // Opens the system time-picker for [config] if it is currently enabled.
    fun pickTime(config: ReminderConfig?) {
        config?.takeIf { it.enabled } ?: return
        TimePickerDialog(
            context,
            { _, h, m -> saveReminder(config.copy(timeHour = h, timeMinute = m)) },
            config.timeHour,
            config.timeMinute,
            true, // 24-hour
        ).show()
    }

    // ── Reader style bottom sheet ──────────────────────────────────────────────
    // Using explicit MutableState (not 'by' delegation) so assignments inside
    // lambdas are visible to the compiler and suppress the "assigned but never read" warning.
    val showLanguageSheet: MutableState<Boolean>    = rememberSaveable { mutableStateOf(false) }

    rememberCoroutineScope()
    val selectedPrint by MushafPrefs.selected.collectAsState()

    val languageOptions = listOf(
        SheetOption(
            key   = null,
            title = stringResource(R.string.language_system_default),
        ),
        SheetOption(
            key   = "en",
            title = stringResource(R.string.language_english),
        ),
        SheetOption(
            key   = "ar",
            title = stringResource(R.string.language_arabic),
        ),
    )

    val listState   = rememberLazyListState()
    val nav         = LocalNavController.current
    val scrollToTop = LocalScrollToTop.current

    // Two-phase scroll-to-top: instant jump to near the top, then smooth animation
    // for the final items. This avoids the visual churn of animating through dozens
    // of items when the list is scrolled far down.
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            if (listState.firstVisibleItemIndex > SMOOTH_SCROLL_THRESHOLD) {
                listState.scrollToItem(SMOOTH_SCROLL_THRESHOLD)
            }
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state          = listState,
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
                icon  = R.drawable.round_book_24,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_surat_mulk),
                icon  = R.drawable.round_book_24,
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_surat_baqarah),
                icon  = R.drawable.round_book_24,
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_settings)) }
        item {
            PreferenceItem(
                title   = stringResource(R.string.more_daily_alarm),
                icon    = Icons.Outlined.NotificationsActive,
                onClick = { nav.navigate(Route.DAILY_ALARM) },
            )
        }
        item {
            PreferenceItem(
                title   = stringResource(R.string.more_start_new_khatmah),
                icon    = Icons.Outlined.Add,
                onClick = { nav.navigate(Route.NEW_KHATMAH) },
            )
        }

        // ── Prayer Times ──────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_prayer_times)) }
        item {
            PreferenceItem(
                title   = stringResource(R.string.more_prayer_times_settings),
                icon    = R.drawable.ic_mosque,
                onClick = { nav.navigate(Route.PRAYER_SETTINGS) },
            )
        }
        item {
            PreferenceItem(
                title   = stringResource(R.string.more_qibla_direction),
                icon    = R.drawable.ic_kaaba,
                onClick = { nav.navigate(Route.QIBLA) },
            )
        }

        // ── Adhkar Alarms ─────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_adhkar_alarms)) }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_day_adhkar_alarm),
                icon      = Icons.Outlined.WbSunny,
                isChecked = morningConfig?.enabled ?: false,
                onClick   = {
                    morningConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) }
                },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_day_adhkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = morningConfig?.enabled ?: false,
                trailingIcon = {
                    ReminderTrailing(config = morningConfig)
                },
                onClick = { pickTime(morningConfig) },
            )
        }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_night_adhkar_alarm),
                icon      = Icons.Outlined.DarkMode,
                isChecked = eveningConfig?.enabled ?: false,
                onClick   = {
                    eveningConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) }
                },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_night_adhkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = eveningConfig?.enabled ?: false,
                trailingIcon = {
                    ReminderTrailing(config = eveningConfig)
                },
                onClick = { pickTime(eveningConfig) },
            )
        }

        // ── Sunnahs Alarms ────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_sunnahs_alarms)) }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_al_mulk_alarm),
                icon      = Icons.Outlined.Notifications,
                isChecked = mulkConfig?.enabled ?: false,
                onClick   = {
                    mulkConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) }
                },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_al_mulk_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = mulkConfig?.enabled ?: false,
                trailingIcon = {
                    ReminderTrailing(config = mulkConfig)
                },
                onClick = { pickTime(mulkConfig) },
            )
        }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_al_baqarah_alarm),
                icon      = Icons.Outlined.Notifications,
                isChecked = baqarahConfig?.enabled ?: false,
                onClick   = {
                    baqarahConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) }
                },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_al_baqarah_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = baqarahConfig?.enabled ?: false,
                trailingIcon = {
                    ReminderTrailing(config = baqarahConfig)
                },
                onClick = { pickTime(baqarahConfig) },
            )
        }

        // ── Khatmah App ───────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_khatmah_app)) }
        item {
            PreferenceItem(
                title        = stringResource(R.string.theme_settings),
                icon         = Icons.Outlined.Palette,
                onClick      = { nav.navigate(Route.THEME_SETTINGS) },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_mushaf_print),
                icon         = Icons.Outlined.AutoStories,
                trailingIcon = { TrailingLabelText(label = stringResource(selectedPrint.nameRes)) },
                onClick      = { nav.navigate(Route.MUSHAF_PRINTS) },
            )
        }
        item {
            PreferenceItem(
                title = stringResource(R.string.more_language),
                icon  = Icons.Outlined.Language,
                trailingIcon = {
                    val currentTag = LocaleManager.getCurrentTag()
                    TrailingLabelText(
                        label = languageOptions.find { it.key == currentTag }?.title
                            ?: stringResource(R.string.language_system_default)
                    )
                },
                onClick = { showLanguageSheet.value = true },
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

        // ── Debug ─────────────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_debug)) }
        item {
            PreferenceItem(
                title   = stringResource(R.string.more_debug_db),
                icon    = Icons.Outlined.BugReport,
                onClick = { nav.navigate(Route.DEBUG_DB) },
            )
        }
    }

    if (showLanguageSheet.value) {
        OptionSelectBottomSheet(
            title    = stringResource(R.string.more_language),
            options  = languageOptions,
            selected = LocaleManager.getCurrentTag(),
            onSelect = { tag ->
                LocaleManager.setLocale(tag)
                showLanguageSheet.value = false
            },
            onDismiss = { showLanguageSheet.value = false },
        )
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

/**
 * Trailing content for a reminder time row: gear icon (when enabled) + 24 h time label.
 * Tapping the parent [PreferenceItem] opens the time picker via the [pickTime] callback above.
 */
@Composable
private fun ReminderTrailing(config: ReminderConfig?) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        TrailingTimeText(
            time    = config?.let { "%02d:%02d".format(it.timeHour, it.timeMinute) } ?: "--:--",
            enabled = config?.enabled ?: false,
        )
    }
}

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
            text       = count.toString(),
            modifier   = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
private fun TrailingTimeText(time: String, enabled: Boolean) {
    Text(
        text  = time,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
            .copy(alpha = if (enabled) 1f else 0.38f),
    )
}

/**
 * Trailing label for preference rows that show the current selection value.
 */
@Composable
private fun TrailingLabelText(label: String) {
    Text(
        text  = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}