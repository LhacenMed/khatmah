package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the last [MAX] accessed surah numbers in recency order (most recent first).
 * Used to populate the Quick Index section on TodayTab.
 *
 * Exposes [recent] as a [StateFlow] so a [record] from any screen (Today, the full index, …)
 * propagates back to every observer in-process — the Quick Index reorders automatically.
 */
object RecentSurahsPrefs {

    const val MAX = 3

    private const val PREFS = "recent_surahs"
    private const val KEY   = "list"
    private const val SEP   = ","

    private val _recent = MutableStateFlow<List<Int>>(emptyList())
    val recent: StateFlow<List<Int>> = _recent.asStateFlow()
    private var loaded = false

    /** Returns up to [MAX] recently accessed surah numbers, most recent first. */
    fun get(context: Context): List<Int> {
        ensureLoaded(context)
        return _recent.value
    }

    /**
     * Pushes [suraNum] to the front of the list, evicting the oldest entry if
     * the list already contains [MAX] distinct surahs.
     */
    fun record(context: Context, suraNum: Int) {
        ensureLoaded(context)
        val updated = (listOf(suraNum) + _recent.value.filter { it != suraNum }).take(MAX)
        prefs(context).edit { putString(KEY, updated.joinToString(SEP)) }
        _recent.value = updated
    }

    /** Seeds the flow from storage once per process. */
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val raw = prefs(context).getString(KEY, null)
        _recent.value = raw?.split(SEP)?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        loaded = true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}