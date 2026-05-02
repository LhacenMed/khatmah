package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized, SharedPreferences-backed store for user preferences that span
 * multiple screens (reader style, selected audio reader, and future additions).
 *
 * Follows the same pattern as [ThemeManager] and [LocaleManager]:
 *  - Singleton object, no DI required.
 *  - [init] called once from [App.onCreate] to load persisted values.
 *  - Exposes a [kotlinx.coroutines.flow.StateFlow] per preference so any composable can observe changes.
 */
object AppPrefs {

    private const val PREFS_FILE        = "app_prefs"
    private const val KEY_READER_STYLE  = "reader_style"
    private const val KEY_AUDIO_READER  = "audio_reader_id"

    // ── Reader Style ──────────────────────────────────────────────────────────

    enum class ReaderStyle {
        /** Rendered Quran text from the database (current default). */
        TEXT,
        /** Scanned mushaf pages from assets/quran/ (images 1–604). */
        IMAGES,
        /** Warsh mushaf SVG pages from assets/mushafs/warsh/svg-br/ (1–604). */
        SVG_WARSH,
    }

    private val _readerStyle = MutableStateFlow(ReaderStyle.TEXT)
    val readerStyle: StateFlow<ReaderStyle> = _readerStyle.asStateFlow()

    // ── Audio Reader ──────────────────────────────────────────────────────────

    /**
     * ID of the currently selected Quran audio reader.
     * Matches [ReaderInfo.id] from assets/readers.json.
     * Empty string means "use the first reader in the manifest".
     */
    private val _audioReaderId = MutableStateFlow("")
    val audioReaderId: StateFlow<String> = _audioReaderId.asStateFlow()

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

        _audioReaderId.value = prefs.getString(KEY_AUDIO_READER, "") ?: ""
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setReaderStyle(context: Context, style: ReaderStyle) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_READER_STYLE, style.name) }
        _readerStyle.value = style
    }

    fun setAudioReaderId(context: Context, id: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_AUDIO_READER, id) }
        _audioReaderId.value = id
    }
}