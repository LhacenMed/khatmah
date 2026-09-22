package com.lhacenmed.khatmah.feature.more.reminders

import android.content.Context
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.normalizeArabic
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderType
import com.lhacenmed.khatmah.shared.reminders.SunnahSurah

/**
 * Something a reminder can be set for: a surah to read, or a category of adhkar to say.
 *
 * [id] is the reminder's id as well as the target's — one target, one reminder, which is what lets
 * the picker say [alreadyAdded] rather than letting the user create a second alarm for a surah
 * they are already reminded about.
 */
data class ReminderTarget(
    val id: String,
    val type: ReminderType,
    val name: String,
    val alreadyAdded: Boolean,
) {
    /**
     * What search matches against — the name stripped of diacritics and of the alef and ya
     * spellings that differ between mushafs, prepared once here rather than on every keystroke.
     */
    val searchKey: String = name.normalizeArabic()
}

/**
 * Everything the app can be asked to remind about: the adhkar categories the user actually has,
 * then the mushaf in order.
 *
 * The surahs come from the selected print, because that is the mushaf the reminder will open, and
 * the three in [SunnahSurah] keep their seeded ids so the app's own suggestion and the one a user
 * would add are the same reminder rather than two alarms for one surah.
 */
suspend fun reminderTargets(context: Context): List<ReminderTarget> {
    val taken = ReminderPrefs.getAll().map { it.id }.toSet()

    val adhkar = AdhkarRepository(context).getCategories().map { category ->
        target("adhkar:${category.id}", ReminderType.Adhkar(category.id), category.title, taken)
    }

    val riwaya = MushafPrefs.selected.value.riwaya.dbKey
    val surahs = QuranTextRepository(context).surahList(riwaya).map { surah ->
        val key = SunnahSurah.ofNumber(surah.num)?.key ?: surah.num.toString()
        target("sunnah:$key", ReminderType.QuranSunnah(key), surah.name, taken)
    }

    return adhkar + surahs
}

private fun target(id: String, type: ReminderType, name: String, taken: Set<String>) =
    ReminderTarget(id = id, type = type, name = name, alreadyAdded = id in taken)
