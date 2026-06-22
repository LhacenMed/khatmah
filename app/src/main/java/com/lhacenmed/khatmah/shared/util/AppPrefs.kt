package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized, SharedPreferences-backed store for user preferences that span
 * multiple screens (selected audio reader, and future additions).
 *
 * Mushaf print selection is managed by [com.lhacenmed.khatmah.feature.quran.data.MushafPrefs].
 */
object AppPrefs {

    private const val PREFS_FILE       = "app_prefs"
    private const val KEY_AUDIO_READER = "audio_reader_id"

    // ── Audio Reader ──────────────────────────────────────────────────────────

    /**
     * ID of the currently selected Quran audio reader.
     * Matches [GhReader.id] from assets/recitations.json.
     * Empty string means "use the first reader in the manifest".
     */
    private val _audioReaderId = MutableStateFlow("")
    val audioReaderId: StateFlow<String> = _audioReaderId.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Call once from [App.onCreate] before any UI is composed. */
    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        _audioReaderId.value = prefs.getString(KEY_AUDIO_READER, "") ?: ""
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    fun setAudioReaderId(context: Context, id: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_AUDIO_READER, id) }
        _audioReaderId.value = id
    }
}