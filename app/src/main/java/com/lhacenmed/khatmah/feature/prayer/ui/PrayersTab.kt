package com.lhacenmed.khatmah.feature.prayer.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.nav.NavScreen
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.feature.prayer.data.PrayerRepository
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTime
import com.lhacenmed.khatmah.feature.prayer.data.toAmPm
import com.lhacenmed.khatmah.shared.util.HijriDate
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Tab registration ─────────────────────────────────────────────────────────

val PrayersTab = NavScreen(
    route = Route.PRAYERS,
    iconRes = R.drawable.ic_mosque,
    labelRes = R.string.prayers,
) { padding ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PrayersScreenContent(padding)
    } else {
        // Prayer times use java.time APIs which require API 26+.
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.prayers_requires_android_8)) }
    }
}

// Virtual pager page count and the index that maps to today.
private const val PAGER_TOTAL  = 2001
private const val PAGER_CENTER = 1000

// How long (ms) to keep the "Since" / elapsed mode active after a prayer passes.
private const val ELAPSED_WINDOW_MS = 30 * 60 * 1_000L

// ─── Main screen ──────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayersScreenContent(padding: PaddingValues) {
    val context    = LocalContext.current
    val repo       = remember { PrayerRepository(context) }
    val today      = remember { LocalDate.now() }
    val scope      = rememberCoroutineScope()
    val scrollToTop = LocalScrollToTop.current

    // Pager drives date navigation; page PAGER_CENTER = today.
    val pagerState = rememberPagerState(initialPage = PAGER_CENTER) { PAGER_TOTAL }

    // Cache: avoids recalculating the same date's prayers on every pager visit.
    val prayerCache = remember { HashMap<LocalDate, List<PrayerTime>>() }

    val cityName = remember {
        OnboardingPrefs.location(context)?.cityName.orEmpty()
    }

    // Today's prayers — loaded once; powers the countdown regardless of selectedDate.
    var todayPrayers by remember { mutableStateOf<List<PrayerTime>>(emptyList()) }

    LaunchedEffect(cityName) {
        if (cityName.isNotBlank()) {
            todayPrayers = repo.getForDate(today).also { prayerCache[today] = it }
        }
    }

    // Live clock — ticks every second for the countdown.
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) { now = LocalTime.now(); delay(1_000L) }
    }

    // Reset pager to today when the tab button is tapped while already selected.
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            pagerState.animateScrollToPage(PAGER_CENTER)
        }
    }

    // ── Elapsed / countdown resolution (mirrors widget logic) ─────────────────
    // If a prayer passed within ELAPSED_WINDOW_MS → elapsed mode (count up).
    // Otherwise → countdown to the next prayer.
    val lastPassedIdx: Int? = remember(todayPrayers, now) {
        todayPrayers.indexOfLast { it.time <= now }.takeIf { it >= 0 }
    }

    // True while we're still inside the 30-min window after the last prayer.
    val inElapsedWindow: Boolean = remember(todayPrayers, lastPassedIdx, now) {
        if (lastPassedIdx == null) return@remember false
        val passedSecs = todayPrayers[lastPassedIdx].time.toSecondOfDay()
        val nowSecs    = now.toSecondOfDay()
        val elapsedMs  = (nowSecs - passedSecs).toLong() * 1_000L
        elapsedMs in 0..ELAPSED_WINDOW_MS
    }

    // True when all of today's prayers have passed AND the elapsed window is over.
    // Drives automatic next-day switch in both the header and the pager.
    val isPostDay: Boolean = remember(todayPrayers, inElapsedWindow, now) {
        todayPrayers.isNotEmpty() &&
                !inElapsedWindow &&
                todayPrayers.all { it.time <= now }
    }

    // Tomorrow's prayers — loaded lazily once post-day state is reached.
    var tomorrowPrayers by remember { mutableStateOf<List<PrayerTime>>(emptyList()) }

    LaunchedEffect(isPostDay, cityName) {
        if (!isPostDay) { tomorrowPrayers = emptyList(); return@LaunchedEffect }
        if (cityName.isBlank()) return@LaunchedEffect
        val tomorrow = today.plusDays(1)
        val cached   = prayerCache[tomorrow]
        if (cached != null) {
            tomorrowPrayers = cached
        } else {
            val fetched = repo.getForDate(tomorrow)
            prayerCache[tomorrow] = fetched
            tomorrowPrayers = fetched
        }
    }

    // Auto-advance pager to tomorrow when the day rolls into post-day state.
    LaunchedEffect(isPostDay) {
        if (isPostDay) pagerState.animateScrollToPage(PAGER_CENTER + 1)
    }

    // The prayer shown in the header:
    //  • Elapsed window  → the prayer that just passed.
    //  • Post-day        → tomorrow's Fajr (countdown to next day).
    //  • Otherwise       → the next upcoming prayer today.
    val headerPrayer: PrayerTime? = remember(
        todayPrayers, lastPassedIdx, inElapsedWindow, now, isPostDay, tomorrowPrayers,
    ) {
        when {
            todayPrayers.isEmpty() -> null
            inElapsedWindow        -> todayPrayers[lastPassedIdx!!]
            isPostDay              -> tomorrowPrayers.firstOrNull()
            else                   -> todayPrayers.firstOrNull { it.time > now }
        }
    }

    // Elapsed seconds since the last prayer (elapsed mode only).
    val elapsedSecs: Long = remember(todayPrayers, lastPassedIdx, inElapsedWindow, now) {
        if (!inElapsedWindow || lastPassedIdx == null) return@remember 0L
        val passedSecs = todayPrayers[lastPassedIdx].time.toSecondOfDay()
        (now.toSecondOfDay() - passedSecs).toLong().coerceAtLeast(0L)
    }

    // Remaining seconds until the next prayer (countdown mode only).
    val nextIdx: Int? = remember(todayPrayers, now) {
        todayPrayers.indexOfFirst { it.time > now }.takeIf { it >= 0 }
    }
    val countdownSecs: Long = remember(todayPrayers, nextIdx, now, isPostDay, tomorrowPrayers) {
        when {
            isPostDay && tomorrowPrayers.isNotEmpty() -> {
                // Seconds from now until tomorrow's Fajr = (secs left today) + (fajr secs into tomorrow).
                val secsUntilMidnight = (24 * 3600 - now.toSecondOfDay()).toLong()
                secsUntilMidnight + tomorrowPrayers.first().time.toSecondOfDay()
            }
            else -> nextIdx?.let { i ->
                val diff = todayPrayers[i].time.toSecondOfDay() - now.toSecondOfDay()
                if (diff >= 0L) diff.toLong() else 0L
            } ?: 0L
        }
    }

    // Alarm state per prayer index — Sunrise (index 1) is off by default.
    var alarmOn by remember { mutableStateOf(List(6) { i -> i != 1 }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding()),
    ) {
        PrayerHeader(
            prayer        = headerPrayer,
            inElapsedMode = inElapsedWindow,
            timerSecs     = if (inElapsedWindow) elapsedSecs else countdownSecs,
        )

        // ── Swipeable date navigator ──────────────────────────────────────────
        HorizontalPager(
            state            = pagerState,
            modifier         = Modifier.fillMaxWidth(),
            beyondViewportPageCount = 1,
        ) { page ->
            val pageDate  = today.plusDays((page - PAGER_CENTER).toLong())
            val isToday   = pageDate == today
            val isTomorrow = pageDate == today.plusDays(1)

            // Per-page prayer list state, seeded from cache when available.
            var prayers by remember { mutableStateOf(prayerCache[pageDate] ?: emptyList()) }

            // Which row to highlight:
            //  • Today, elapsed window  → prayer that just passed.
            //  • Today, normal          → last prayer whose time ≤ now.
            //  • Tomorrow, post-day     → Fajr (index 0) as the upcoming prayer.
            //  • Other pages            → no highlight.
            val currentIdx: Int? = remember(prayers, isToday, isTomorrow, isPostDay, headerPrayer) {
                if (prayers.isEmpty() || headerPrayer == null) return@remember null
                if (!isToday && !(isTomorrow && isPostDay)) return@remember null
                prayers.indexOfFirst { it.name == headerPrayer.name }.takeIf { it >= 0 }
            }

            LaunchedEffect(pageDate, cityName) {
                if (cityName.isNotBlank() && prayerCache[pageDate] == null) {
                    val fetched = repo.getForDate(pageDate)
                    prayerCache[pageDate] = fetched
                    prayers = fetched
                } else if (prayerCache[pageDate] != null) {
                    prayers = prayerCache[pageDate]!!
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                PrayerDateNav(
                    date    = pageDate,
                    isToday = isToday,
                    onPrev  = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext  = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (prayers.isEmpty()) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        prayers.forEachIndexed { i, prayer ->
                            PrayerRow(
                                prayer        = prayer,
                                isCurrent     = i == currentIdx,
                                alarmOn       = alarmOn.getOrElse(i) { false },
                                onAlarmToggle = {
                                    alarmOn = alarmOn.toMutableList().also { list -> list[i] = !list[i] }
                                },
                            )
                            if (i < prayers.size - 1) {
                                HorizontalDivider(
                                    modifier  = Modifier.padding(horizontal = 16.dp),
                                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                        // Space so the last row clears the bottom navigation bar.
                        Spacer(Modifier.height(padding.calculateBottomPadding()))
                    }
                }
            }
        }
    }
}

// ─── Green header ─────────────────────────────────────────────────────────────

/**
 * Countdown/elapsed header.
 *
 * [inElapsedMode] = true  → counting up (Since Fajr…), [timerSecs] = elapsed.
 * [inElapsedMode] = false → counting down (Till Dhuhr…), [timerSecs] = remaining.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayerHeader(
    prayer:        PrayerTime?,
    inElapsedMode: Boolean,
    timerSecs:     Long,
) {
    val h = (timerSecs / 3600).toInt()
    val m = ((timerSecs % 3600) / 60).toInt()
    val s = (timerSecs % 60).toInt()

    val onPrimary      = MaterialTheme.colorScheme.onSurface
    val onPrimaryMuted = onPrimary.copy(alpha = 0.75f)

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (prayer != null) {
                val localName = localizedPrayerName(prayer.name)
                val hasAdhan  = prayer.name.lowercase(Locale.ROOT) != "sunrise"
                val label = if (inElapsedMode) {
                    if (hasAdhan) stringResource(R.string.prayers_since, localName)
                    else stringResource(R.string.prayers_since_no_adhan, localName)
                } else {
                    if (hasAdhan) stringResource(R.string.prayers_till, localName)
                    else stringResource(R.string.prayers_till_no_adhan, localName)
                }

                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = onPrimaryMuted,
                )
                Spacer(Modifier.height(4.dp))
                // Force LTR so the HH:MM SS layout is never mirrored in RTL locales.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text     = "%02d:%02d".format(h, m),
                            modifier = Modifier.alignByBaseline(),
                            style    = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color    = onPrimary,
                        )
                        Text(
                            text     = " %02d".format(s),
                            modifier = Modifier.alignByBaseline(),
                            style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                            color    = onPrimary,
                        )
                    }
                }
            } else {
                // Location not set — reserve header height without showing stale data.
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─── Date navigation ─────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayerDateNav(
    date: LocalDate,
    isToday: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val hijriMonths = stringArrayResource(R.array.hijri_months)
    val hijriSuffix = stringResource(R.string.hijri_year_suffix)

    val hijri      = remember(date) { HijriDate.fromGregorian(date) }
    val monthName  = hijriMonths.getOrElse(hijri.month - 1) { "" }
    val hijriLabel = "${hijri.day} $monthName, ${hijri.year} $hijriSuffix"

    val gregFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    val gregDay       = date.format(gregFormatter)
    val gregLabel     = if (isToday) "${stringResource(R.string.prayers_today)} $gregDay" else gregDay

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.prayers_prev_day),
                tint               = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = gregLabel,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = hijriLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.prayers_next_day),
                tint               = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Prayer row ───────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayerRow(
    prayer: PrayerTime,
    isCurrent: Boolean,
    alarmOn: Boolean,
    onAlarmToggle: () -> Unit,
) {
    val bgColor   = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = localizedPrayerName(prayer.name),
            style    = MaterialTheme.typography.bodyLarge,
            color    = textColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text     = prayer.time.toAmPm(),
            style    = MaterialTheme.typography.bodyLarge,
            color    = textColor,
            modifier = Modifier.padding(end = 8.dp),
        )
        IconButton(
            onClick  = onAlarmToggle,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector        = if (alarmOn) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                contentDescription = stringResource(
                    if (alarmOn) R.string.prayers_alarm_on else R.string.prayers_alarm_off
                ),
                tint     = if (alarmOn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Maps the engine's English prayer name to the current locale's string resource. */
@Composable
private fun localizedPrayerName(name: String): String = when (name.lowercase(Locale.ROOT)) {
    "fajr"    -> stringResource(R.string.prayer_fajr)
    "sunrise" -> stringResource(R.string.prayer_sunrise)
    "dhuhr"   -> stringResource(R.string.prayer_dhuhr)
    "asr"     -> stringResource(R.string.prayer_asr)
    "maghrib" -> stringResource(R.string.prayer_maghrib)
    "isha"    -> stringResource(R.string.prayer_isha)
    else      -> name
}