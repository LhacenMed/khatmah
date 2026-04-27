package com.lhacenmed.khatmah.data.prefs

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized, SharedPreferences-backed store for user preferences that span
 * multiple screens (reader style, and future additions).
 *
 * Follows the same pattern as [ThemeManager] and [LocaleManager]:
 *  - Singleton object, no DI required.
 *  - [init] called once from [App.onCreate] to load persisted values.
 *  - Exposes a [StateFlow] per preference so any composable can observe changes.
 */
object AppPrefs {

    private const val PREFS_FILE       = "app_prefs"
    private const val KEY_READER_STYLE = "reader_style"

    // ── Reader Style ──────────────────────────────────────────────────────────

    enum class ReaderStyle {
        /** Rendered Quran text from the database (current default). */
        TEXT,
        /** Scanned mushaf pages from assets/quran/ (images 1–604). */
        IMAGES,
    }

    private val _readerStyle = MutableStateFlow(ReaderStyle.TEXT)
    val readerStyle: StateFlow<ReaderStyle> = _readerStyle.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Call once from [App.onCreate] before any UI is composed.
     * Restores all persisted preferences into their respective StateFlows.
     */
    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        _readerStyle.value = prefs.getString(KEY_READER_STYLE, null)
            ?.let { runCatching { ReaderStyle.valueOf(it) }.getOrNull() }
            ?: ReaderStyle.TEXT
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setReaderStyle(context: Context, style: ReaderStyle) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_READER_STYLE, style.name) }
        _readerStyle.value = style
    }
}