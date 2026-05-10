package com.lhacenmed.khatmah.shared.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings

/**
 * Re-schedules all reminders after device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml.
 */
@RequiresApi(Build.VERSION_CODES.O)
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderPrefs.init(context)
        PrayerSettings.init(context)
        ReminderScheduler.scheduleAll(context)
    }
}