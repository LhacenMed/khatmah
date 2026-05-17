package com.lhacenmed.khatmah.shared.reminders

/**
 * Notification sound for non-prayer reminders.
 * Prayer reminders use AdhanSound key strings directly (see ReminderConfig.soundKey).
 */
sealed class ReminderSound {
    object Off    : ReminderSound()
    object Device : ReminderSound()
    data class Custom(val uri: String, val displayName: String) : ReminderSound()

    fun toKey(): String = when (this) {
        is Off    -> "off"
        is Device -> "device"
        is Custom -> "custom\u0000$displayName\u0000$uri"
    }

    companion object {
        fun fromKey(key: String?): ReminderSound = when {
            key == null                     -> Device
            key == "off"                    -> Off
            key == "device"                 -> Device
            key.startsWith("custom\u0000") -> key.split('\u0000').let {
                if (it.size >= 3) Custom(it[2], it[1]) else Device
            }
            else -> Device
        }
    }
}