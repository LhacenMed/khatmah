package com.lhacenmed.khatmah.shared.reminders

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R

/**
 * A surah the app reminds the user to read, and everything the app needs to know about it: the
 * [key] its reminder is stored under, its [number] in the mushaf, and its [nameRes].
 *
 * One entry per surah, in one place, so the reminder, the notification it posts, the row on the
 * More tab and the reader that opens from either all mean the same surah. Adding a fourth is this
 * list plus its reminder in [ReminderPrefs] — nothing else needs to learn about it.
 */
enum class SunnahSurah(
    val key: String,
    val number: Int,
    @param:StringRes val nameRes: Int,
) {
    AlKahf("al_kahf", 18, R.string.more_surat_kahf),
    AlMulk("al_mulk", 67, R.string.more_surat_mulk),
    AlBaqarah("al_baqarah", 2, R.string.more_surat_baqarah);

    companion object {
        /** The surah a [ReminderType.QuranSunnah] key names, or null if it names none. */
        fun of(key: String): SunnahSurah? = entries.firstOrNull { it.key == key }

        /** The surah with this number in the mushaf, or null if it is not one of these. */
        fun ofNumber(number: Int): SunnahSurah? = entries.firstOrNull { it.number == number }
    }
}
