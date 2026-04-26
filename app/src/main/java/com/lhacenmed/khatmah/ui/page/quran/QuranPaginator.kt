package com.lhacenmed.khatmah.ui.page.quran

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.quran.QuranAya
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Paginates the full Quran into [QuranPageData] pages using real Android font
 * metrics via [StaticLayout].
 *
 * Each page is exactly [pageHeightPx] pixels tall and [contentWidthPx] pixels wide.
 * Content fills top-to-bottom; when an aya overflows the remaining height it is
 * split at the last fitting line — the remainder continues naturally on the next page.
 * The aya-end ornament is suppressed on all but the final slice of a split aya.
 *
 * Text is measured using [density] × [fontScale] (sp→px) so pagination stays
 * accurate when the user has a non-default system font scale.
 */
object QuranPaginator {

    private const val AYA_SP    = 28f
    private const val LINE_MULT = 40f / 28f   // lineHeight / fontSize ratio matching TextStyle

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * @param context        used to load the Warsh typeface from res/font
     * @param ayas           raw aya list from [QuranRepository.allAyas]
     * @param pageHeightPx   full usable page height in pixels
     * @param contentWidthPx usable content width in pixels
     * @param density        physical screen density (dp → px)
     * @param fontScale      system font scale (sp multiplier, from [LocalDensity])
     */
    suspend fun build(
        context:        Context,
        ayas:           List<QuranAya>,
        pageHeightPx:   Int,
        contentWidthPx: Int,
        density:        Float,
        fontScale:      Float,
    ): List<QuranPageData> = withContext(Dispatchers.Default) {

        val valid = ayas.filter { it.aya.isNotBlank() }
        if (valid.isEmpty() || pageHeightPx <= 0 || contentWidthPx <= 0) return@withContext emptyList()

        // sp → px must include fontScale so StaticLayout matches Compose text rendering.
        val spPx     = density * fontScale
        val ayaPaint = makePaint(
            withContext(Dispatchers.IO) { loadTypeface(context, R.font.warsh_regular) },
            AYA_SP * spPx,
        )

        // Nominal line height for headers and basmala (no StaticLayout for these items).
        val lineH  = (AYA_SP * spPx * LINE_MULT).toInt()
        // dp padding uses density only — dp is independent of font scale.
        val padPx  = (8 * density).toInt()
        val usable = pageHeightPx - padPx * 2

        // Measure every aya in parallel using real font metrics.
        // Measured without the ornament so layout.text == aya.aya,
        // simplifying clean text extraction for split slices.
        val layouts = valid.map { aya ->
            async { makeLayout(aya.aya, ayaPaint, contentWidthPx) }
        }.awaitAll()

        paginate(valid, layouts, usable, lineH)
    }

    // ── Paginator core ────────────────────────────────────────────────────────

