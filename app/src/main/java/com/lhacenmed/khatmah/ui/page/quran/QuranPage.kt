package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.ui.text.AnnotatedString

// ── Segment types ─────────────────────────────────────────────────────────────

/**
 * A single renderable block within a Quran display page.
 *
 * [SuraHeader] — decorative centred row with the sura name and dividers.
 * [Basmala]    — the opening Basmala line (omitted for suras 1 and 9).
 * [AyaFlow]    — concatenated aya text with inline ornate aya-number spans,
 *                rendered as a single justified [Text] so all ayas flow
 *                continuously on the same line(s).
 */
sealed class QuranSegment {
    data class SuraHeader(val num: Int, val name: String) : QuranSegment()
    data class Basmala(val suraNum: Int)                  : QuranSegment()
    data class AyaFlow(
        val suraNum:  Int,
        val text:     AnnotatedString,
        val firstAya: Int,
        val lastAya:  Int,
    ) : QuranSegment()
}

// ── Page model ────────────────────────────────────────────────────────────────

/**
 * A single display page in the Quran reader.
 *
 * [pageNum]  — 1-based sequential page number for the top bar.
 * [suraName] — the dominant sura on this page (used in the top bar title).
 * [juz]      — juz identifier taken from the first aya on this page.
 * [segments] — ordered list of segments to render top-to-bottom.
 */
data class QuranPageData(
    val pageNum:  Int,
    val suraName: String,
    val juz:      String,
    val segments: List<QuranSegment>,
)