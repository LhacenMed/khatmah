package com.lhacenmed.khatmah.ui.page.tabs.adhkar.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.data.adhkar.DhikrParagraph
import com.lhacenmed.khatmah.ui.theme.WarshFamily

// ── Font size cycle ───────────────────────────────────────────────────────────

/**
 * Three-step reading font size that cycles on each tap of the resize button.
 * [bodyScale] scales general text; [quranScale] scales Quranic verse text,
 * which starts slightly larger to preserve the traditional Uthmanic appearance.
 */
enum class DhikrFontSize(val bodyScale: Float, val quranScale: Float) {
    SMALL(0.82f, 0.88f),
    MEDIUM(1f,   1f),
    LARGE(1.22f, 1.16f),
}

fun DhikrFontSize.next() = when (this) {
    DhikrFontSize.SMALL  -> DhikrFontSize.MEDIUM
    DhikrFontSize.MEDIUM -> DhikrFontSize.LARGE
    DhikrFontSize.LARGE  -> DhikrFontSize.SMALL
}

// ── Aya number annotation ─────────────────────────────────────────────────────

/**
 * Regex matching aya number tokens embedded in Quranic text:
 *  • Circled Unicode digits  ①–⑳  (U+2460–U+2473)
 *  • ASCII digit sequences   123
 *  • Arabic-Indic sequences  ٢٥٣
 */
private val ayaNumberRegex = Regex("[\u2460-\u2473]|[0-9٠-٩]+")

/**
 * Builds an [AnnotatedString] where aya number tokens are tinted with [numberColor]
 * and all other characters use [bodyColor]. Processes the string in a single pass.
 */
private fun quranAnnotated(
    text:        String,
    bodyColor:   Color,
    numberColor: Color,
) = buildAnnotatedString {
    var cursor = 0
    ayaNumberRegex.findAll(text).forEach { match ->
        if (cursor < match.range.first) {
            pushStyle(SpanStyle(color = bodyColor))
            append(text.substring(cursor, match.range.first))
            pop()
        }
        pushStyle(SpanStyle(color = numberColor, fontSize = 25.sp))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        pushStyle(SpanStyle(color = bodyColor))
        append(text.substring(cursor))
        pop()
    }
}

// ── Dhikr body ────────────────────────────────────────────────────────────────

/**
 * Scrollable content area for one [Dhikr].
 *
 * Paragraph rendering by type:
 *  [DhikrParagraph.Body]  — standard body text, right-aligned.
 *  [DhikrParagraph.Quran] — Quranic verse rendered in [WarshFamily]; aya numbers
 *                           are tinted with a muted primary color to visually
 *                           separate them from the verse text.
 *  [DhikrParagraph.Note]  — small muted footnote in primary tint.
 *
 * Line heights are proportional to font size to keep Arabic diacritics legible
 * at all three [DhikrFontSize] steps.
 */
@Composable
fun DhikrBody(dhikr: Dhikr, fontSize: DhikrFontSize) {
    val bodySp  = (24f * fontSize.bodyScale).sp
    val quranSp = (27f * fontSize.quranScale).sp
    val noteSp  = (16f * fontSize.bodyScale).sp

    val quranBodyColor  = MaterialTheme.colorScheme.onBackground
    val ayaNumberColor  = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        dhikr.paragraphs.forEachIndexed { i, paragraph ->
            if (i > 0) Spacer(Modifier.height(22.dp))
            when (paragraph) {
                is DhikrParagraph.Body -> Text(
                    text       = paragraph.text,
                    fontSize   = bodySp,
                    lineHeight = (bodySp.value * 1.85f).sp,
                    textAlign  = TextAlign.Start,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Normal,
                )
                is DhikrParagraph.Quran -> Text(
                    text       = quranAnnotated(
                        text        = paragraph.text,
                        bodyColor   = quranBodyColor,
                        numberColor = ayaNumberColor,
                    ),
                    fontFamily = WarshFamily,
                    fontSize   = quranSp,
                    lineHeight = (quranSp.value * 2.0f).sp,
                    textAlign  = TextAlign.Start,
                    fontWeight = FontWeight.Normal,
                )
                is DhikrParagraph.Note -> Text(
                    text       = paragraph.text,
                    fontSize   = noteSp,
                    lineHeight = (noteSp.value * 1.65f).sp,
                    textAlign  = TextAlign.Start,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}