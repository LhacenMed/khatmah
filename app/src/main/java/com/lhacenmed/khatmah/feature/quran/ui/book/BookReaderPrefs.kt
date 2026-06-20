package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable display preferences for the book reader — the QCF4 equivalents of Quran
 * Android's night-mode brightness and page-info overlay settings. Night mode itself lives
 * in [BookReaderTheme].
 *
 * Each value is a [StateFlow] so a change propagates to **every** live page at once (current
 * and offscreen neighbours), with no stale frame when swiping. Brightness values are 0..255
 * and only affect night mode (matching `nightModeTextBrightness` / `nightModeBackgroundBrightness`).
 */
object BookReaderPrefs {

    private const val PREFS = "book_reader"
    private const val KEY_TEXT_BRIGHTNESS = "text_brightness"
    private const val KEY_BG_BRIGHTNESS = "bg_brightness"
    private const val KEY_SHOW_PAGE_INFO = "show_page_info"

    const val DEFAULT_TEXT_BRIGHTNESS = 255
    const val DEFAULT_BG_BRIGHTNESS = 0

    private val _textBrightness = MutableStateFlow(DEFAULT_TEXT_BRIGHTNESS)
    val textBrightness: StateFlow<Int> = _textBrightness.asStateFlow()

    private val _backgroundBrightness = MutableStateFlow(DEFAULT_BG_BRIGHTNESS)
    val backgroundBrightness: StateFlow<Int> = _backgroundBrightness.asStateFlow()

    private val _showPageInfo = MutableStateFlow(true)
    val showPageInfo: StateFlow<Boolean> = _showPageInfo.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Seeds the flows from storage. Call once when the reader opens. */
    fun init(context: Context) {
        val p = prefs(context)
        _textBrightness.value = p.getInt(KEY_TEXT_BRIGHTNESS, DEFAULT_TEXT_BRIGHTNESS)
        _backgroundBrightness.value = p.getInt(KEY_BG_BRIGHTNESS, DEFAULT_BG_BRIGHTNESS)
        _showPageInfo.value = p.getBoolean(KEY_SHOW_PAGE_INFO, true)
    }

    fun setTextBrightness(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_TEXT_BRIGHTNESS, value) }
        _textBrightness.value = value
    }

    fun setBackgroundBrightness(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_BG_BRIGHTNESS, value) }
        _backgroundBrightness.value = value
    }

    fun setShowPageInfo(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SHOW_PAGE_INFO, value) }
        _showPageInfo.value = value
    }
}
