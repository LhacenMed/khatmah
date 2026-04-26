package com.lhacenmed.khatmah.ui.page.quran

sealed class QuranSegment {
    /** Sura title — appears before Basmala. Name has no "سورة" prefix. */
    data class SuraHeader(val num: Int, val name: String) : QuranSegment()
    /** Basmala — follows SuraHeader. Omitted for suras 1 and 9. */
    data class Basmala(val suraNum: Int) : QuranSegment()
    /**
     * Single aya slice. When an aya is split across a page boundary,
     * [showOrnament] is false on all slices except the last.
     */
    data class Aya(
        val suraNum:      Int,
        val ayaNum:       Int,
        val text:         String,
        val showOrnament: Boolean = true,
    ) : QuranSegment()
}
data class QuranPageData(
    val pageNum:  Int,
    val suraName: String,
    val juz:      String,
    val segments: List<QuranSegment>,
    val centered: Boolean = false,
)
// Shared utility: Eastern Arabic-Indic numerals
internal fun Int.toArNums(): String =
    toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
