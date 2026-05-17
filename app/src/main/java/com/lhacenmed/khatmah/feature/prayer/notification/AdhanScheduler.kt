package com.lhacenmed.khatmah.feature.prayer.notification

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler

/**
 * Compat shim — delegates to [ReminderScheduler].
 * Kept for any prayer settings UI that calls [schedulePrayer] after saving.
 * Remove once those callers migrate to [ReminderScheduler] directly.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AdhanScheduler {
    fun scheduleAll(context: Context) = ReminderScheduler.scheduleAll(context)
    fun schedulePrayer(context: Context, prayerId: Int) {
        val config = ReminderPrefs.getById("prayer:$prayerId") ?: return
        ReminderScheduler.schedule(context, config)
    }
}