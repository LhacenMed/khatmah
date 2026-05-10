package com.lhacenmed.khatmah.shared.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.feature.prayer.data.PrayerCalcSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerEngine
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

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

        when (config.type) {
            is ReminderType.Prayer -> schedulePrayer(context, am, config)
            else                   -> scheduleFixed(context, am, config)
        }
    }

    // ── Prayer (dynamic timing) ───────────────────────────────────────────────

    private fun schedulePrayer(context: Context, am: AlarmManager, config: ReminderConfig) {
        val prayerId = (config.type as ReminderType.Prayer).prayerId
        val loc      = OnboardingPrefs.location(context) ?: run { cancelAll(context, am, config); return }
        val settings = PrayerSettings.get().resolve(loc.countryCode)
        val prayerMs = nextPrayerMs(prayerId, loc.lat, loc.lng, settings) ?: return

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

    private fun nextPrayerMs(
        prayerId: Int, lat: Double, lng: Double, settings: PrayerCalcSettings,
    ): Long? {
        val zone  = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        for (offset in 0L..1L) {
            val date    = LocalDate.now().plusDays(offset)
            val prayers = runCatching { PrayerEngine.calculate(lat, lng, date, settings) }
                .getOrDefault(emptyList())
            val prayer  = prayers.getOrNull(prayerId) ?: continue
            val ms      = ZonedDateTime.of(date, prayer.time, zone).toInstant().toEpochMilli()
            if (ms > nowMs) return ms
        }
        return null
    }

    // ── Fixed-time (adhkar, sunnah, khatmah, custom) ─────────────────────────

    private fun scheduleFixed(context: Context, am: AlarmManager, config: ReminderConfig) {
        val ms = nextOccurrenceMs(config.timeHour, config.timeMinute)
        setAlarm(context, am, ms, config.alarmCode, mainIntent(context, config.id, ms))
    }

    private fun nextOccurrenceMs(hour: Int, minute: Int): Long {
        val zone  = ZoneId.systemDefault()
        val today = ZonedDateTime.now(zone).with(LocalTime.of(hour, minute))
        val ms    = today.toInstant().toEpochMilli()
        return if (ms > System.currentTimeMillis()) ms else today.plusDays(1).toInstant().toEpochMilli()
    }

    // ── AlarmManager helpers ──────────────────────────────────────────────────

    private fun setAlarm(context: Context, am: AlarmManager, triggerMs: Long, code: Int, intent: Intent) =
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs,
            PendingIntent.getBroadcast(context, code, intent, piFlags()))

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