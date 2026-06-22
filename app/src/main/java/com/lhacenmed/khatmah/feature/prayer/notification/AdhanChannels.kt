package com.lhacenmed.khatmah.feature.prayer.notification

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.shared.reminders.ReminderNotifier

/**
 * Keeps the per-sound adhan notification channels in step with the prayers that actually use them.
 *
 * Owns the AdhanSound ↔ channel mapping so [ReminderNotifier] (in shared/) stays free of any
 * feature/prayer dependency. Call [sync] on launch and after any prayer-sound change: it creates a
 * channel for every adhan sound currently assigned to a prayer and removes the rest, so the system
 * notification settings list only ever shows sounds in use — no orphan categories.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AdhanChannels {

    fun sync(context: Context) {
        val keep = mutableSetOf<String>()
        AdhanPrefs.get().forEach { config ->
            when (val sound = config.sound) {
                is AdhanSound.Asset -> {
                    ReminderNotifier.ensureAdhanAssetChannel(context, sound.filename)
                    keep += ReminderNotifier.adhanAssetChannelId(sound.filename)
                }
                is AdhanSound.Custom -> {
                    ReminderNotifier.ensureCustomAdhanChannel(context, sound.uri, sound.displayName)
                    keep += ReminderNotifier.customAdhanChannelId(sound.uri)
                }
                else -> Unit   // Off / Silent / Device use fixed channels — no per-sound channel.
            }
        }
        ReminderNotifier.pruneAdhanChannels(context, keep)
    }
}
