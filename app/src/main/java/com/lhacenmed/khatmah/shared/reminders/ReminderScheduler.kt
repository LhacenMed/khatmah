package com.lhacenmed.khatmah.shared.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.feature.prayer.data.PrayerTimetable
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Schedules or cancels exact AlarmManager alarms for every reminder type.
 *
 * Prayer alarmCodes 0-5 → pre-alert codes 10-15 (alarmCode + 10).
 * All other types use their alarmCode directly with no pre-alert.
 */
@RequiresApi(Build.VERSION_CODES.O)
object ReminderScheduler {

    fun scheduleAll(context: Context) = ReminderPrefs.getAll().forEach { schedule(context, it) }

    fun schedule(context: Context, config: ReminderConfig) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return

        if (!config.enabled) { cancelAll(context, am, config); return }

        when {
            config.type is ReminderType.Prayer -> schedulePrayer(context, am, config)
            isAnchoredAdhkar(config)            -> scheduleAdhkarAnchored(context, am, config)
            else                                -> scheduleFixed(context, am, config)
        }
    }

    /** Morning adhkar anchors to Fajr, evening to Maghrib — unless the user picked a custom time. */
    private fun isAnchoredAdhkar(config: ReminderConfig): Boolean {
        val t = config.type as? ReminderType.Adhkar ?: return false
        return !config.useCustomTime && (t.categoryId == "morning" || t.categoryId == "evening")
    }

    // ── Prayer (dynamic timing) ───────────────────────────────────────────────

    private fun schedulePrayer(context: Context, am: AlarmManager, config: ReminderConfig) {
        val prayerId = (config.type as ReminderType.Prayer).prayerId
        if (OnboardingPrefs.location(context) == null) { cancelAll(context, am, config); return }
        val prayerMs = nextPrayerMs(context, prayerId) ?: return

        setAlarm(context, am, prayerMs, config.alarmCode, mainIntent(context, config.id, prayerMs))

        val preCode = config.alarmCode + 10
        if (config.preAlertMinutes > 0) {
            val preMs = prayerMs - config.preAlertMinutes * 60_000L
            if (preMs > System.currentTimeMillis()) {
                setAlarm(context, am, preMs, preCode, preIntent(context, config.id))
            } else {
                cancelCode(context, am, preCode, preIntent(context, config.id))
            }
        } else {
            cancelCode(context, am, preCode, preIntent(context, config.id))
        }
    }

    private fun nextPrayerMs(context: Context, prayerId: Int): Long? {
        val zone  = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        for (offset in 0L..1L) {
            val date   = LocalDate.now().plusDays(offset)
            val prayer = PrayerTimetable.forDate(context, date).getOrNull(prayerId) ?: continue
            val ms     = ZonedDateTime.of(date, prayer.time, zone).toInstant().toEpochMilli()
            if (ms > nowMs) return ms
        }
        return null
    }

    // ── Adhkar (anchored to Fajr/Maghrib) ─────────────────────────────────────

    /** Fajr index in [PrayerTimetable.forDate]'s Fajr·Sunrise·Dhuhr·Asr·Maghrib·Isha order. */
    private const val FAJR_INDEX = 0

    /** Maghrib index in [PrayerTimetable.forDate]'s Fajr·Sunrise·Dhuhr·Asr·Maghrib·Isha order. */
    private const val MAGHRIB_INDEX = 4

    private fun scheduleAdhkarAnchored(context: Context, am: AlarmManager, config: ReminderConfig) {
        val categoryId = (config.type as ReminderType.Adhkar).categoryId
        if (OnboardingPrefs.location(context) == null) { cancelAll(context, am, config); return }

        val prayerId = if (categoryId == "morning") FAJR_INDEX else MAGHRIB_INDEX
        // Morning adhkar comes after Fajr, evening adhkar comes before Maghrib.
        val offsetMs = if (categoryId == "morning") config.anchorOffsetMinutes * 60_000L
                       else -config.anchorOffsetMinutes * 60_000L
        val ms = nextAdhkarMs(context, prayerId, offsetMs) ?: return

        setAlarm(context, am, ms, config.alarmCode, mainIntent(context, config.id, ms))
    }

    /** Like [nextPrayerMs], but offset by [offsetMs] from the prayer time before comparing to now. */
    private fun nextAdhkarMs(context: Context, prayerId: Int, offsetMs: Long): Long? {
        val zone  = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        for (offset in 0L..1L) {
            val date   = LocalDate.now().plusDays(offset)
            val prayer = PrayerTimetable.forDate(context, date).getOrNull(prayerId) ?: continue
            val ms     = ZonedDateTime.of(date, prayer.time, zone).toInstant().toEpochMilli() + offsetMs
            if (ms > nowMs) return ms
        }
        return null
    }

    // ── Fixed-time (adhkar, sunnah, khatmah, custom) ─────────────────────────

    private fun scheduleFixed(context: Context, am: AlarmManager, config: ReminderConfig) {
        val ms = nextOccurrenceMs(config.timeHour, config.timeMinute, config.repeatDayOfWeek)
        setAlarm(context, am, ms, config.alarmCode, mainIntent(context, config.id, ms))
    }

    /**
     * The next time a fixed-time reminder is due.
     *
     * A daily one is today at that time, or tomorrow once today's has gone by. A weekly one is the
     * next [dayOfWeek] on or after today, pushed a week on when that day's time has already passed
     * — so setting a Friday reminder on a Friday afternoon lands next Friday, not in a few minutes.
     */
    private fun nextOccurrenceMs(hour: Int, minute: Int, dayOfWeek: Int): Long {
        val zone    = ZoneId.systemDefault()
        val isDaily = dayOfWeek == ReminderConfig.REPEAT_DAILY
        val due     = ZonedDateTime.now(zone).with(LocalTime.of(hour, minute))
            .let { if (isDaily) it else it.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek))) }

        val ms = due.toInstant().toEpochMilli()
        if (ms > System.currentTimeMillis()) return ms
        return (if (isDaily) due.plusDays(1) else due.plusWeeks(1)).toInstant().toEpochMilli()
    }

    // ── AlarmManager helpers ──────────────────────────────────────────────────

    private fun setAlarm(context: Context, am: AlarmManager, triggerMs: Long, code: Int, intent: Intent) =
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs,
            PendingIntent.getBroadcast(context, code, intent, piFlags()))

    /** Drops every alarm [config] holds — used before a reminder the user deleted is forgotten. */
    fun cancel(context: Context, config: ReminderConfig) =
        cancelAll(context, context.getSystemService(Context.ALARM_SERVICE) as AlarmManager, config)

    private fun cancelAll(context: Context, am: AlarmManager, config: ReminderConfig) {
        cancelCode(context, am, config.alarmCode, mainIntent(context, config.id, 0L))
        if (config.type is ReminderType.Prayer)
            cancelCode(context, am, config.alarmCode + 10, preIntent(context, config.id))
    }

    private fun cancelCode(context: Context, am: AlarmManager, code: Int, intent: Intent) =
        am.cancel(PendingIntent.getBroadcast(context, code, intent, piFlags()))

    internal fun mainIntent(context: Context, id: String, triggerMs: Long) =
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID,      id)
            putExtra(ReminderReceiver.EXTRA_IS_PRE,  false)
            putExtra(ReminderReceiver.EXTRA_TIME_MS, triggerMs)
        }

    private fun preIntent(context: Context, id: String) =
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID,     id)
            putExtra(ReminderReceiver.EXTRA_IS_PRE, true)
        }

    private fun piFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}