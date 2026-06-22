package com.lhacenmed.khatmah.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lhacenmed.khatmah.core.MainActivity
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.prayer.data.PrayerEngine
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTime
import com.lhacenmed.khatmah.shared.util.LocaleManager
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("RestrictedApi")
class PrayerWidget : GlanceAppWidget() {

    /**
     * Describes the countdown panel state:
     *
     * [CountingDown] — next prayer hasn't arrived yet; Chronometer counts down.
     * [ElapsedSince] — a prayer passed within [ELAPSED_WINDOW_MS]; Chronometer counts up.
     *                  That prayer stays highlighted in the list for 30 minutes.
     */
    private sealed class Countdown {
        abstract val prayer: PrayerTime
        data class CountingDown(override val prayer: PrayerTime, val msRemaining: Long) : Countdown()
        data class ElapsedSince(override val prayer: PrayerTime, val msElapsed: Long)   : Countdown()
    }

    /**
     * Dynamic Material 3 colors (ARGB) resolved once per render and fed to the
     * RemoteViews panels — the only layer [GlanceTheme.colors] cannot reach.
     *
     * [panel]/[onPanel]     → countdown panel surface + its text/icon (primaryContainer)
     * [onSurface]/[accent]  → prayer-list text, normal + highlighted (onSecondaryContainer / primary)
     */
    private data class WidgetColors(
        val panel:     Int,
        val onPanel:   Int,
        val onSurface: Int,
        val accent:    Int,
    )

    companion object {
        /** How long (ms) to show "Since / مضى على" before switching to the next prayer. */
        private const val ELAPSED_WINDOW_MS = 30 * 60 * 1000L

        /** Widget corner radius — mirrors the rounded countdown shapes; effective on API 31+. */
        private val CORNER_RADIUS = 15.dp

        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        private val ROW_IDS   = intArrayOf(
            R.id.prayer_row_0,   R.id.prayer_row_1,   R.id.prayer_row_2,
            R.id.prayer_row_3,   R.id.prayer_row_4,   R.id.prayer_row_5,
        )
        private val LEFT_IDS  = intArrayOf(
            R.id.prayer_left_0,  R.id.prayer_left_1,  R.id.prayer_left_2,
            R.id.prayer_left_3,  R.id.prayer_left_4,  R.id.prayer_left_5,
        )
        private val RIGHT_IDS = intArrayOf(
            R.id.prayer_right_0, R.id.prayer_right_1, R.id.prayer_right_2,
            R.id.prayer_right_3, R.id.prayer_right_4, R.id.prayer_right_5,
        )
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PrayerSettings.init(context)

        val location  = OnboardingPrefs.location(context)
        val zone      = ZoneId.systemDefault()
        val now       = LocalTime.now()
        val today     = LocalDate.now()
        val nowMs     = System.currentTimeMillis()

        // Always calculate today's prayers first — needed for alarm scheduling.
        val todayPrayers: List<PrayerTime> = location?.let { loc ->
            runCatching {
                PrayerEngine.calculate(
                    loc.lat, loc.lng, today,
                    PrayerSettings.get().resolve(loc.countryCode),
                )
            }.getOrDefault(emptyList())
        } ?: emptyList()

        // Post-day: all of today's prayers have passed AND Isha's 30-min window is over.
        // Switch to tomorrow's prayer list and count down to tomorrow's Fajr.
        val isPostDay = todayPrayers.isNotEmpty() && run {
            val lastPrayer = todayPrayers.last()
            val passedMs   = ZonedDateTime.of(today, lastPrayer.time, zone).toInstant().toEpochMilli()
            (nowMs - passedMs) > ELAPSED_WINDOW_MS
        }

        val (displayPrayers, countdown) = when {
            isPostDay && location != null -> {
                val tomorrow     = today.plusDays(1)
                val tmrPrayers   = runCatching {
                    PrayerEngine.calculate(
                        location.lat, location.lng, tomorrow,
                        PrayerSettings.get().resolve(location.countryCode),
                    )
                }.getOrDefault(emptyList())
                val fajr = tmrPrayers.firstOrNull()
                val cd   = fajr?.let {
                    val fajrMs = ZonedDateTime.of(tomorrow, it.time, zone).toInstant().toEpochMilli()
                    Countdown.CountingDown(it, (fajrMs - nowMs).coerceAtLeast(0L))
                }
                tmrPrayers to cd
            }
            todayPrayers.isNotEmpty() -> {
                val cd = resolveCountdown(todayPrayers, now, today)
                todayPrayers to cd
            }
            else -> emptyList<PrayerTime>() to null
        }

        // Alarm scheduling always uses today's list — the logic already handles post-day
        // by wrapping Fajr's epoch to tomorrow when all of today's prayers are in the past.
        if (todayPrayers.isNotEmpty()) scheduleNextAlarm(context, todayPrayers)

        provideContent {
            GlanceTheme { Content(displayPrayers, countdown) }
        }
    }

