package com.lhacenmed.khatmah.ui.page.tabs

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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prefs.AppPrefs
import com.lhacenmed.khatmah.data.quran.WarshDownloadState
import com.lhacenmed.khatmah.data.quran.WarshXmlRepository
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.component.OptionSelectBottomSheet
import com.lhacenmed.khatmah.ui.component.PreferenceItem
import com.lhacenmed.khatmah.ui.component.PreferenceSubtitle
import com.lhacenmed.khatmah.ui.component.PreferenceSwitch
import com.lhacenmed.khatmah.ui.component.SheetOption
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.LocalScrollToTop
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.util.LocaleManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.lhacenmed.khatmah.data.quran.WarshImageRepository
import com.lhacenmed.khatmah.data.quran.WarshImageDownloadState

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

    // ── Alarm switch states ────────────────────────────────────────────────────
    // Persisted across recompositions; drives enabled state on paired time items.
    var dayAdhkarOn      by rememberSaveable { mutableStateOf(true) }
    var nightAdhkarOn    by rememberSaveable { mutableStateOf(true) }
    var alMulkAlarmOn    by rememberSaveable { mutableStateOf(false) }
    var alBaqarahAlarmOn by rememberSaveable { mutableStateOf(false) }

    // ── Reader style bottom sheet ──────────────────────────────────────────────
    // Using explicit MutableState (not 'by' delegation) so assignments inside
    // lambdas are visible to the compiler and suppress the "assigned but never read" warning.
    val showReaderStyleSheet: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }
    val showLanguageSheet: MutableState<Boolean>    = rememberSaveable { mutableStateOf(false) }
    val readerStyle by AppPrefs.readerStyle.collectAsState()

    val warshRepo = remember { WarshXmlRepository(context) }
    val warshState by warshRepo.downloadState.collectAsState()
    val warshImageRepo  = remember { WarshImageRepository(context) }
    val warshImageState by warshImageRepo.downloadState.collectAsState()

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val readerStyleOptions = listOf(
        SheetOption(
            key      = AppPrefs.ReaderStyle.TEXT,
            title    = stringResource(R.string.reader_style_text),
            subtitle = stringResource(R.string.reader_style_text_desc),
        ),
        SheetOption(
            key               = AppPrefs.ReaderStyle.IMAGES,
            title             = stringResource(R.string.reader_style_images),
            subtitle          = stringResource(R.string.reader_style_images_desc),
            hasCustomTrailing = true,
        ),
        SheetOption(
            key               = AppPrefs.ReaderStyle.SVG_WARSH,
            title             = stringResource(R.string.reader_style_svg_warsh),
            subtitle          = stringResource(R.string.reader_style_svg_warsh_desc),
            hasCustomTrailing = true,
        ),
    )

    val languageOptions = listOf<SheetOption<String?>>(
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

        // ── Adhkar Alarms ─────────────────────────────────────────────────────
        item { PreferenceSubtitle(text = stringResource(R.string.more_adhkar_alarms)) }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_day_adhkar_alarm),
                icon      = Icons.Outlined.WbSunny,
                isChecked = dayAdhkarOn,
                onClick   = { dayAdhkarOn = !dayAdhkarOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_day_adhkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = dayAdhkarOn,
                trailingIcon = { TrailingTimeText(time = "07:00 AM", enabled = dayAdhkarOn) },
            )
        }
        item {
            PreferenceSwitch(
                title     = stringResource(R.string.more_night_adhkar_alarm),
                icon      = Icons.Outlined.DarkMode,
                isChecked = nightAdhkarOn,
                onClick   = { nightAdhkarOn = !nightAdhkarOn },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_night_adhkar_time),
                icon         = Icons.Outlined.Schedule,
                enabled      = nightAdhkarOn,
                trailingIcon = { TrailingTimeText(time = "05:30 PM", enabled = nightAdhkarOn) },
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
                title        = stringResource(R.string.theme_settings),
                icon         = Icons.Outlined.AutoStories,
                onClick      = { nav.navigate(Route.THEME_SETTINGS) },
            )
        }
        item {
            PreferenceItem(
                title        = stringResource(R.string.more_reader_style),
                icon         = Icons.Outlined.AutoStories,
                trailingIcon = {
                    TrailingLabelText(
                        label = stringResource(
                            when (readerStyle) {
                                AppPrefs.ReaderStyle.TEXT      -> R.string.reader_style_text
                                AppPrefs.ReaderStyle.IMAGES    -> R.string.reader_style_images
                                AppPrefs.ReaderStyle.SVG_WARSH -> R.string.reader_style_svg_warsh
                            }
                        )
                    )
                },
                onClick = { showReaderStyleSheet.value = true },
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
    }

    // ── Reader Style Sheet ────────────────────────────────────────────────────
    if (showReaderStyleSheet.value) {
        OptionSelectBottomSheet(
            title      = stringResource(R.string.more_reader_style),
            options    = readerStyleOptions,
            selected   = readerStyle,
            sheetState = sheetState,
            onSelect = { style ->
                when {
                    style == AppPrefs.ReaderStyle.IMAGES && warshImageState !is WarshImageDownloadState.Downloaded -> {
                        if (warshImageState !is WarshImageDownloadState.Downloading &&
                            warshImageState !is WarshImageDownloadState.Connecting) {
                            scope.launch {
                                warshImageRepo.downloadAll().collectLatest { /* state updates internally */ }
                            }
                        }
                    }
                    style == AppPrefs.ReaderStyle.SVG_WARSH && warshState !is WarshDownloadState.Downloaded -> {
                        if (warshState !is WarshDownloadState.Downloading &&
                            warshState !is WarshDownloadState.Connecting) {
                            scope.launch {
                                warshRepo.downloadAll().collectLatest { /* state updates internally */ }
                            }
                        }
                    }
                    else -> {
                        scope.launch {
                            sheetState.hide()
                            AppPrefs.setReaderStyle(context, style)
                            showReaderStyleSheet.value = false
                        }
                    }
                }
            },
            onDismiss = { showReaderStyleSheet.value = false },
            optionTrailingContent = { option ->
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    when (option.key) {
                        AppPrefs.ReaderStyle.IMAGES -> when (val s = warshImageState) {
                            is WarshImageDownloadState.NotDownloaded,
                            is WarshImageDownloadState.Error -> Icon(
                                imageVector        = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(20.dp),
                            )
                            is WarshImageDownloadState.Connecting -> CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            is WarshImageDownloadState.Downloading -> CircularProgressIndicator(
                                progress    = { s.progress },
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            is WarshImageDownloadState.Downloaded -> {
                                if (readerStyle == AppPrefs.ReaderStyle.IMAGES) {
                                    Icon(
                                        imageVector        = Icons.Default.Check,
                                        contentDescription = null,
                                        tint               = MaterialTheme.colorScheme.primary,
                                        modifier           = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        AppPrefs.ReaderStyle.SVG_WARSH -> when (val s = warshState) {
                            is WarshDownloadState.NotDownloaded,
                            is WarshDownloadState.Error -> Icon(
                                imageVector        = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(20.dp),
                            )
                            is WarshDownloadState.Connecting -> CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            is WarshDownloadState.Downloading -> CircularProgressIndicator(
                                progress    = { s.progress },
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary,
                            )
                            is WarshDownloadState.Downloaded -> {
                                if (readerStyle == AppPrefs.ReaderStyle.SVG_WARSH) {
                                    Icon(
                                        imageVector        = Icons.Default.Check,
                                        contentDescription = null,
                                        tint               = MaterialTheme.colorScheme.primary,
                                        modifier           = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        )
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
        style = MaterialTheme.typography.bodyMedium,
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