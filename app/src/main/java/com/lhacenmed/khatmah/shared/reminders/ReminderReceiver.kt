package com.lhacenmed.khatmah.shared.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings

/**
 * Central broadcast receiver for all scheduled reminders.
 * Posts the appropriate notification then re-arms the next occurrence.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID      = "reminder_id"
        const val EXTRA_IS_PRE  = "is_pre"
        const val EXTRA_TIME_MS = "trigger_time_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id     = intent.getStringExtra(EXTRA_ID)            ?: return
        val isPre  = intent.getBooleanExtra(EXTRA_IS_PRE, false)
        val timeMs = intent.getLongExtra(EXTRA_TIME_MS, System.currentTimeMillis())

        // Ensure singletons loaded — process may be cold-started by the alarm.
        ReminderPrefs.init(context)
        PrayerSettings.init(context)

        val config = ReminderPrefs.getById(id) ?: return
        if (!config.enabled) return

        when (config.type) {
            is ReminderType.Prayer -> handlePrayer(context, config, isPre, timeMs)
            else                   -> handleFixed(context, config)
        }

        ReminderScheduler.schedule(context, config)
    }

    // ── Prayer ────────────────────────────────────────────────────────────────

    private fun handlePrayer(context: Context, config: ReminderConfig, isPre: Boolean, timeMs: Long) {
        val name = prayerName(context, (config.type as ReminderType.Prayer).prayerId)
        if (isPre) {
            if (config.preAlertMinutes > 0) ReminderNotifier.postPreAlert(context, config, name)
        } else {
            ReminderNotifier.postPrayer(context, config, name, timeMs)
        }
    }

    // ── Fixed-time ────────────────────────────────────────────────────────────

    private fun handleFixed(context: Context, config: ReminderConfig) {
        val (title, body) = labelFor(context, config)
        ReminderNotifier.postReminder(context, config, title, body)
    }

    // ── Label resolution ──────────────────────────────────────────────────────

    private fun prayerName(context: Context, prayerId: Int): String {
        val ids = intArrayOf(
            R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
            R.string.prayer_asr,  R.string.prayer_maghrib, R.string.prayer_isha,
        )
        return context.getString(ids.getOrElse(prayerId) { R.string.prayer_fajr })
    }

    private fun labelFor(context: Context, config: ReminderConfig): Pair<String, String> {
        val appName = context.getString(R.string.app_name)
        return when (val t = config.type) {
            is ReminderType.Adhkar -> {
                val name = adhkarName(context, t.categoryId)
                name to name
            }
            is ReminderType.QuranSunnah -> {
                val name = sunnahName(context, t.surahKey)
                name to name
            }
            is ReminderType.DailyKhatmah ->
                context.getString(R.string.today_khatmah_title) to appName
            is ReminderType.Custom -> appName to appName
            is ReminderType.Prayer -> "" to ""  // unreachable; handled by handlePrayer
        }
    }

    private fun adhkarName(context: Context, categoryId: String): String {
        val res = when (categoryId) {
            "morning"      -> R.string.adhkar_morning
            "evening"      -> R.string.adhkar_evening
            "sleep"        -> R.string.adhkar_sleep
            "after_prayer" -> R.string.adhkar_after_prayer
            "wakeup"       -> R.string.adhkar_wakeup
            "mosque"       -> R.string.adhkar_mosque
            else           -> 0
        }
        return if (res != 0) context.getString(res) else context.getString(R.string.adhkar)
    }

    private fun sunnahName(context: Context, surahKey: String): String {
        val res = when (surahKey) {
            "al_kahf"    -> R.string.more_surat_kahf
            "al_mulk"    -> R.string.more_surat_mulk
            "al_baqarah" -> R.string.more_surat_baqarah
            else         -> 0
        }
        return if (res != 0) context.getString(res) else context.getString(R.string.app_name)
    }
}