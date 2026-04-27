package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import com.lhacenmed.khatmah.ui.theme.WarshSuraNameFamily

// ── Render units ──────────────────────────────────────────────────────────────

/**
 * Typed render units produced by [groupSegments].
 *
 * [Header]  — single sura name line, rendered centered.
 * [Basmala] — basmala line, rendered centered.
 * [AyaRun]  — one or more consecutive ayas merged into a flowing paragraph.
 */
internal sealed interface PageRun {
    data class Header(val name: String) : PageRun
    data object Basmala                 : PageRun
    data class AyaRun(val ayas: List<QuranSegment.Aya>) : PageRun {
        /**
         * Builds the [AnnotatedString] for this run.
         * No trailing separator after the last aya — avoids a phantom partial
         * line from the justify engine in RTL layout.
         */
        fun annotated(primary: Color, onBg: Color): AnnotatedString = buildAnnotatedString {
            ayas.forEachIndexed { i, aya ->
                withStyle(SpanStyle(color = onBg)) { append(aya.text) }
                append(" ")
                withStyle(SpanStyle(color = primary, fontSize = 25.sp)) {
                    append(toArNums(aya.ayaNum))
                }
                if (i < ayas.lastIndex) append(" ")
            }
        }
    }
}

/** Collapses a flat [QuranSegment] list into [PageRun] items. Consecutive ayas merge. */
internal fun groupSegments(segments: List<QuranSegment>): List<PageRun> {
    val runs   = mutableListOf<PageRun>()
    val ayaBuf = mutableListOf<QuranSegment.Aya>()

    fun flushAyas() {
        if (ayaBuf.isNotEmpty()) { runs += PageRun.AyaRun(ayaBuf.toList()); ayaBuf.clear() }
    }

    segments.forEach { seg ->
        when (seg) {
            is QuranSegment.SuraHeader -> { flushAyas(); runs += PageRun.Header(seg.name) }
            is QuranSegment.Basmala    -> { flushAyas(); runs += PageRun.Basmala }
            is QuranSegment.Aya        -> ayaBuf += seg
        }
    }
    flushAyas()
    return runs
}

// ── Page composable ───────────────────────────────────────────────────────────

/**
 * Renders one Quran page, vertically centered in the screen.
 *
 * [QuranPageData.centered] controls aya text alignment:
 *   true  → [TextAlign.Center] — short special pages (Al-Fatiha, Al-Baqarah intro).
 *   false → [TextAlign.Justify] — standard full pages.
 */
@Composable
internal fun PageContent(page: QuranPageData) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg    = MaterialTheme.colorScheme.onBackground
    val runs    = remember(page.pageNum) { groupSegments(page.segments) }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        runs.forEach { run ->
            when (run) {
                is PageRun.Header  -> SuraHeaderText(name = run.name, primary = primary)
                is PageRun.Basmala -> BasmalaText(primary = primary)
                is PageRun.AyaRun  -> AyaFlowText(
                    annotated = run.annotated(primary, onBg),
                    centered  = page.centered,
                )
            }
        }
    }
}

// ── Segment composables ───────────────────────────────────────────────────────

/** Sura name centered between two dividers, using [WarshSuraNameFamily]. */
@Composable
private fun SuraHeaderText(name: String, primary: Color) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
        )
        Text(
            text  = name,
            style = TextStyle(
                fontFamily    = WarshSuraNameFamily,
                fontSize      = 28.sp,
                lineHeight    = 40.sp,
                textDirection = TextDirection.Rtl,
            ),
            color    = primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier  = Modifier.weight(1f),
            color     = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.8.dp,
        )
    }
}

/** Basmala line centered and tinted in [primary]. */
@Composable
private fun BasmalaText(primary: Color) {
    Text(
        text      = "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ",
        style     = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
        ),
        textAlign = TextAlign.Center,
        color     = primary,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

/**
 * Flowing aya paragraph.
 * [centered] true  → [TextAlign.Center] for short special pages.
 * [centered] false → [TextAlign.Justify] for standard full pages.
 */
@Composable
private fun AyaFlowText(annotated: AnnotatedString, centered: Boolean) {
    Text(
        text     = annotated,
        style    = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = if (centered) TextAlign.Center else TextAlign.Justify,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}