package com.lhacenmed.khatmah.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import com.lhacenmed.khatmah.feature.prayer.data.CustomTimesPrefs
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTime
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTimetable
import com.lhacenmed.khatmah.shared.util.LocaleManager
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
     * How the widget is painted and worded, resolved once per render.
     *
     * [palette] is the app's own, [strings] a context carrying the app's language — a widget is
     * drawn outside any Activity, so the per-app locale has to be applied by hand — and [isRtl]
     * the direction that language reads in, which decides which side each panel takes.
     */
    private data class Style(
        val palette: WidgetPalette,
        val strings: Context,
        val isRtl:   Boolean,
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
        CustomTimesPrefs.init(context)

        val zone      = ZoneId.systemDefault()
        val now       = LocalTime.now()
        val today     = LocalDate.now()
        val nowMs     = System.currentTimeMillis()

        // Always resolve today's prayers first — needed for alarm scheduling.
        val todayPrayers = PrayerTimetable.forDate(context, today)

        // Post-day: all of today's prayers have passed AND Isha's 30-min window is over.
        // Switch to tomorrow's prayer list and count down to tomorrow's Fajr.
        val isPostDay = todayPrayers.isNotEmpty() && run {
            val lastPrayer = todayPrayers.last()
            val passedMs   = ZonedDateTime.of(today, lastPrayer.time, zone).toInstant().toEpochMilli()
            (nowMs - passedMs) > ELAPSED_WINDOW_MS
        }

        val (displayPrayers, countdown) = when {
            // isPostDay already implies a list, and a list implies somewhere to compute for.
            isPostDay -> {
                val tomorrow   = today.plusDays(1)
                val tmrPrayers = PrayerTimetable.forDate(context, tomorrow)
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

        val style = resolveStyle(context)
        provideContent {
            Content(displayPrayers, countdown, style)
        }
    }

    /** Reads the app's palette and language once, so every panel is drawn from the same answer. */
    private fun resolveStyle(context: Context): Style {
        val strings = LocaleManager.applyTo(context)
        val locale  = strings.resources.configuration.locales[0]
        return Style(
            palette = widgetPalette(context),
            strings = strings,
            isRtl   = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL,
        )
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
    private fun Content(prayers: List<PrayerTime>, countdown: Countdown?, style: Style) {
        val context = LocalContext.current

        val openPrayersAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                action = WidgetAction.OPEN_PRAYERS
                flags  = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        val surface = GlanceModifier.fillMaxSize()
            .cornerRadius(CORNER_RADIUS)
            .background(style.palette.surface.asColorProvider())
            .clickable(openPrayersAction)

        if (prayers.isEmpty() || countdown == null) {
            Box(modifier = surface, contentAlignment = Alignment.Center) {
                Text(
                    text  = style.strings.getString(R.string.widget_no_location),
                    style = TextStyle(
                        color    = style.palette.onSurface.asColorProvider(),
                        fontSize = 30.sp,
                    ),
                )
            }
            return
        }

        Row(modifier = surface, verticalAlignment = Alignment.CenterVertically) {
            val side = GlanceModifier.defaultWeight().fillMaxHeight()
            if (style.isRtl) {
                PrayerList(prayers, countdown.prayer.name, style, side)
                CountdownPanel(countdown, style, side)
            } else {
                CountdownPanel(countdown, style, side)
                PrayerList(prayers, countdown.prayer.name, style, side)
            }
        }
    }

    // ── Countdown panel ───────────────────────────────────────────────────────

    @Composable
    private fun CountdownPanel(countdown: Countdown, style: Style, modifier: GlanceModifier) {
        val context = LocalContext.current
        val bgRes   = if (style.isRtl) R.drawable.widget_countdown_rtl_bg
        else       R.drawable.widget_countdown_ltr_bg

        val prayerLabel = prayerName(countdown.prayer.name, style.strings)
        val label = style.strings.getString(
            if (countdown is Countdown.ElapsedSince) R.string.widget_since else R.string.widget_till,
            prayerLabel,
        )

        // ElapsedSince: Chronometer counts up from (now - elapsed).
        // CountingDown: Chronometer counts down from (now + remaining).
        val (chronometerBase, countingDown) = when (countdown) {
            is Countdown.ElapsedSince  ->
                SystemClock.elapsedRealtime() - countdown.msElapsed to false
            is Countdown.CountingDown ->
                SystemClock.elapsedRealtime() + countdown.msRemaining to true
        }

        val palette = style.palette

        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                // Rounded shape + tint on API 31+, where both variants ride along; flat fill below.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInt(R.id.countdown_root, "setBackgroundResource", bgRes)
                    setColorStateList(
                        R.id.countdown_root, "setBackgroundTintList",
                        ColorStateList.valueOf(palette.panel.day),
                        ColorStateList.valueOf(palette.panel.night),
                    )
                } else {
                    setDayNightColor(R.id.countdown_root, "setBackgroundColor", palette.panel, palette.night)
                }
                setImageViewResource(R.id.prayer_icon, prayerIcon(countdown.prayer.name))
                setDayNightColor(R.id.prayer_icon, "setColorFilter", palette.onPanel, palette.night)
                setTextViewText(R.id.prayer_label, label)
                setDayNightColor(R.id.prayer_label, "setTextColor", palette.onPanel, palette.night)
                setChronometer(R.id.chrono, chronometerBase, null, true)
                setChronometerCountDown(R.id.chrono, countingDown)
                setDayNightColor(R.id.chrono, "setTextColor", palette.onPanel, palette.night)
            },
        )
    }

    // ── Prayer list panel ─────────────────────────────────────────────────────

    @Composable
    private fun PrayerList(
        prayers:       List<PrayerTime>,
        highlightName: String,
        style:         Style,
        modifier:      GlanceModifier,
    ) {
        val context = LocalContext.current
        val palette = style.palette
        AndroidRemoteViews(
            modifier    = modifier,
            remoteViews = RemoteViews(context.packageName, R.layout.widget_prayer_list).apply {
                prayers.forEachIndexed { i, prayer ->
                    val isHighlight = prayer.name == highlightName
                    val color       = if (isHighlight) palette.accent else palette.onSurface
                    val nameText    = prayerName(prayer.name, style.strings)
                    val timeText    = prayer.time.format(TIME_FMT)

                    val leftText  = if (style.isRtl) timeText else nameText
                    val rightText = if (style.isRtl) nameText else timeText

                    setTextViewText(LEFT_IDS[i],  if (isHighlight) boldOf(leftText)  else leftText)
                    setTextViewText(RIGHT_IDS[i], if (isHighlight) boldOf(rightText) else rightText)
                    setDayNightColor(LEFT_IDS[i],  "setTextColor", color, palette.night)
                    setDayNightColor(RIGHT_IDS[i], "setTextColor", color, palette.night)
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
     * The prayer's name in the app's language. [strings] carries that language (see [Style]), so
     * the name follows the app rather than the device — the two need not agree.
     */
    private fun prayerName(name: String, strings: Context): String = when (name.lowercase()) {
        "fajr"    -> strings.getString(R.string.prayer_fajr)
        "sunrise" -> strings.getString(R.string.prayer_sunrise)
        "dhuhr"   -> strings.getString(R.string.prayer_dhuhr)
        "asr"     -> strings.getString(R.string.prayer_asr)
        "maghrib" -> strings.getString(R.string.prayer_maghrib)
        "isha"    -> strings.getString(R.string.prayer_isha)
        else      -> name
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