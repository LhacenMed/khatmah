package com.lhacenmed.khatmah.feature.quran.ui.book

import android.graphics.Paint
import android.graphics.Typeface
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page

/**
 * Pure layout engine for one QCF4 page — a verbatim port of the `computeLayout`
 * already used by the Compose `QuranQcf4Page`. It is intentionally Compose-free
 * (only `android.graphics`) so [BookPageView] can call it directly from `onDraw`.
 */

private const val MIN_LINE_COUNT = 15
private const val HDR_LINE_SCALE = 1.4f  // surah-header slot height multiplier vs normal line
private const val BSML_LINE_SCALE = 0.95f // besmala slot height multiplier vs normal line
private const val HDR_NAME_SCALE = 0.50f // name glyph height as fraction of lineH

// Proportional x-offset of the glyph's built-in circle centers from each edge (0..1).
private const val CIRCLE_OFFSET = 0.21f
private const val CIRCLE_LABEL_SCALE = 0.12f
private const val CIRCLE_NUM_SCALE = 0.09f
private const val CIRCLE_GAP_SCALE = -0.05f

/** Surah header frame glyph in QCF2_QBSML (U+00F2). */
const val CONTAINER_CHAR = "ò"

private val EAST_ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

private val SURA_AYA_COUNTS_HAFS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
    123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
    34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
    60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
    28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6,
)

private val SURA_AYA_COUNTS_WARSH = intArrayOf(
    7, 285, 200, 175, 122, 167, 206, 76, 130, 109,
    121, 111, 44, 54, 99, 128, 110, 105, 99, 134,
    111, 76, 119, 62, 77, 226, 95, 88, 69, 59,
    33, 30, 73, 54, 46, 82, 182, 86, 72, 84,
    53, 50, 89, 56, 36, 34, 39, 29, 18, 45,
    60, 47, 61, 55, 77, 99, 28, 21, 24, 13,
    14, 11, 11, 18, 12, 12, 31, 52, 52, 44,
    30, 28, 18, 55, 39, 31, 50, 40, 45, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 32, 20,
    15, 21, 11, 8, 8, 20, 5, 8, 9, 11,
    10, 8, 3, 9, 5, 5, 6, 3, 6, 3,
    5, 4, 5, 6,
)

private fun ayaCount(riwaya: Riwaya, suraNum: Int): Int =
    if (suraNum in 1..114)
        (if (riwaya == Riwaya.HAFS) SURA_AYA_COUNTS_HAFS else SURA_AYA_COUNTS_WARSH)[suraNum - 1]
    else 0

private fun toEastArabic(n: Int): String =
    n.toString().map { EAST_ARABIC_DIGITS[it - '0'] }.joinToString("")

// ── Rendered units ────────────────────────────────────────────────────────────

internal data class WordRender(
    val char: String,
    val x: Float,
    val baseline: Float,
    val width: Float,
    val paint: Paint,
    /** "sura:aya" of the verse this word belongs to, or null for headers/markers. */
    val verseKey: String? = null,
)

internal data class ContainerRender(
    val x: Float,
    val baseline: Float,
    val paint: Paint,
    val circles: SurahCircles? = null,
)

internal class SurahCircles(
    val orderCx: Float,
    val ayaCx: Float,
    val labelY: Float,
    val numY: Float,
    val orderStr: String,
    val ayaStr: String,
    val labelPaint: Paint,
    val numPaint: Paint,
)

internal data class LineRender(
    val words: List<WordRender>,
    val container: ContainerRender? = null,
    /** Slot top y-coordinate in view pixels — used for highlight geometry. */
    val lineTop: Float = 0f,
    /** Slot height in view pixels — used for highlight geometry. */
    val lineHeight: Float = 0f,
)

// ── Layout computation ────────────────────────────────────────────────────────