    private fun paginate(
        ayas:    List<QuranAya>,
        layouts: List<StaticLayout>,
        pageH:   Int,
        lineH:   Int,
    ): List<QuranPageData> {

        val pages       = mutableListOf<QuranPageData>()
        var pageNum     = 1
        val curSegs     = mutableListOf<QuranSegment>()
        var used        = 0
        var pageSura    = ""
        var pageJuz     = ""
        val headedSuras = mutableSetOf<Int>()

        fun setMeta(sura: String, juz: String) {
            if (curSegs.isEmpty()) { pageSura = sura; pageJuz = juz }
        }

        fun flush() {
            if (curSegs.isEmpty()) return
            pages += QuranPageData(pageNum++, pageSura, pageJuz, curSegs.toList())
            curSegs.clear(); used = 0; pageSura = ""; pageJuz = ""
        }

        fun addSeg(seg: QuranSegment, costPx: Int, sura: String, juz: String) {
            setMeta(sura, juz); curSegs += seg; used += costPx
        }

        // ── Special centered opening pages ────────────────────────────────────

        fun centeredPage(suraAyas: List<IndexedValue<QuranAya>>, hasBasmala: Boolean) {
            val first    = suraAyas.first().value
            val suraNum  = first.suraNum
            val suraName = first.sura.removePrefix("سورة ").trim()
            val segs = buildList<QuranSegment> {
                add(QuranSegment.SuraHeader(suraNum, suraName))
                if (hasBasmala) add(QuranSegment.Basmala(suraNum))
                suraAyas.forEach { (_, a) ->
                    add(QuranSegment.Aya(a.suraNum, a.ayaNum, a.aya))
                }
            }
            pages += QuranPageData(pageNum++, suraName, first.juz, segs, centered = true)
            headedSuras += suraNum
        }

        val indexed    = ayas.mapIndexed { i, a -> IndexedValue(i, a) }
        val fatiha     = indexed.filter { it.value.suraNum == 1 }
        val baqOpening = indexed.filter { it.value.suraNum == 2 && it.value.ayaNum <= 5 }
        if (fatiha.isNotEmpty())     centeredPage(fatiha,     hasBasmala = false)
        if (baqOpening.isNotEmpty()) centeredPage(baqOpening, hasBasmala = true)

        // ── Normal pagination ─────────────────────────────────────────────────

        val normalAyas = indexed.filter {
            it.value.suraNum != 1 && !(it.value.suraNum == 2 && it.value.ayaNum <= 5)
        }

        var prevSura = -1
        val suraBuf  = mutableListOf<IndexedValue<QuranAya>>()

        fun flushSura() {
            val first    = suraBuf.firstOrNull()?.value ?: return
            val suraNum  = first.suraNum
            val suraName = first.sura.removePrefix("سورة ").trim()
            val firstJuz = first.juz
            val skip     = suraNum in headedSuras
            val basmala  = !skip && suraNum != 1 && suraNum != 9
            val hdrCost  = if (skip) 0 else lineH + (if (basmala) lineH else 0)

            if (!skip) {
                // Orphan guard: don't strand a header alone at the bottom of a page.
                if (curSegs.isNotEmpty() && used + hdrCost > pageH) flush()
                addSeg(QuranSegment.SuraHeader(suraNum, suraName), lineH, suraName, firstJuz)
                if (basmala) addSeg(QuranSegment.Basmala(suraNum), lineH, suraName, firstJuz)
                if (used >= pageH) flush()
            }

            for ((origIdx, aya) in suraBuf) {
                val layout = layouts[origIdx]
                // Use StaticLayout.height — exact pixel total, no integer-truncation drift.
                val ayaH   = layout.height

                // Ensure space for at least one line before attempting to add.
                if (curSegs.isNotEmpty() && pageH - used < lineH) {
                    flush(); setMeta(suraName, aya.juz)
                }

                if (used + ayaH <= pageH) {
                    // Whole aya fits — add as-is.
                    addSeg(
                        QuranSegment.Aya(aya.suraNum, aya.ayaNum, aya.aya),
                        ayaH, suraName, aya.juz,
                    )
                    if (used >= pageH) flush()
                } else {
                    // Aya overflows — split at line boundaries across pages.
                    var lineStart  = 0
                    val totalLines = layout.lineCount

                    while (lineStart < totalLines) {
                        val avail     = pageH - used
                        // topOffset: accumulated height before lineStart (0 for first line).
                        val topOffset = if (lineStart > 0) layout.getLineBottom(lineStart - 1) else 0

                        // Advance lineEnd until the next line would exceed avail.
                        // Always advance at least once to prevent infinite loop on tiny avail.
                        var lineEnd = lineStart
                        while (lineEnd < totalLines) {
                            val h = layout.getLineBottom(lineEnd) - topOffset
                            if (h > avail && lineEnd > lineStart) break
                            lineEnd++
                        }
                        lineEnd = minOf(lineEnd, totalLines)
                        val isLast = lineEnd == totalLines

                        // Extract exactly the characters on lines [lineStart, lineEnd).
                        // For the last slice use layout.text.length (== aya.aya.length)
                        // so we never return text past the aya boundary.
                        val sliceText = layout.text.substring(
                            layout.getLineStart(lineStart),
                            if (isLast) layout.text.length else layout.getLineEnd(lineEnd - 1),
                        ).trim()

                        // Exact pixel cost for these lines via StaticLayout cumulative bottoms.
                        val cost = layout.getLineBottom(lineEnd - 1) - topOffset

                        addSeg(
                            QuranSegment.Aya(aya.suraNum, aya.ayaNum, sliceText, showOrnament = isLast),
                            cost, suraName, aya.juz,
                        )
                        if (used >= pageH || !isLast) flush()
                        lineStart = lineEnd
                        if (!isLast) setMeta(suraName, aya.juz)
                    }
                }
            }
            suraBuf.clear()
        }

        for (iv in normalAyas) {
            if (iv.value.suraNum != prevSura) {
                if (suraBuf.isNotEmpty()) flushSura()
                prevSura = iv.value.suraNum
            }
            suraBuf += iv
        }
        if (suraBuf.isNotEmpty()) flushSura()
        flush()

        return pages
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadTypeface(ctx: Context, fontRes: Int): Typeface =
        ResourcesCompat.getFont(ctx, fontRes) ?: Typeface.DEFAULT

    private fun makePaint(tf: Typeface, sizePx: Float) = TextPaint().apply {
        typeface    = tf
        textSize    = sizePx
        isAntiAlias = true
    }

    private fun makeLayout(text: String, paint: TextPaint, widthPx: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_MULT)
            .setIncludePad(false)
            .build()
}