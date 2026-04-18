package com.lhacenmed.khatmah.data.adhkar

/**
 * A single visual content block within one dhikr entry.
 *
 * Three concrete types drive the three visual styles in the reader:
 *  [Body]  — main supplication text (standard size, centered).
 *  [Quran] — Quranic verse (slightly larger, Uthmanic glyph style preserved in the raw string).
 *  [Note]  — hadith source or contextual footnote (small, muted primary color).
 *
 * The raw text of every type is plain Unicode so the share payload is
 * readable in any messaging app without special formatting.
 */
sealed class DhikrParagraph {
    data class Body(val text: String)  : DhikrParagraph()
    data class Quran(val text: String) : DhikrParagraph()
    data class Note(val text: String)  : DhikrParagraph()
}

/**
 * A single dhikr item.
 *
 * @param paragraphs  Ordered list of content blocks rendered in the body.
 * @param repetitions How many times the user should read the dhikr (≥ 1).
 *                    A value of 1 skips the repetition counter UI entirely.
 */
data class Dhikr(
    val paragraphs:   List<DhikrParagraph>,
    val repetitions:  Int = 1,
) {
    /**
     * Plain-text version for Android share sheet — all blocks joined by
     * double newlines so the message stays readable without styling.
     */
    val shareText: String
        get() = paragraphs.joinToString("\n\n") { p ->
            when (p) {
                is DhikrParagraph.Body  -> p.text
                is DhikrParagraph.Quran -> p.text
                is DhikrParagraph.Note  -> p.text
            }
        }
}