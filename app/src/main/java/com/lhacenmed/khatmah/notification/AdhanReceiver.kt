package com.lhacenmed.khatmah.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.AdhanSound
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.util.NotificationHelper

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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerId   = intent.getIntExtra(EXTRA_PRAYER_ID, -1).takeIf { it in 0..5 } ?: return
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
        val isPre      = intent.getBooleanExtra(EXTRA_IS_PRE, false)

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
                    context, prayerId, prayerName, config.sound,
                )
            }
        }

        // Re-arm next occurrence (AdhanScheduler handles missing location internally).
        AdhanScheduler.schedulePrayer(context, prayerId)
    }
}