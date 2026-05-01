package com.lhacenmed.khatmah.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.data.prayer.AdhanPrefs
import com.lhacenmed.khatmah.data.prayer.PrayerSettings

/**
 * Re-schedules all prayer alarms after a device reboot.
 * Requires RECEIVE_BOOT_COMPLETED permission in the manifest.
 */
@RequiresApi(Build.VERSION_CODES.O)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AdhanPrefs.init(context)
        PrayerSettings.init(context)
        AdhanScheduler.scheduleAll(context)
    }
}