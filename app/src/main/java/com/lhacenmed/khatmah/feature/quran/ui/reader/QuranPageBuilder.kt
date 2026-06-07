package com.lhacenmed.khatmah.feature.quran.ui.reader

import com.lhacenmed.khatmah.feature.quran.data.QuranAya
import kotlin.math.ceil
import kotlin.math.max

// ── Models ────────────────────────────────────────────────────────────────────

sealed class QuranSegment {
    /** Sura title — appears BEFORE Basmala. Name has no "سورة" prefix. */
    data class SuraHeader(val num: Int, val name: String) : QuranSegment()
    /** Basmala — follows SuraHeader. */
    data class Basmala(val suraNum: Int)                  : QuranSegment()
    /** Single aya with its number for inline rendering. */
    data class Aya(val suraNum: Int, val ayaNum: Int, val text: String) : QuranSegment()
}

/**
 * A single display page in the Quran reader.
 *
 * [pageNum]  — 1-based sequential page number.
 * [suraName] — dominant sura on this page (top bar title).
 * [juz]      — juz label from the first aya on this page e.g. "الجزء ١".
 * [segments] — ordered segments to render top-to-bottom.
 * [centered] — true for special opening pages (Al-Fatiha, Al-Baqarah intro).
 */
data class QuranPageData(
    val pageNum:  Int,
    val suraName: String,
    val juz:      String,
    val segments: List<QuranSegment>,
    val centered: Boolean = false,
)

// ── Utility ───────────────────────────────────────────────────────────────────

/** Converts a non-negative integer to Eastern Arabic-Indic numerals (٠١٢٣…). */
internal fun toArNums(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")

// ── Builder ───────────────────────────────────────────────────────────────────

/**
 * Builds [QuranPageData] pages from raw aya data using pure line-count arithmetic.
 *
 * [bismillahPre] — map of suraNum → whether a basmala precedes it.
 *   Derived from [SurahEntity.bismillahPre]; defaults to the standard rule
 *   (all surahs except 1 and 9) when empty.
 */
object QuranPageBuilder {

    /** Target logical lines per page. */
    const val LINES_PER_PAGE = 25

    /**
     * Estimated Unicode code units per rendered line.
     * Calibrated for ~360 dp content width.
     */
    private const val CHARS_PER_LINE = 58

    private fun estimateLines(text: String): Int =
        max(1, ceil(text.length.toFloat() / CHARS_PER_LINE).toInt())

    fun build(
        ayas:          List<QuranAya>,
        bismillahPre:  Map<Int, Boolean> = emptyMap(),
    ): List<QuranPageData> {
        val valid = ayas.filter { it.aya.isNotBlank() }
        if (valid.isEmpty()) return emptyList()

        val result      = mutableListOf<QuranPageData>()
        var pageNum     = 1
        val headedSuras = mutableSetOf<Int>()

        // Moved up so special-pages block can use it too.
        fun hasBismillah(suraNum: Int): Boolean =
            bismillahPre[suraNum] ?: (suraNum != 1 && suraNum != 9)

// ── Special opening pages (centered) ─────────────────────────────────

        val fatihaAyas = valid.filter { it.suraNum == 1 }
        if (fatihaAyas.isNotEmpty()) {
            val name = fatihaAyas.first().sura.removePrefix("سورة ").trim()
            result += QuranPageData(
                pageNum  = pageNum++,
                suraName = name,
                juz      = fatihaAyas.first().juz,
                segments = buildList {
                    add(QuranSegment.SuraHeader(1, name))
                    if (hasBismillah(1)) add(QuranSegment.Basmala(1))
                    fatihaAyas.forEach { add(QuranSegment.Aya(it.suraNum, it.ayaNum, it.aya)) }
                },
                centered = true,
            )
            headedSuras += 1
        }

        // Al-Baqarah ayas 1–5: isolated centered page with header + Basmala.
        val baqarahOpening = valid.filter { it.suraNum == 2 && it.ayaNum <= 5 }
        if (baqarahOpening.isNotEmpty()) {
            val name = baqarahOpening.first().sura.removePrefix("سورة ").trim()
            result += QuranPageData(
                pageNum  = pageNum++,
                suraName = name,
                juz      = baqarahOpening.first().juz,
                segments = buildList {
                    add(QuranSegment.SuraHeader(2, name))
                    add(QuranSegment.Basmala(2))
                    baqarahOpening.forEach { add(QuranSegment.Aya(it.suraNum, it.ayaNum, it.aya)) }
                },
                centered = true,
            )
            headedSuras += 2
        }

        // ── Normal pagination ─────────────────────────────────────────────────

        val normalAyas = valid.filter {
            it.suraNum != 1 && !(it.suraNum == 2 && it.ayaNum <= 5)
        }
        if (normalAyas.isEmpty()) return result

        val curSegs   = mutableListOf<QuranSegment>()
        var lineCount = 0
        var pageSura  = ""
        var pageJuz   = ""

        fun setMeta(sura: String, juz: String) {
            if (curSegs.isEmpty()) { pageSura = sura; pageJuz = juz }
        }

        fun flush() {
            if (curSegs.isNotEmpty()) {
                result += QuranPageData(pageNum++, pageSura, pageJuz, curSegs.toList())
                curSegs.clear(); lineCount = 0; pageSura = ""; pageJuz = ""
            }
        }

        fun addSeg(seg: QuranSegment, lines: Int, sura: String, juz: String) {
            setMeta(sura, juz); curSegs += seg; lineCount += lines
        }

        val suraBuffer = mutableListOf<QuranAya>()
        var prevSura   = -1

        fun flushSura() {
            val first    = suraBuffer.firstOrNull() ?: return
            val suraNum  = first.suraNum
            val suraName = first.sura.removePrefix("سورة ").trim()
            val firstJuz = first.juz

            val skipHeader = suraNum in headedSuras
            val hasBasmala = !skipHeader && hasBismillah(suraNum)
            val headerCost = if (skipHeader) 0 else (1 + if (hasBasmala) 1 else 0)

            if (!skipHeader) {
                // Orphan prevention: flush so headers never sit alone at page bottom.
                if (curSegs.isNotEmpty() && lineCount + headerCost > LINES_PER_PAGE) flush()

                addSeg(QuranSegment.SuraHeader(suraNum, suraName), 1, suraName, firstJuz)
                if (hasBasmala) addSeg(QuranSegment.Basmala(suraNum), 1, suraName, firstJuz)
                if (lineCount >= LINES_PER_PAGE) flush()
            }

            for (aya in suraBuffer) {
                val lines = estimateLines(aya.aya)
                // An oversized aya (> LINES_PER_PAGE) always gets its own page.
                if (curSegs.isNotEmpty() && lineCount + lines > LINES_PER_PAGE) flush()
                addSeg(QuranSegment.Aya(aya.suraNum, aya.ayaNum, aya.aya), lines, suraName, aya.juz)
                if (lineCount >= LINES_PER_PAGE) flush()
            }

            suraBuffer.clear()
        }

        for (aya in normalAyas) {
            if (aya.suraNum != prevSura) {
                if (suraBuffer.isNotEmpty()) flushSura()
                prevSura = aya.suraNum
            }
            suraBuffer += aya
        }
        if (suraBuffer.isNotEmpty()) flushSura()
        flush()

        return result
    }
}