package com.lhacenmed.khatmah.shared.reminders

import android.content.Context
import android.os.Build

/**
 * The two ways a reminder changes from the outside — saved, or dropped.
 *
 * Both halves of each are done together on purpose: a config the store knows about but no alarm is
 * armed for is a reminder that silently never arrives, and an alarm with no config behind it is one
 * that cannot be switched off. Every screen that edits a reminder goes through here so neither half
 * can be forgotten, and both are no-ops on the API levels the scheduler does not run on.
 */

/** Saves [config] and re-arms its alarm from the values it now carries. */
fun saveReminder(context: Context, config: ReminderConfig) {
    ReminderPrefs.save(context, config)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ReminderScheduler.schedule(context, config)
}

/** Cancels [config]'s alarm, then forgets the reminder — in that order, while it can still be found. */
fun deleteReminder(context: Context, config: ReminderConfig) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ReminderScheduler.cancel(context, config)
    ReminderPrefs.remove(context, config.id)
}
