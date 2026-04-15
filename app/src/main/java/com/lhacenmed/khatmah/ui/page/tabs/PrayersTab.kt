package com.lhacenmed.khatmah.ui.page.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.prayer.PrayerRepository
import com.lhacenmed.khatmah.data.prayer.PrayerTime
import com.lhacenmed.khatmah.data.prayer.toAmPm
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.util.HijriDate
import com.lhacenmed.khatmah.util.OnboardingPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─── Tab registration ─────────────────────────────────────────────────────────

val PrayersTab = NavScreen(
    route    = Route.PRAYERS,
    iconRes  = R.drawable.ic_mosque,
    labelRes = R.string.prayers,
) { padding ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PrayersScreenContent(padding)
    } else {
        // Prayer times use java.time APIs which require API 26+.
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.prayers_requires_android_8)) }
    }
}

// ─── Main screen ──────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayersScreenContent(padding: PaddingValues) {
    val context = LocalContext.current
    val nav     = LocalNavController.current
    val repo    = remember { PrayerRepository(context) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var prayers      by remember { mutableStateOf<List<PrayerTime>>(emptyList()) }
    var now          by remember { mutableStateOf(LocalTime.now()) }

    // Alarm state per prayer index — Sunrise (index 1) is off by default.
    var alarmOn by remember { mutableStateOf(List(6) { i -> i != 1 }) }

    val cityName = remember {
        OnboardingPrefs.location(context)?.cityName.orEmpty()
    }

    // Reload when the selected date changes.
    LaunchedEffect(selectedDate, cityName) {
        if (cityName.isNotBlank()) {
            prayers = repo.getForDate(selectedDate)
        }
    }

    // Tick every second for the countdown.
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalTime.now()
            delay(1_000L)
        }
    }

    val isToday = selectedDate == LocalDate.now()

    // Index of the last prayer whose time ≤ now (the "current" active prayer).
    val currentIdx: Int? = remember(prayers, now, isToday) {
        if (!isToday) null
        else prayers.indexOfLast { it.time <= now }.takeIf { it >= 0 }
    }

    // Index of the first prayer whose time > now (the upcoming prayer).
    val nextIdx: Int? = remember(prayers, now, isToday) {
        if (!isToday) null
        else prayers.indexOfFirst { it.time > now }.takeIf { it >= 0 }
    }

    // Remaining seconds until the next prayer.
    val countdownSecs: Long = remember(prayers, nextIdx, now) {
        nextIdx?.let { i ->
            val diff = prayers[i].time.toSecondOfDay() - now.toSecondOfDay()
            if (diff >= 0L) diff.toLong() else 0L
        } ?: 0L
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding()),
    ) {
        PrayerHeader(
            cityName        = cityName.ifBlank { stringResource(R.string.prayers_city_unknown) },
            nextPrayer      = nextIdx?.let { prayers[it] },
            countdownSecs   = countdownSecs,
            onQiblaClick    = { /* TODO: Qibla screen */ },
            // Navigate to the dedicated prayer settings page.
            onSettingsClick = { nav.navigate(Route.PRAYER_SETTINGS) },
        )

        PrayerDateNav(
            date    = selectedDate,
            isToday = isToday,
            onPrev  = { selectedDate = selectedDate.minusDays(1) },
            onNext  = { selectedDate = selectedDate.plusDays(1) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (prayers.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize(),
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

// ─── Green header ─────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PrayerHeader(
    cityName: String,
    nextPrayer: PrayerTime?,
    countdownSecs: Long,
    onQiblaClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val h = (countdownSecs / 3600).toInt()
    val m = ((countdownSecs % 3600) / 60).toInt()
    val s = (countdownSecs % 60).toInt()

    val onPrimary      = MaterialTheme.colorScheme.onPrimary
    val onPrimaryMuted = onPrimary.copy(alpha = 0.75f)

    Surface(color = MaterialTheme.colorScheme.primary) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Title row ─────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = stringResource(R.string.prayers_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = onPrimary,
                    )
                    if (cityName.isNotBlank()) {
                        Text(
                            text  = cityName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onPrimaryMuted,
                        )
                    }
                }
                IconButton(onClick = onQiblaClick) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_mosque),
                        contentDescription = stringResource(R.string.prayers_qibla),
                        tint               = onPrimary,
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector        = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.prayers_settings),
                        tint               = onPrimary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Countdown ─────────────────────────────────────────────────────
            if (nextPrayer != null) {
                val localName = localizedPrayerName(nextPrayer.name)
                Text(
                    text  = stringResource(R.string.prayers_till, localName),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = onPrimaryMuted,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment     = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text  = "%02d:%02d".format(h, m),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        color = onPrimary,
                    )
                    Text(
                        text     = " %02d".format(s),
                        style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                        color    = onPrimary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else {
                // After Isha — no remaining prayers today.
                Text(
                    text  = stringResource(R.string.prayers_next_fajr),
                    style = MaterialTheme.typography.titleMedium,
                    color = onPrimaryMuted,
                )
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