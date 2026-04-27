package com.lhacenmed.khatmah.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the exact-alarm broadcast fired at each prayer time transition.
 * Triggers a full widget refresh, which recalculates prayers and re-arms
 * the next alarm via [PrayerWidget.scheduleNextAlarm].
 */
@RequiresApi(Build.VERSION_CODES.O)
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { PrayerWidget().updateAll(context) }
            finally { pendingResult.finish() }
        }
    }
}