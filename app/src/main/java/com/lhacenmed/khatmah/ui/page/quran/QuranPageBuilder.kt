package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.ui.text.*
import androidx.compose.ui.unit.Constraints
import com.lhacenmed.khatmah.data.quran.QuranAya

/**
 * Builds [QuranPageData] pages from raw aya data using [TextMeasurer].
 *
 * Strategy:
 *  1. Iterate over suras. For each sura emit: Basmala (if applicable),
 *     SuraHeader, then the sura's ayas.
 *  2. For aya content, use a binary search to find the maximum number of
 *     consecutive ayas that fit in the remaining page height — O(n log n)
 *     total measure calls instead of O(n²).
 *  3. All ayas within one contiguous sura section are concatenated into a
 *     single [AnnotatedString] so they flow naturally on the same line(s).
 *
 * Must be called on the main thread (or a thread where [TextMeasurer] is safe).
 */
object QuranPageBuilder {

    private const val BASMALA = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
    // Sample text used to pre-measure header row height (content irrelevant)
    private const val HEADER_SAMPLE = "── سورة الفاتحة ──"

    fun build(
        ayas:        List<QuranAya>,
        measurer:    TextMeasurer,
        pageWidth:   Int,
        pageHeight:  Int,
        ayaStyle:    TextStyle,
        headerStyle: TextStyle,
        basmalaStyle: TextStyle,
        ayaNumSpan:  SpanStyle,
        gapPx:       Int,
    ): List<QuranPageData> {
        if (ayas.isEmpty()) return emptyList()

        val wc = Constraints(maxWidth = pageWidth)

        // Pre-measure fixed-height items once
        val basmalaH = measurer.measure(BASMALA, basmalaStyle, wc).size.height + gapPx
        val headerH  = measurer.measure(HEADER_SAMPLE, headerStyle, wc).size.height + gapPx

        // ── Page state ────────────────────────────────────────────────────────
        val result  = mutableListOf<QuranPageData>()
        val segs    = mutableListOf<QuranSegment>()
        var pH      = 0       // accumulated height on current page
        var pNum    = 1
        var pSura   = ""
        var pJuz    = ""

        fun flush() {
            if (segs.isNotEmpty()) {
                result += QuranPageData(pNum++, pSura, pJuz, segs.toList())
                segs.clear(); pH = 0; pSura = ""; pJuz = ""
            }
        }

        fun setMeta(sura: String, juz: String) {
            if (segs.isEmpty()) { pSura = sura; pJuz = juz }
        }

        fun addSeg(seg: QuranSegment, h: Int, sura: String, juz: String) {
            setMeta(sura, juz); segs += seg; pH += h
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        /** Concatenate [ayaList] into one AnnotatedString with inline aya numbers. */
        fun ayaStr(ayaList: List<QuranAya>): AnnotatedString = buildAnnotatedString {
            ayaList.forEachIndexed { i, a ->
                if (i > 0) append(" ")
                append(a.aya)
                append(" ")
                pushStyle(ayaNumSpan)
                append("﴿${toArNums(a.ayaNum)}﴾")
                pop()
            }
        }

        /**
         * Binary search: how many ayas from [list] fit within [availPx] height?
         * Returns 0 if even 1 aya exceeds [availPx].
         */
        fun fitCount(list: List<QuranAya>, availPx: Int): Int {
            var count = 0; var lo = 1; var hi = list.size
            while (lo <= hi) {
                val mid  = (lo + hi) / 2
                val h    = measurer.measure(ayaStr(list.take(mid)), ayaStyle, wc).size.height + gapPx
                if (h <= availPx) { count = mid; lo = mid + 1 } else hi = mid - 1
            }
            return count
        }

        // ── Per-sura processing ───────────────────────────────────────────────

        fun processSura(suraNum: Int, suraName: String, firstJuz: String, ayaList: List<QuranAya>) {
            // Basmala (suras 1 and 9 are exceptions)
            if (suraNum != 1 && suraNum != 9) {
                if (pH + basmalaH > pageHeight) flush()
                addSeg(QuranSegment.Basmala(suraNum), basmalaH, suraName, firstJuz)
            }
            // Sura header
            if (pH + headerH > pageHeight) flush()
            addSeg(QuranSegment.SuraHeader(suraNum, suraName), headerH, suraName, firstJuz)

            // Aya content: greedily fill pages using binary search splits
            var remaining = ayaList
            while (remaining.isNotEmpty()) {
                var fc = fitCount(remaining, pageHeight - pH)
                if (fc == 0) {
                    // Nothing fits on the current (partially filled) page → flush and retry
                    if (segs.isNotEmpty()) flush()
                    fc = fitCount(remaining, pageHeight).coerceAtLeast(1) // force ≥1 to avoid loop
                }
                val chunk    = remaining.take(fc)
                val str      = ayaStr(chunk)
                val strH     = measurer.measure(str, ayaStyle, wc).size.height + gapPx
                val chunkJuz = chunk.first().juz
                addSeg(
                    QuranSegment.AyaFlow(suraNum, str, chunk.first().ayaNum, chunk.last().ayaNum),
                    strH, suraName, chunkJuz,
                )
                remaining = remaining.drop(fc)
                if (remaining.isNotEmpty()) flush()
            }
        }

        // ── Main iteration ────────────────────────────────────────────────────
        var prevSura = -1
        val suraAyas = mutableListOf<QuranAya>()

        for (aya in ayas) {
            if (aya.suraNum != prevSura) {
                if (suraAyas.isNotEmpty()) {
                    processSura(prevSura, suraAyas.first().sura, suraAyas.first().juz, suraAyas.toList())
                    suraAyas.clear()
                }
                prevSura = aya.suraNum
            }
            suraAyas += aya
        }
        if (suraAyas.isNotEmpty()) {
            processSura(prevSura, suraAyas.first().sura, suraAyas.first().juz, suraAyas)
        }

        flush()
        return result
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun toArNums(n: Int): String =
        n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
}
