package com.lhacenmed.khatmah.feature.more

import android.os.Build
import com.lhacenmed.khatmah.BuildConfig
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.ui.components.OptionSelectBottomSheet
import com.lhacenmed.khatmah.core.ui.components.SheetOption
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.util.LocaleManager

// Items within this distance from the top animate directly; farther ones jump-then-animate.
private const val SMOOTH_SCROLL_THRESHOLD = 4

object MoreTab : AppTab(
    iconRes  = R.drawable.ic_profile,
    titleRes = R.string.more,
    route    = "more",
) {
    @Composable override fun Content(padding: PaddingValues) = MoreScreen(padding)
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreScreen(padding: PaddingValues) {
    val context = LocalContext.current

    // ── Reminder state from ReminderPrefs ──────────────────────────────────────
    val reminders     by ReminderPrefs.flow.collectAsState()
    val morningConfig  = reminders.find { it.id == "adhkar:morning"     }
    val eveningConfig  = reminders.find { it.id == "adhkar:evening"     }
    val mulkConfig     = reminders.find { it.id == "sunnah:al_mulk"    }
    val baqarahConfig  = reminders.find { it.id == "sunnah:al_baqarah" }

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
        showTimePicker(context, config.timeHour, config.timeMinute) { h, m ->
            saveReminder(config.copy(timeHour = h, timeMinute = m))
        }
    }

    // ── Reader style bottom sheet ──────────────────────────────────────────────
    // Using explicit MutableState (not 'by' delegation) so assignments inside
    // lambdas are visible to the compiler and suppress the "assigned but never read" warning.
    val showLanguageSheet: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }

    val selectedPrint by MushafPrefs.selected.collectAsState()

    val languageOptions = listOf(
        SheetOption(key = null, title = stringResource(R.string.language_system_default)),
        SheetOption(key = "en", title = stringResource(R.string.language_english)),
        SheetOption(key = "ar", title = stringResource(R.string.language_arabic)),
    )

    val listState   = rememberLazyListState()
    val nav         = LocalNavigator.current
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
        subtitle(R.string.more_support_us)
        prefItem(R.string.more_support_khatmah, Icons.Outlined.Favorite)

        // ── Current Khatmah ───────────────────────────────────────────────────
        subtitle(R.string.more_current_khatmah)
        prefItem(R.string.more_previous_sessions, Icons.Outlined.SkipPrevious,
            trailingIcon = { CountBadge(count = 13) })
        prefItem(R.string.more_upcoming_sessions, Icons.Outlined.SkipNext,
            trailingIcon = { CountBadge(count = 16) })
        prefItem(R.string.more_bookmark, Icons.Outlined.Bookmark)

        // ── Quranic Sunnahs ───────────────────────────────────────────────────
        subtitle(R.string.more_quranic_sunnahs)
        prefItem(R.string.more_surat_kahf,    R.drawable.round_book_24)
        prefItem(R.string.more_surat_mulk,    R.drawable.round_book_24)
        prefItem(R.string.more_surat_baqarah, R.drawable.round_book_24)

        // ── Settings ──────────────────────────────────────────────────────────
        subtitle(R.string.more_settings)
        prefItem(R.string.more_daily_alarm,       Icons.Outlined.NotificationsActive,
            onClick = { nav.go(Dest.DailyAlarm) })
        prefItem(R.string.more_start_new_khatmah, Icons.Outlined.Add,
            onClick = { nav.go(Dest.NewKhatmah) })

        // ── Prayer Times ──────────────────────────────────────────────────────
        subtitle(R.string.more_prayer_times)
        prefItem(R.string.more_prayer_times_settings, R.drawable.ic_mosque,
            onClick = { nav.go(Dest.PrayerSettings) })
        prefItem(R.string.more_qibla_direction, R.drawable.ic_kaaba,
            onClick = { nav.go(Dest.Qibla) })

        // ── Adhkar Alarms ─────────────────────────────────────────────────────
        subtitle(R.string.more_adhkar_alarms)
        reminderPair(
            switchTitleRes = R.string.more_day_adhkar_alarm,
            timeTitleRes   = R.string.more_day_adhkar_time,
            switchIcon     = Icons.Outlined.WbSunny,
            config         = morningConfig,
            onToggle       = { morningConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) } },
            onPickTime     = { pickTime(morningConfig) },
        )
        reminderPair(
            switchTitleRes = R.string.more_night_adhkar_alarm,
            timeTitleRes   = R.string.more_night_adhkar_time,
            switchIcon     = Icons.Outlined.DarkMode,
            config         = eveningConfig,
            onToggle       = { eveningConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) } },
            onPickTime     = { pickTime(eveningConfig) },
        )

        // ── Sunnahs Alarms ────────────────────────────────────────────────────
        subtitle(R.string.more_sunnahs_alarms)
        reminderPair(
            switchTitleRes = R.string.more_al_mulk_alarm,
            timeTitleRes   = R.string.more_al_mulk_time,
            switchIcon     = Icons.Outlined.Notifications,
            config         = mulkConfig,
            onToggle       = { mulkConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) } },
            onPickTime     = { pickTime(mulkConfig) },
        )
        reminderPair(
            switchTitleRes = R.string.more_al_baqarah_alarm,
            timeTitleRes   = R.string.more_al_baqarah_time,
            switchIcon     = Icons.Outlined.Notifications,
            config         = baqarahConfig,
            onToggle       = { baqarahConfig?.let { saveReminder(it.copy(enabled = !it.enabled)) } },
            onPickTime     = { pickTime(baqarahConfig) },
        )

        // ── Khatmah App ───────────────────────────────────────────────────────
        subtitle(R.string.more_khatmah_app)
        prefItem(R.string.theme_settings, Icons.Outlined.Palette,
            onClick = { nav.go(Dest.ThemeSettings) })
        prefItem(
            titleRes     = R.string.more_mushaf_print,
            icon         = Icons.Outlined.AutoStories,
            trailingIcon = { TrailingLabelText(stringResource(selectedPrint.nameRes)) },
            onClick      = { nav.go(Dest.MushafPrints) },
        )
        prefItem(
            titleRes     = R.string.more_language,
            icon         = Icons.Outlined.Language,
            trailingIcon = {
                val currentTag = LocaleManager.getCurrentTag()
                TrailingLabelText(
                    label = languageOptions.find { it.key == currentTag }?.title
                        ?: stringResource(R.string.language_system_default)
                )
            },
            onClick = { showLanguageSheet.value = true },
        )
        prefItem(R.string.more_contact_us, Icons.Outlined.Email)
        prefItem(R.string.more_twitter,    Icons.Outlined.AlternateEmail)
        prefItem(R.string.more_instagram,  Icons.Outlined.CameraAlt)
        prefItem(R.string.more_share_app,  Icons.Outlined.Share)
        prefItem(R.string.more_rate_khatmah, Icons.Outlined.StarBorder)

        // ── Debug (debug builds only) ─────────────────────────────────────────
        if (BuildConfig.DEBUG) {
            subtitle(R.string.more_debug)
            prefItem(R.string.more_debug_db, Icons.Outlined.BugReport,
                onClick = { nav.go(Dest.DbBrowser) })
            prefItem(R.string.more_trip_requests, Icons.Outlined.DirectionsBus,
                onClick = { nav.go(Dest.TripRequests) })
            prefItem(R.string.more_files_browser, Icons.Outlined.FolderOpen,
                onClick = { nav.go(Dest.FileBrowser) })
        }
    }

    if (showLanguageSheet.value) {
        OptionSelectBottomSheet(
            title     = stringResource(R.string.more_language),
            options   = languageOptions,
            selected  = LocaleManager.getCurrentTag(),
            onSelect  = { tag ->
                LocaleManager.setLocale(tag)
                showLanguageSheet.value = false
            },
            onDismiss = { showLanguageSheet.value = false },
        )
    }
}