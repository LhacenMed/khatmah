package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the reader was left off, per print — the anchor the Quran tab resumes from.
 *
 * A page number alone can't describe a position (text and QCF4 prints paginate differently), so
 * [ReaderActivity] records the page together with its first verse; the tab resolves sura/juz/aya
 * text from that verse. [lastSaved] emits on every save, so the tab refreshes the moment the
 * reader moves a page — no reload on return.
 */
object ReaderProgress {

    /** Preferences file — shared with [ReaderActivity]'s last-page and session-page stores. */
    const val PREFS = "quran_reader"

    private const val KEY_PREFIX = "resume_"  // + print id
    private const val SEP        = ","

    /** A reading position: 1-based [page] in the print's own pagination, and its first verse. */
    data class Anchor(val page: Int, val sura: Int, val aya: Int)

    private val _lastSaved = MutableStateFlow<Anchor?>(null)

    /** Latest anchor written in this process; null until the reader saves one. */
    val lastSaved: StateFlow<Anchor?> = _lastSaved.asStateFlow()

    fun save(context: Context, printId: String, anchor: Anchor) {
        prefs(context).edit {
            putString(key(printId), "${anchor.page}$SEP${anchor.sura}$SEP${anchor.aya}")
        }
        _lastSaved.value = anchor
    }

    /** The stored anchor for [printId], or null when that print was never opened. */
    fun read(context: Context, printId: String): Anchor? {
        val parts = prefs(context).getString(key(printId), null)?.split(SEP) ?: return null
        val nums  = parts.mapNotNull { it.toIntOrNull() }
        return if (nums.size == 3) Anchor(nums[0], nums[1], nums[2]) else null
    }

    private fun key(printId: String) = "$KEY_PREFIX$printId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
