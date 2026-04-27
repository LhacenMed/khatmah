package com.lhacenmed.khatmah.ui.page.quran

sealed class QuranSegment {
    /** Sura title — appears BEFORE Basmala. Name is raw (no "سورة" prefix). */
    data class SuraHeader(val num: Int, val name: String) : QuranSegment()
    /** Basmala — follows SuraHeader. Omitted for suras 1 and 9 per Quranic tradition. */
    data class Basmala(val suraNum: Int)                  : QuranSegment()
    /** Single aya with its number for inline rendering. */
    data class Aya(val suraNum: Int, val ayaNum: Int, val text: String) : QuranSegment()
}

// ── Page model ────────────────────────────────────────────────────────────────

/**
 * A single display page in the Quran reader.
 *
 * [pageNum]  — 1-based sequential page number for the top bar.
 * [suraName] — the dominant sura on this page (used in the top bar title).
 * [juz]      — juz identifier taken from the first aya on this page.
 * [segments] — ordered list of segments to render top-to-bottom.
 * [centered] — true for special opening pages (Al-Fatiha, Al-Baqarah intro)
 *              where aya text is center-aligned rather than justified edge-to-edge.
 */
data class QuranPageData(
    val pageNum:  Int,
    val suraName: String,
    val juz:      String,
    val segments: List<QuranSegment>,
    val centered: Boolean = false,
)