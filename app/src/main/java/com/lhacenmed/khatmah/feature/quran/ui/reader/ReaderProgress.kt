package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import androidx.core.content.edit
import com.lhacenmed.khatmah.shared.reminders.SunnahSurah
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

    private const val KEY_PREFIX       = "resume_"  // + print id
    private const val KEY_LAST_READING = "resume_reading"
    private const val SEP              = ","

    // How a LastReading is written down; the surah's own key follows the prefix.
    private const val MUSHAF        = "mushaf"
    private const val WIRD          = "wird"
    private const val SUNNAH_PREFIX = "sunnah:"

    /** A reading position: 1-based [page] in the print's own pagination, and its first verse. */
    data class Anchor(val page: Int, val sura: Int, val aya: Int)

    /**
     * What the reader was last opened on — the answer to "carry on reading".
     *
     * Every reading already remembers its own position ([Anchor] per print, a page per session);
     * what is missing without this is which of them was last in front. Recorded where progress
     * itself is recorded, so every surface that offers to carry on reading agrees on where that
     * leads without having to ask the others.
     *
     * Each case names a *reading*, not a place. Where it leads is worked out again at the moment
     * it is resumed, because the answer moves: the wird advances with the khatmah's schedule, and
     * a surah falls on different pages in different riwayas. Storing a destination instead would
     * reopen a wird the schedule has left behind, or a page belonging to another print.
     */
    sealed interface LastReading {
        /** Free reading of the mushaf, resumed from the selected print's own anchor. */
        data object Mushaf : LastReading

        /** The khatmah wird — whichever session is the current one when it is resumed. */
        data object Wird : LastReading

        /** A sunnah surah, windowed to its pages in whatever print is selected when resumed. */
        data class Sunnah(val surah: SunnahSurah) : LastReading
    }

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

    /** Records the reading in front, alongside the position being saved for it. */
    fun saveLastReading(context: Context, reading: LastReading) {
        prefs(context).edit { putString(KEY_LAST_READING, encode(reading)) }
    }

    /** The reading to carry on with; [LastReading.Mushaf] until another one has been read. */
    fun lastReading(context: Context): LastReading =
        decode(prefs(context).getString(KEY_LAST_READING, null))

    private fun encode(reading: LastReading): String = when (reading) {
        LastReading.Mushaf    -> MUSHAF
        LastReading.Wird      -> WIRD
        is LastReading.Sunnah -> "$SUNNAH_PREFIX${reading.surah.key}"
    }

    /** Anything no longer recognised — a surah dropped by an update — reads as the mushaf. */
    private fun decode(stored: String?): LastReading = when {
        stored == WIRD -> LastReading.Wird
        stored != null && stored.startsWith(SUNNAH_PREFIX) ->
            SunnahSurah.of(stored.removePrefix(SUNNAH_PREFIX))
                ?.let(LastReading::Sunnah)
                ?: LastReading.Mushaf
        else -> LastReading.Mushaf
    }

    private fun key(printId: String) = "$KEY_PREFIX$printId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
