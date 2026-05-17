package com.lhacenmed.khatmah.shared.reminders

/**
 * Unified configuration for any reminder.
 *
 * [soundKey]:
 *  - [ReminderType.Prayer]  → AdhanSound key: "off" / "silent" / "device" / "asset:<file>" / "custom\u0000<name>\u0000<uri>"
 *  - All other types        → ReminderSound key: "off" / "device" / "custom\u0000<name>\u0000<uri>"
 *
 * [timeHour] / [timeMinute]: ignored for [ReminderType.Prayer] — times are computed by PrayerEngine.
 * [preAlertMinutes]: [ReminderType.Prayer] only. 0 = disabled. Pre-alert uses alarmCode + 10.
 * [deepLink]: null → type default resolved in [ReminderNotifier].
 */
data class ReminderConfig(
    val id: String,
    val type: ReminderType,
    val enabled: Boolean = true,
    val timeHour: Int = 7,
    val timeMinute: Int = 0,
    val soundKey: String = "device",
    val preAlertMinutes: Int = 0,
    val alarmCode: Int,
    val deepLink: String? = null,
)