    // ── Alarm scheduling ──────────────────────────────────────────────────────

    /**
     * Schedules an exact alarm at the next transition point:
     *  - If inside the 30-min elapsed window → alarm at window end (prayer + 30 min).
     *  - Otherwise → alarm at the next prayer time.
     *
     * Falls back gracefully on API 31-32 if SCHEDULE_EXACT_ALARM hasn't been granted.
     */
    internal fun scheduleNextAlarm(context: Context, prayers: List<PrayerTime>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return

        val zone      = ZoneId.systemDefault()
        val now       = LocalTime.now()
        val today     = LocalDate.now()
        val nowMs     = System.currentTimeMillis()
        val lastPassed = prayers.lastOrNull { !it.time.isAfter(now) }

        val triggerMs: Long = if (lastPassed != null) {
            val passedMs  = ZonedDateTime.of(today, lastPassed.time, zone).toInstant().toEpochMilli()
            val windowEnd = passedMs + ELAPSED_WINDOW_MS
            if (windowEnd > nowMs) windowEnd          // still in elapsed window
            else nextPrayerEpochMs(prayers, now, today, zone, nowMs)
        } else {
            nextPrayerEpochMs(prayers, now, today, zone, nowMs)
        }

        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, PrayerAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    private fun nextPrayerEpochMs(
        prayers: List<PrayerTime>,
        now:     LocalTime,
        today:   LocalDate,
        zone:    ZoneId,
        nowMs:   Long,
    ): Long {
        val next = prayers.firstOrNull { it.time.isAfter(now) } ?: prayers.first()
        var ms   = ZonedDateTime.of(today, next.time, zone).toInstant().toEpochMilli()
        if (ms <= nowMs) ms += 86_400_000L
        return ms
    }

    // ── Countdown resolution ──────────────────────────────────────────────────

    /**
     * Determines whether to show elapsed or remaining time:
     *
     * - If a prayer passed within [ELAPSED_WINDOW_MS] → [Countdown.ElapsedSince] with
     *   that prayer highlighted and the Chronometer counting up.
     * - Otherwise → [Countdown.CountingDown] to the next prayer.
     *
     * Only called for the current day; post-day countdown is built directly in [provideGlance].
     */
    private fun resolveCountdown(prayers: List<PrayerTime>, now: LocalTime, today: LocalDate): Countdown {
        val zone  = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()

        val lastPassed = prayers.lastOrNull { !it.time.isAfter(now) }
        if (lastPassed != null) {
            val passedMs = ZonedDateTime.of(today, lastPassed.time, zone).toInstant().toEpochMilli()
            val elapsed  = nowMs - passedMs
            if (elapsed in 0..ELAPSED_WINDOW_MS) {
                return Countdown.ElapsedSince(lastPassed, elapsed)
            }
        }

        val next = prayers.firstOrNull { it.time.isAfter(now) } ?: prayers.first()
        var triggerMs = ZonedDateTime.of(today, next.time, zone).toInstant().toEpochMilli()
        if (triggerMs <= nowMs) triggerMs += 86_400_000L
        return Countdown.CountingDown(next, (triggerMs - nowMs).coerceAtLeast(0L))
    }

    // ── Root layout ───────────────────────────────────────────────────────────

    @Composable
    private fun Content(prayers: List<PrayerTime>, countdown: Countdown?) {
        val context = LocalContext.current

        val openPrayersAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetAction.OPEN_PRAYERS
                flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        if (prayers.isEmpty() || countdown == null) {
            Box(
                modifier         = GlanceModifier.fillMaxSize()
                    .cornerRadius(CORNER_RADIUS)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(openPrayersAction),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Open the app to set your location",
                    style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 30.sp),
                )
            }
            return
        }

        val isRtl = LocaleManager.savedTag(context)?.startsWith("ar") == true

