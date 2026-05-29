package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the last [MAX] accessed surah numbers in recency order (most recent first).
 * Used to populate the Quick Index section on TodayTab.
 */
object RecentSurahsPrefs {

    const val MAX = 3

    private const val PREFS = "recent_surahs"
    private const val KEY   = "list"
    private const val SEP   = ","

    /** Returns up to [MAX] recently accessed surah numbers, most recent first. */
    fun get(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return raw.split(SEP).mapNotNull { it.toIntOrNull() }
    }

    /**
     * Pushes [suraNum] to the front of the list, evicting the oldest entry if
     * the list already contains [MAX] distinct surahs.
     */
    fun record(context: Context, suraNum: Int) {
        val updated = (listOf(suraNum) + get(context).filter { it != suraNum }).take(MAX)
        prefs(context).edit { putString(KEY, updated.joinToString(SEP)) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}