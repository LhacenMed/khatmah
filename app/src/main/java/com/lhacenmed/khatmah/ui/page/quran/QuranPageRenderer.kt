package com.lhacenmed.khatmah.ui.page.quran

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import com.lhacenmed.khatmah.ui.theme.WarshSuraNameFamily

// ── Render units ──────────────────────────────────────────────────────────────

sealed interface PageRun {
    data class Header(val name: String) : PageRun
    data object Basmala                 : PageRun
    /**
     * One or more consecutive ayas merged into a flowing paragraph.
     * [ayaRanges] maps each aya to its [start, end) char range within the
     * full annotated string so tap/long-press can identify the target aya.
     */
    data class AyaRun(val ayas: List<QuranSegment.Aya>) : PageRun
}

/** Collapses a flat [QuranSegment] list into [PageRun] items. Consecutive ayas merge. */
internal fun groupSegments(segments: List<QuranSegment>): List<PageRun> {
    val runs   = mutableListOf<PageRun>()
    val ayaBuf = mutableListOf<QuranSegment.Aya>()

    fun flushAyas() {
        if (ayaBuf.isNotEmpty()) { runs += PageRun.AyaRun(ayaBuf.toList()); ayaBuf.clear() }
    }

    for (seg in segments) {
        when (seg) {
            is QuranSegment.SuraHeader -> { flushAyas(); runs += PageRun.Header(seg.name) }
            is QuranSegment.Basmala    -> { flushAyas(); runs += PageRun.Basmala }
            is QuranSegment.Aya        -> ayaBuf += seg
        }
    }
    flushAyas()
    return runs
}

// ── Annotated string builder ──────────────────────────────────────────────────

/**
 * Builds the [AnnotatedString] for an [AyaRun] alongside a list of
 * (startChar, endChar, aya) triples for hit-testing long-press offsets.
 *
 * Per-aya layout:  {ayaText} {space} {ayaNumOrnament}  [space between ayas]
 * The highlighted aya gets a tinted background across its full span.
 */
internal fun buildAyaRunAnnotated(
    ayas:        List<QuranSegment.Aya>,
    selectedAya: Pair<Int, Int>?,
    primary:     Color,
    onBg:        Color,
): Pair<AnnotatedString, List<Triple<Int, Int, QuranSegment.Aya>>> {
    val ranges = mutableListOf<Triple<Int, Int, QuranSegment.Aya>>()
    val str = buildAnnotatedString {
        ayas.forEachIndexed { i, aya ->
            val start       = length
            val highlighted = selectedAya?.first == aya.suraNum &&
                    selectedAya?.second == aya.ayaNum
            val bgAlpha     = if (highlighted) 0.12f else 0f

            withStyle(SpanStyle(background = primary.copy(alpha = bgAlpha))) {
                withStyle(SpanStyle(color = if (highlighted) primary else onBg)) {
                    append(aya.text)
                }
                append(" ")
                withStyle(SpanStyle(color = primary, fontSize = 25.sp)) {
                    append(toArNums(aya.ayaNum))
                }
            }
            ranges += Triple(start, length, aya)
            if (i < ayas.lastIndex) append(" ")
        }
    }
    return str to ranges
}

// ── Page composable ───────────────────────────────────────────────────────────

/**
 * Renders one Quran page, vertically centered in the screen.
 *
 * [selectedAya]    — (suraNum, ayaNum) of the highlighted aya, or null.
 * [onAyaLongPress] — called when the user long-presses within a run.
 * [onTap]          — forwarded from aya text areas so the screen-level tap-to-toggle
 *                    bars still fires even when the user taps on aya text.
 */
@Composable
internal fun PageContent(
    page:            QuranPageData,
    selectedAya:     Pair<Int, Int>? = null,
    onAyaLongPress:  (suraNum: Int, ayaNum: Int) -> Unit = { _, _ -> },
    onTap:           () -> Unit = {},
) {
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
                    run         = run,
                    selectedAya = selectedAya,
                    primary     = primary,
                    onBg        = onBg,
                    centered    = page.centered,
                    onLongPress = onAyaLongPress,
                    onTap       = onTap,
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
 * Flowing aya paragraph with per-aya hit testing.
 *
 * Uses [TextLayoutResult] to map a tap/long-press offset to the correct aya
 * in the merged run. Both [onTap] and [onLongPress] are handled inside a single
 * [detectTapGestures] so neither gesture is consumed without the other.
 */
@Composable
private fun AyaFlowText(
    run:         PageRun.AyaRun,
    selectedAya: Pair<Int, Int>?,
    primary:     Color,
    onBg:        Color,
    centered:    Boolean,
    onLongPress: (suraNum: Int, ayaNum: Int) -> Unit,
    onTap:       () -> Unit,
) {
    var layoutResult: TextLayoutResult? = remember { null }

    val (annotated, ranges) = remember(run, selectedAya, primary, onBg) {
        buildAyaRunAnnotated(run.ayas, selectedAya, primary, onBg)
    }

    Text(
        text         = annotated,
        style        = TextStyle(
            fontFamily    = WarshFamily,
            fontSize      = 28.sp,
            lineHeight    = 40.sp,
            textDirection = TextDirection.Rtl,
            textAlign     = if (centered) TextAlign.Center else TextAlign.Justify,
        ),
        onTextLayout = { layoutResult = it },
        modifier     = Modifier
            .fillMaxWidth()
            .pointerInput(run) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { offset ->
                        val layout     = layoutResult ?: return@detectTapGestures
                        val charOffset = layout.getOffsetForPosition(offset)
                        val hit        = ranges.firstOrNull { (start, end, _) ->
                            charOffset in start until end
                        }
                        hit?.let { (_, _, aya) -> onLongPress(aya.suraNum, aya.ayaNum) }
                    },
                )
            },
    )
}