        Row(
            modifier          = GlanceModifier.fillMaxSize()
                .cornerRadius(CORNER_RADIUS)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(openPrayersAction),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val side = GlanceModifier.defaultWeight().fillMaxHeight()
            if (isRtl) {
                PrayerList(prayers, countdown.prayer.name, isRtl, side)
                CountdownPanel(countdown, side)
            } else {
                CountdownPanel(countdown, side)
                PrayerList(prayers, countdown.prayer.name, isRtl, side)
            }
        }
    }

    // ── Countdown panel ───────────────────────────────────────────────────────

    @Composable
    private fun CountdownPanel(countdown: Countdown, modifier: GlanceModifier) {
        val context = LocalContext.current
        val isRtl   = LocaleManager.savedTag(context)?.startsWith("ar") == true
        val bgRes   = if (isRtl) R.drawable.widget_countdown_rtl_bg
        else       R.drawable.widget_countdown_ltr_bg

        val prayerLabel = prayerName(countdown.prayer.name, context, isRtl)
        val label = when {
            countdown is Countdown.ElapsedSince && isRtl  -> "مضى على $prayerLabel"
            countdown is Countdown.ElapsedSince            -> "Since $prayerLabel"
            isRtl                                          -> "باقي على $prayerLabel"
            else                                           -> "Till $prayerLabel"
        }

        // ElapsedSince: Chronometer counts up from (now - elapsed).
        // CountingDown: Chronometer counts down from (now + remaining).
        val (chronometerBase, countingDown) = when (countdown) {
            is Countdown.ElapsedSince  ->
                SystemClock.elapsedRealtime() - countdown.msElapsed to false
            is Countdown.CountingDown ->
                SystemClock.elapsedRealtime() + countdown.msRemaining to true
        }

        val colors = resolveColors(context)

        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                // Rounded shape + dynamic tint on API 31+; flat dynamic fill below.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInt(R.id.countdown_root, "setBackgroundResource", bgRes)
                    setColorStateList(
                        R.id.countdown_root, "setBackgroundTintList",
                        ColorStateList.valueOf(colors.panel),
                    )
                } else {
                    setInt(R.id.countdown_root, "setBackgroundColor", colors.panel)
                }
                setImageViewResource(R.id.prayer_icon, prayerIcon(countdown.prayer.name))
                setInt(R.id.prayer_icon, "setColorFilter", colors.onPanel)
                setTextViewText(R.id.prayer_label, label)
                setTextColor(R.id.prayer_label, colors.onPanel)
                setChronometer(R.id.chrono, chronometerBase, null, true)
                setChronometerCountDown(R.id.chrono, countingDown)
                setTextColor(R.id.chrono, colors.onPanel)
            },
        )
    }

    // ── Prayer list panel ─────────────────────────────────────────────────────

    @Composable
    private fun PrayerList(
        prayers:       List<PrayerTime>,
        highlightName: String,
        isRtl:         Boolean,
        modifier:      GlanceModifier,
    ) {
        val context = LocalContext.current
        val colors  = resolveColors(context)
        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_prayer_list).apply {
                prayers.forEachIndexed { i, prayer ->
                    val isHighlight = prayer.name == highlightName
                    val color       = if (isHighlight) colors.accent else colors.onSurface
                    val nameText    = prayerName(prayer.name, context, isRtl)
                    val timeText    = prayer.time.format(TIME_FMT)

                    val leftText  = if (isRtl) timeText else nameText
                    val rightText = if (isRtl) nameText else timeText

                    setTextViewText(LEFT_IDS[i],  if (isHighlight) boldOf(leftText)  else leftText)
                    setTextViewText(RIGHT_IDS[i], if (isHighlight) boldOf(rightText) else rightText)
                    setTextColor(LEFT_IDS[i],  color)
                    setTextColor(RIGHT_IDS[i], color)
                    setViewVisibility(ROW_IDS[i], View.VISIBLE)
                }
                for (i in prayers.size until ROW_IDS.size) {
                    setViewVisibility(ROW_IDS[i], View.GONE)
                }
            },
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the dynamic Material 3 scheme exactly the way [GlanceTheme] does:
     * device dynamic colors on API 31+, the Material baseline below — honoring the
     * current night-mode setting. Keeps the RemoteViews panels in lock-step with the
     * Glance composables, which read [GlanceTheme.colors].
     */
    private fun resolveColors(context: Context): WidgetColors {
        val night = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (night) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (night) darkColorScheme() else lightColorScheme()
        }

        return WidgetColors(
            panel     = scheme.primaryContainer.toArgb(),
            onPanel   = scheme.onPrimaryContainer.toArgb(),
            onSurface = scheme.onSecondaryContainer.toArgb(),
            accent    = scheme.primary.toArgb(),
        )
    }

    /**
     * Returns the localized prayer name.
     * Uses the string resource (Arabic when [isRtl], English otherwise) so the
     * result is correct regardless of which process is running the widget.
     */
    private fun prayerName(name: String, context: Context, isRtl: Boolean): String {
        if (!isRtl) return name
        return when (name.lowercase()) {
            "fajr"    -> context.getString(R.string.prayer_fajr)
            "sunrise" -> context.getString(R.string.prayer_sunrise)
            "dhuhr"   -> context.getString(R.string.prayer_dhuhr)
            "asr"     -> context.getString(R.string.prayer_asr)
            "maghrib" -> context.getString(R.string.prayer_maghrib)
            "isha"    -> context.getString(R.string.prayer_isha)
            else      -> name
        }
    }

    private fun boldOf(text: String): SpannableString =
        SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

    private fun prayerIcon(name: String): Int = when (name.lowercase()) {
        "fajr"    -> R.drawable.ic_fajr
        "sunrise" -> R.drawable.ic_sunrise
        "dhuhr"   -> R.drawable.ic_dhuhr
        "asr"     -> R.drawable.ic_asr
        "maghrib" -> R.drawable.ic_maghrib
        "isha"    -> R.drawable.ic_isha
        else      -> R.drawable.ic_dhuhr
    }
}