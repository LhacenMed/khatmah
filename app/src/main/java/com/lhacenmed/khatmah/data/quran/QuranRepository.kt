package com.lhacenmed.khatmah.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Summary of a single surah for the index list. */
data class SurahInfo(val num: Int, val name: String, val ayaCount: Int)

class QuranRepository(private val context: Context) {

    /** All 6236 ayas ordered by sura then aya number. */
    suspend fun allAyas(): List<QuranAya> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_AYAS, null).use { c ->
            val iSuraNum = c.getColumnIndexOrThrow("sura_num")
            val iSura    = c.getColumnIndexOrThrow("sura")
            val iAyaNum  = c.getColumnIndexOrThrow("aya_num")
            val iAya     = c.getColumnIndexOrThrow("aya")
            val iJuz     = c.getColumnIndexOrThrow("juz")
            buildList {
                while (c.moveToNext()) add(
                    QuranAya(
                        suraNum = c.getInt(iSuraNum),
                        sura    = c.getString(iSura).orEmpty(),
                        ayaNum  = c.getInt(iAyaNum),
                        aya     = c.getString(iAya).orEmpty(),
                        juz     = c.getString(iJuz).orEmpty(),
                    )
                )
            }
        }
    }

    /** All 114 surahs with aya counts, ordered by surah number. */
    suspend fun surahList(): List<SurahInfo> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_SURAHS, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    SurahInfo(
                        num      = c.getInt(0),
                        name     = c.getString(1).orEmpty(),
                        ayaCount = c.getInt(2),
                    )
                )
            }
        }
    }

    private companion object {
        const val SQL_AYAS =
            "SELECT sura_num, sura, aya_num, aya, juz FROM quran ORDER BY sura_num, aya_num"
        const val SQL_SURAHS =
            "SELECT sura_num, sura, COUNT(aya_num) FROM quran GROUP BY sura_num ORDER BY sura_num"
    }
}
