package com.lhacenmed.khatmah.shared.reminders

/**
 * Unified configuration for any reminder.
 *
 * [soundKey]:
 *  - [ReminderType.Prayer]  → AdhanSound key: "off" / "silent" / "device" / "asset:<file>" / "custom\u0000<name>\u0000<uri>"
 *  - All other types        → ReminderSound key: "off" / "device" / "custom\u0000<name>\u0000<uri>"
 *
 * [timeHour] / [timeMinute]: ignored for [ReminderType.Prayer] — times are computed by PrayerEngine.
 * Also ignored for [ReminderType.Adhkar] "morning"/"evening" unless [useCustomTime] is set — those
 * anchor to Fajr/Maghrib by default (see [ReminderScheduler]).
 * [preAlertMinutes]: [ReminderType.Prayer] only. 0 = disabled. Pre-alert uses alarmCode + 10.
 * [useCustomTime]: [ReminderType.Adhkar] "morning"/"evening" only. false (default) = anchor to the
 * prayer via [anchorOffsetMinutes]; true = use the fixed [timeHour]/[timeMinute] instead.
 * [anchorOffsetMinutes]: [ReminderType.Adhkar] "morning"/"evening" only. Minutes after Fajr (morning)
 * or before Maghrib (evening).
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
    val useCustomTime: Boolean = false,
    val anchorOffsetMinutes: Int = 30,
    /**
     * How often the reminder comes back: [REPEAT_DAILY], or a single [java.time.DayOfWeek] value
     * (Monday = 1 … Sunday = 7) for a once-a-week reminder.
     *
     * Only fixed-time reminders repeat on a schedule of their own. A prayer and an anchored dhikr
     * follow the sun, which has no weekday, so they ignore this.
     */
    val repeatDayOfWeek: Int = REPEAT_DAILY,
    /**
     * What a reminder the user added calls itself — the surah or the dhikr category they picked.
     *
     * Seeded reminders leave this null and name themselves from string resources, so they follow
     * the app's language. A user's own names the thing it was created from, which is not in the
     * string table and is not worth a database read every time a notification is posted.
     */
    val label: String? = null,
) {
    companion object {
        /** [repeatDayOfWeek] for a reminder that comes back every day. */
        const val REPEAT_DAILY = 0
    }
}