internal fun computeLayout(
    page: Qcf4Page,
    faces: Map<String, Typeface>,
    w: Float,
    h: Float,
    textArgb: Int,
    accentArgb: Int,
    riwaya: Riwaya,
    kfgqpcFace: Typeface,
): List<LineRender> {
    val lineCount = page.lines.size
    if (lineCount == 0 || w <= 0f || h <= 0f) return emptyList()

    val hPad = w * 0.02f
    val vPad = h * 0.14f
    val usableW = w - 2f * hPad
    val usableH = h - 2f * vPad

    val lineH = (usableH / lineCount).coerceAtMost(usableH / MIN_LINE_COUNT.toFloat())
    // Max ayah-glyph size (the cap). Lines wide enough to fill the page are governed by
    // fontScale below; only the lines that don't fill the width use this size, so lowering
    // it shrinks just those without disturbing the full-width lines.
    val candWordSz = lineH * 0.62f
    val isOpening = page.page == 1 || page.page == 2
    val quranTextScale = if (isOpening) 0.82f else 1f

    // Pass 1 — measure only non-header lines.
    val probe = Paint().apply { isAntiAlias = true }
    var maxLineW = 0f
    for (line in page.lines) {
        if (line.words.any { it.type == "surah_header" }) continue
        var lineW = 0f
        for (word in line.words) {
            probe.typeface = faces[word.font] ?: Typeface.DEFAULT
            val sz = candWordSz * quranTextScale
            probe.textSize = if (word.type == "bismillah") sz * BSML_LINE_SCALE else sz
            lineW += probe.measureText(word.char)
        }
        if (lineW > maxLineW) maxLineW = lineW
    }

    val fontScale = if (maxLineW > usableW) usableW / maxLineW else 1f
    val wordFontSz = candWordSz * fontScale
    val nameFontSz = lineH * HDR_NAME_SCALE

    val slotHeights = page.lines.map { line ->
        when {
            line.words.any { it.type == "surah_header" } -> lineH * HDR_LINE_SCALE
            line.words.any { it.type == "bismillah" }    -> lineH * BSML_LINE_SCALE
            else -> lineH
        }
    }
    val openingGap = if (isOpening) lineH * 1.2f else 0f
    val totalContentH = slotHeights.sum() + openingGap
    val topOffset = vPad + (usableH - totalContentH) / 2f

    // Pass 2 — cumulative Y, per-line slot heights.
    var cumY = 0f
    var gapApplied = false
    return page.lines.mapIndexed { idx, line ->
        val isHeader = line.words.any { it.type == "surah_header" }
        if (isOpening && !isHeader && !gapApplied) {
            cumY += openingGap
            gapApplied = true
        }
        val slotH = slotHeights[idx]
        val slotTop = topOffset + cumY
        cumY += slotH

        val container: ContainerRender? = if (isHeader) run {
            val qcf2Face = faces["QCF2_QBSML"] ?: return@run null
            val p = Paint().apply {
                typeface = qcf2Face
                isAntiAlias = true
                textSize = 1f
                color = accentArgb
            }
            val natW = p.measureText(CONTAINER_CHAR)
            if (natW <= 0f) return@run null
            p.textSize = usableW / natW
            val ctrBaseline = slotTop + slotH / 2f - (p.ascent() + p.descent()) / 2f

            val suraNum = line.words.firstOrNull()?.sura ?: 0
            val circles: SurahCircles? = if (suraNum in 1..114) {
                val fm = p.fontMetrics
                val containerH = fm.descent - fm.ascent
                val circleCy = ctrBaseline + (fm.ascent + fm.descent) / 2f

                val labelFontSz = containerH * CIRCLE_LABEL_SCALE
                val numFontSz = containerH * CIRCLE_NUM_SCALE

                val labelP = Paint().apply {
                    isAntiAlias = true
                    color = accentArgb
                    textSize = labelFontSz
                    textAlign = Paint.Align.CENTER
                    typeface = kfgqpcFace
                }
                val numP = Paint().apply {
                    isAntiAlias = true
                    color = accentArgb
                    textSize = numFontSz
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }

                val labelLineH = labelP.descent() - labelP.ascent()
                val numLineH = numP.descent() - numP.ascent()
                val gap = containerH * CIRCLE_GAP_SCALE
                val stackTop = circleCy - (labelLineH + gap + numLineH) / 2f
                val labelY = stackTop - labelP.ascent()
                val numY = stackTop + labelLineH + gap - numP.ascent()

                SurahCircles(
                    orderCx = hPad + usableW * (1f - CIRCLE_OFFSET),
                    ayaCx = hPad + usableW * CIRCLE_OFFSET,
                    labelY = labelY,
                    numY = numY,
                    orderStr = toEastArabic(suraNum),
                    ayaStr = toEastArabic(ayaCount(riwaya, suraNum)),
                    labelPaint = labelP,
                    numPaint = numP,
                )
            } else null

            ContainerRender(x = hPad, baseline = ctrBaseline, paint = p, circles = circles)
        } else null

        val baseline: Float = if (isHeader && container != null) {
            probe.typeface = faces["QCF4_QBSML"] ?: Typeface.DEFAULT
            probe.textSize = nameFontSz
            slotTop + slotH / 2f - (probe.ascent() + probe.descent()) / 2f
        } else {
            slotTop + slotH * 0.78f
        }

        val measured = line.words.map { word ->
            val sz = when {
                isHeader -> nameFontSz
                word.type == "bismillah" -> wordFontSz * quranTextScale * BSML_LINE_SCALE
                else -> wordFontSz * quranTextScale
            }
            val p = Paint().apply {
                typeface = faces[word.font] ?: Typeface.DEFAULT
                textSize = sz
                isAntiAlias = true
                color = when (word.type) {
                    "surah_header", "bismillah", "end", "aya_end" -> accentArgb
                    else -> textArgb
                }
            }
            Triple(word, p, p.measureText(word.char))
        }

        // Center the line block horizontally (RTL: start from right edge).
        val totalW = measured.sumOf { it.third.toDouble() }.toFloat()
        var x = (w + totalW) / 2f

        LineRender(
            words = measured.map { (word, paint, ww) ->
                WordRender(word.char, x - ww, baseline, ww, paint, word.verseKey).also { x -= ww }
            },
            container = container,
            lineTop = slotTop,
            lineHeight = slotH,
        )
    }
}
