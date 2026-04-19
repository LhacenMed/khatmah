package com.lhacenmed.khatmah.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuranRepository(private val context: Context) {

    /** Returns all 6 262 ayas ordered by sura then aya number. */
    suspend fun allAyas(): List<QuranAya> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL, null).use { c ->
            val iSuraNum = c.getColumnIndexOrThrow("sura_num")
            val iSura    = c.getColumnIndexOrThrow("sura")
            val iAyaNum  = c.getColumnIndexOrThrow("aya_num")
            val iAya     = c.getColumnIndexOrThrow("aya")
            buildList {
                while (c.moveToNext()) add(
                    QuranAya(
                        suraNum = c.getInt(iSuraNum),
                        sura    = c.getString(iSura).orEmpty(),
                        ayaNum  = c.getInt(iAyaNum),
                        aya     = c.getString(iAya).orEmpty(),
                    )
                )
            }
        }
    }

    private companion object {
        const val SQL =
            "SELECT sura_num, sura, aya_num, aya FROM quran ORDER BY sura_num, aya_num"
    }
}
