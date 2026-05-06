package com.lhacenmed.khatmah.feature.prayer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings

/**
 * Receives exact-alarm broadcasts for prayer notifications.
 *
 * Extras:
 *   EXTRA_PRAYER_ID   — 0-5 index matching [AdhanPrefs] constants.
 *   EXTRA_PRAYER_NAME — localized prayer name for the notification text.
 *   EXTRA_IS_PRE      — true = pre-alert; false = main adhan notification.
 *
 * After posting, re-arms the next occurrence via [AdhanScheduler] to keep
 * the chain alive across days and after reboots.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AdhanReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PRAYER_ID   = "prayer_id"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_IS_PRE      = "is_pre"
        const val EXTRA_PRAYER_TIME_MS = "prayer_time_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerId   = intent.getIntExtra(EXTRA_PRAYER_ID, -1).takeIf { it in 0..5 } ?: return
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val isPre      = intent.getBooleanExtra(EXTRA_IS_PRE, false)
        val prayerTimeMs = intent.getLongExtra(EXTRA_PRAYER_TIME_MS, System.currentTimeMillis())

        // Ensure singletons are loaded — process may have been cold-started by the alarm.
        AdhanPrefs.init(context)
        PrayerSettings.init(context)

        val config = AdhanPrefs.getFor(prayerId)

        if (isPre) {
            if (config.isEnabled && config.preAlertMinutes > 0) {
                NotificationHelper.postPreAlertNotification(
                    context, prayerId, prayerName, config.preAlertMinutes,
                )
            }
        } else {
            if (config.sound !is AdhanSound.Off) {
                NotificationHelper.postAdhanNotification(
                    context, prayerId, prayerName, prayerTimeMs, config.sound,
                )
            }
        }

        // Re-arm next occurrence (AdhanScheduler handles missing location internally).
        AdhanScheduler.schedulePrayer(context, prayerId)
    }
}