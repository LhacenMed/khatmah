package com.lhacenmed.khatmah.ui.page.quran

import com.lhacenmed.khatmah.data.quran.QuranAya
import kotlin.math.ceil
import kotlin.math.max

/**
 * Builds [QuranPageData] pages from raw aya data using pure line-count arithmetic.
 *
 * Why this is fast:
 *  The previous builder used TextMeasurer with binary search — O(n log n) expensive
 *  layout passes, taking several seconds. This builder uses only character-count
 *  estimation with integer arithmetic: O(n), completes in < 10 ms for the full Quran.
 *
 * Page fill strategy:
 *  Each item is assigned an estimated line count:
 *    SuraHeader = 1 line · Basmala = 1 line · Aya = ceil(text.length / CHARS_PER_LINE) lines.
 *  Items are packed greedily until lines ≥ [LINES_PER_PAGE].
 *
 * Special opening pages (centered = true):
 *  Al-Fatiha (sura 1) and Al-Baqarah ayas 1–5 are each isolated onto their own
 *  page before normal pagination begins. These pages are marked [QuranPageData.centered]
 *  so the renderer uses center text-alignment instead of justify.
 *  Suras emitted on special pages are recorded in [headedSuras] so the normal
 *  pagination loop never re-emits their SuraHeader or Basmala.
 *
 * Item order within each sura boundary: SuraHeader → Basmala → Ayas.
 */
object QuranPageBuilder {

    /** Target logical lines per page. Drives packing density. */
    const val LINES_PER_PAGE = 25

    /**
     * Estimated Unicode code units per rendered line for WarshFamily at 20sp.
     * Quran text contains many diacritics (harakat), inflating code-unit count ~2-3×
     * vs. visible glyphs. Calibrated for a standard ~360 dp content width.
     */
    private const val CHARS_PER_LINE = 58

    private fun estimateLines(text: String): Int =
        max(1, ceil(text.length.toFloat() / CHARS_PER_LINE).toInt())

    fun build(ayas: List<QuranAya>): List<QuranPageData> {
        // Filter out DB rows where the aya text is null or blank — these would
        // render as an empty line followed by an aya number ornament.
        val valid = ayas.filter { it.aya.isNotBlank() }
        if (valid.isEmpty()) return emptyList()

        val result  = mutableListOf<QuranPageData>()
        var pageNum = 1

        // Suras whose header was emitted on a special page — skipped in normal flow
        // to prevent a duplicate SuraHeader + Basmala on the continuation page.
        val headedSuras = mutableSetOf<Int>()

        // ── Special opening pages (centered) ─────────────────────────────────

        // Al-Fatiha: all ayas on one isolated centered page. Sura 1 has no Basmala.
        val fatihaAyas = valid.filter { it.suraNum == 1 }
        if (fatihaAyas.isNotEmpty()) {
            val name = fatihaAyas.first().sura.removePrefix("سورة ").trim()
            val segs = buildList<QuranSegment> {
                add(QuranSegment.SuraHeader(1, name))
                fatihaAyas.forEach { add(QuranSegment.Aya(it.suraNum, it.ayaNum, it.aya)) }
            }
            result += QuranPageData(
                pageNum  = pageNum++,
                suraName = name,
                juz      = fatihaAyas.first().juz,
                segments = segs,
                centered = true,
            )
            headedSuras += 1
        }

        // Al-Baqarah ayas 1–5: isolated centered page with header + Basmala.
        // Remaining Baqarah ayas (6+) continue in normal pagination without re-heading.
        val baqarahOpening = valid.filter { it.suraNum == 2 && it.ayaNum <= 5 }
        if (baqarahOpening.isNotEmpty()) {
            val name = baqarahOpening.first().sura.removePrefix("سورة ").trim()
            val segs = buildList<QuranSegment> {
                add(QuranSegment.SuraHeader(2, name))
                add(QuranSegment.Basmala(2))
                baqarahOpening.forEach { add(QuranSegment.Aya(it.suraNum, it.ayaNum, it.aya)) }
            }
            result += QuranPageData(
                pageNum  = pageNum++,
                suraName = name,
                juz      = baqarahOpening.first().juz,
                segments = segs,
                centered = true,
            )
            headedSuras += 2
        }

        // ── Normal pagination ─────────────────────────────────────────────────
        // Sura 1 is fully consumed above. Baqarah ayas 1–5 are consumed above;
        // aya 6+ continues here without a repeated header.
        val normalAyas = valid.filter {
            it.suraNum != 1 && !(it.suraNum == 2 && it.ayaNum <= 5)
        }
        if (normalAyas.isEmpty()) return result

        val curSegs = mutableListOf<QuranSegment>()
        var lineCount = 0
        var pageSura  = ""
        var pageJuz   = ""

        // Lazy metadata: set only when the page is fresh so each page
        // is attributed to its first sura.
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
            // Strip any "سورة " prefix that might be present in the DB name column.
            val suraName = first.sura.removePrefix("سورة ").trim()
            val firstJuz = first.juz

            // Skip header/basmala for suras already emitted on a special page.
            val skipHeader = suraNum in headedSuras
            val hasBasmala = !skipHeader && suraNum != 1 && suraNum != 9
            val headerCost = if (skipHeader) 0 else (1 + if (hasBasmala) 1 else 0)

            if (!skipHeader) {
                // Orphan prevention: flush current page so headers never appear alone
                // at the bottom of a page with no following ayas.
                if (curSegs.isNotEmpty() && lineCount + headerCost > LINES_PER_PAGE) flush()

                // ── Ordering: SuraHeader THEN Basmala ────────────────────────
                addSeg(QuranSegment.SuraHeader(suraNum, suraName), 1, suraName, firstJuz)
                if (hasBasmala) addSeg(QuranSegment.Basmala(suraNum), 1, suraName, firstJuz)
                if (lineCount >= LINES_PER_PAGE) flush()
            }

            for (aya in suraBuffer) {
                val lines = estimateLines(aya.aya)
                // Flush before adding only when the page already has content.
                // An oversized aya (estimatedLines > LINES_PER_PAGE) always gets its
                // own page rather than causing an infinite loop.
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
        flush() // emit the final partial page

        return result
    }
}