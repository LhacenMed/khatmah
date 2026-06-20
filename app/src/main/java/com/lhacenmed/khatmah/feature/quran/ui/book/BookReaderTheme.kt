package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The book reader's own night-mode state — independent of the app theme and the reader's
 * chrome (toolbar/settings stay on the app theme).
 *
 * Default ([override] == null) follows the app theme. Once the user toggles night reading,
 * an explicit override is stored and applied **only to the rendered pages**. Exposed as a
 * [StateFlow] so every visible page updates live, with no activity recreation.
 */
object BookReaderTheme {

    private const val PREFS = "book_reader"
    private const val KEY_OVERRIDE = "reader_night_override" // -1 follow, 0 light, 1 dark
    private const val FOLLOW = -1
    private const val LIGHT = 0
    private const val DARK = 1

    private val _override = MutableStateFlow<Boolean?>(null)
    /** null = follow app theme; true/false = explicit reader-only override. */
    val override: StateFlow<Boolean?> = _override.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Seeds the flow from storage. Call once when the reader opens. */
    fun init(context: Context) {
        _override.value = when (prefs(context).getInt(KEY_OVERRIDE, FOLLOW)) {
            DARK -> true
            LIGHT -> false
            else -> null
        }
    }

    /** Effective night mode for the pages: the override, or the app theme when unset. */
    fun effectiveNight(context: Context): Boolean = _override.value ?: isAppInDarkTheme(context)

    /** Flips the current effective state into an explicit reader-only override. */
    fun toggle(context: Context) = set(context, !effectiveNight(context))

    private fun set(context: Context, value: Boolean) {
        prefs(context).edit { putInt(KEY_OVERRIDE, if (value) DARK else LIGHT) }
        _override.value = value
    }
}
