package com.lhacenmed.khatmah.shared.reminders

sealed class ReminderType {
    /** Prayer adhan — time computed dynamically from PrayerEngine, not from timeHour/timeMinute. */
    data class Prayer(val prayerId: Int) : ReminderType()
    /** Morning, evening, or any adhkar category. */
    data class Adhkar(val categoryId: String) : ReminderType()
    /** Quran sunnah — al_kahf, al_mulk, al_baqarah, etc. */
    data class QuranSunnah(val surahKey: String) : ReminderType()
    /** Daily khatmah session reminder. */
    object DailyKhatmah : ReminderType()
    /** Fully user-defined reminder. */
    data class Custom(val customId: String) : ReminderType()

    fun toKey(): String = when (this) {
        is Prayer        -> "prayer:$prayerId"
        is Adhkar        -> "adhkar:$categoryId"
        is QuranSunnah   -> "sunnah:$surahKey"
        is DailyKhatmah  -> "khatmah"
        is Custom        -> "custom:$customId"
    }

    companion object {
        fun fromKey(key: String): ReminderType = when {
            key.startsWith("prayer:") -> Prayer(key.removePrefix("prayer:").toInt())
            key.startsWith("adhkar:") -> Adhkar(key.removePrefix("adhkar:"))
            key.startsWith("sunnah:") -> QuranSunnah(key.removePrefix("sunnah:"))
            key == "khatmah"          -> DailyKhatmah
            key.startsWith("custom:") -> Custom(key.removePrefix("custom:"))
            else -> throw IllegalArgumentException("Unknown ReminderType key: $key")
        }
    }
}