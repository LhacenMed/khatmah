package com.lhacenmed.khatmah.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.AdhanSound
import com.lhacenmed.khatmah.data.prayer.PrayerEngine
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.util.OnboardingPrefs
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules (or cancels) exact [AlarmManager] alarms for prayer notifications.
 *
 * Design:
 *  - One alarm per prayer for the main adhan.
 *  - One alarm per prayer for the pre-alert (when preAlertMinutes > 0).
 *  - Request codes: main = prayerId (0-5), pre = prayerId + 10 (10-15).
 *  - All alarms are RTC_WAKEUP + setExactAndAllowWhileIdle for reliability.
 *  - Adding a new sound file requires no changes here — it's just an [AdhanSound.Asset].
 */
@RequiresApi(Build.VERSION_CODES.O)
object AdhanScheduler {

    /** Schedule / refresh alarms for all 6 prayers. */
    fun scheduleAll(context: Context) {
        for (id in 0 until AdhanPrefs.COUNT) schedulePrayer(context, id)
    }

    /** Schedule / refresh alarms for one prayer (cancels if disabled). */
    fun schedulePrayer(context: Context, prayerId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return

        val config = AdhanPrefs.getFor(prayerId)
        val loc    = OnboardingPrefs.location(context)

        // Cancel both alarms if prayer is off or location unknown.
        if (!config.isEnabled || loc == null) {
            cancelAlarm(context, am, prayerId, isPre = false)
            cancelAlarm(context, am, prayerId, isPre = true)
            return
        }

        val settings = PrayerSettings.get().resolve(loc.countryCode)
        val prayerTime = nextPrayerEpochMs(context, prayerId, loc.lat, loc.lng, settings) ?: return

        val prayerName = prayerNameFor(context, prayerId)

        // Main adhan alarm
        setAlarm(context, am, prayerTime, prayerId, prayerName, isPre = false)

        // Pre-alert alarm
        if (config.preAlertMinutes > 0) {
            val preMs = prayerTime - config.preAlertMinutes * 60_000L
            if (preMs > System.currentTimeMillis()) {
                setAlarm(context, am, preMs, prayerId, prayerName, isPre = true)
            } else {
                cancelAlarm(context, am, prayerId, isPre = true)
            }
        } else {
            cancelAlarm(context, am, prayerId, isPre = true)
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun nextPrayerEpochMs(
        context:   Context,
        prayerId:  Int,
        lat:       Double,
        lng:       Double,
        settings:  com.lhacenmed.khatmah.data.prayer.PrayerCalcSettings,
    ): Long? {
        val zone  = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()

        // Try today, then tomorrow (if today's prayer has already passed).
        for (dayOffset in 0L..1L) {
            val date    = LocalDate.now().plusDays(dayOffset)
            val prayers = runCatching {
                PrayerEngine.calculate(lat, lng, date, settings)
            }.getOrDefault(emptyList())
            val prayer = prayers.getOrNull(prayerId) ?: continue
            val epochMs = ZonedDateTime.of(date, prayer.time, zone).toInstant().toEpochMilli()
            if (epochMs > nowMs) return epochMs
        }
        return null
    }

    private fun setAlarm(
        context:    Context,
        am:         AlarmManager,
        triggerMs:  Long,
        prayerId:   Int,
        prayerName: String,
        isPre:      Boolean,
    ) {
        val pi = buildPendingIntent(context, prayerId, prayerName, isPre)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    private fun cancelAlarm(context: Context, am: AlarmManager, prayerId: Int, isPre: Boolean) {
        am.cancel(buildPendingIntent(context, prayerId, "", isPre))
    }

    private fun buildPendingIntent(
        context:    Context,
        prayerId:   Int,
        prayerName: String,
        isPre:      Boolean,
    ): PendingIntent {
        val requestCode = if (isPre) prayerId + 10 else prayerId
        val intent = Intent(context, AdhanReceiver::class.java).apply {
            putExtra(AdhanReceiver.EXTRA_PRAYER_ID,   prayerId)
            putExtra(AdhanReceiver.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AdhanReceiver.EXTRA_IS_PRE,      isPre)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun prayerNameFor(context: Context, prayerId: Int): String {
        val ids = intArrayOf(
            com.lhacenmed.khatmah.R.string.prayer_fajr,
            com.lhacenmed.khatmah.R.string.prayer_sunrise,
            com.lhacenmed.khatmah.R.string.prayer_dhuhr,
            com.lhacenmed.khatmah.R.string.prayer_asr,
            com.lhacenmed.khatmah.R.string.prayer_maghrib,
            com.lhacenmed.khatmah.R.string.prayer_isha,
        )
        return context.getString(ids.getOrElse(prayerId) { ids[0] })
    }
}