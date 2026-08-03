package com.lhacenmed.khatmah.feature.adhkar.ui.editor

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr
import com.lhacenmed.khatmah.feature.adhkar.data.DhikrParagraph

/**
 * Mutable working copies of a category's content, held only while the editor is open.
 *
 * They exist because a [Dhikr] is immutable and complete, whereas a form is neither: a
 * paragraph may be empty mid-typing, and a dhikr may be half-written. Drafts allow those
 * in-between states, and [toDhikr] is the single point where a draft becomes real data —
 * blank paragraphs are dropped there rather than being guarded against everywhere.
 */
enum class ParagraphType(@StringRes val labelRes: Int, @StringRes val hintRes: Int) {
    BODY(R.string.adhkar_paragraph_body,   R.string.adhkar_paragraph_body_hint),
    QURAN(R.string.adhkar_paragraph_quran, R.string.adhkar_paragraph_quran_hint),
    NOTE(R.string.adhkar_paragraph_note,   R.string.adhkar_paragraph_note_hint),
}

class ParagraphDraft(
    var type: ParagraphType = ParagraphType.BODY,
    var text: String = "",
) {
    /** Null for a blank paragraph, so empty rows simply never reach the database. */
    fun toParagraph(): DhikrParagraph? = if (text.isBlank()) null else when (type) {
        ParagraphType.BODY  -> DhikrParagraph.Body(text)
        ParagraphType.QURAN -> DhikrParagraph.Quran(text)
        ParagraphType.NOTE  -> DhikrParagraph.Note(text)
    }
}

class DhikrDraft(var repetitions: Int = 1) {

    val paragraphs = mutableListOf(ParagraphDraft())

    /** A dhikr counts as written once any one of its paragraphs has text. */
    val isValid: Boolean get() = paragraphs.any { it.text.isNotBlank() }

    fun toDhikr() = Dhikr(
        paragraphs  = paragraphs.mapNotNull { it.toParagraph() },
        repetitions = repetitions,
    )

    companion object {
        /** Builds a draft pre-filled from an existing [Dhikr]. */
        fun from(dhikr: Dhikr) = DhikrDraft(dhikr.repetitions).apply {
            paragraphs.clear()
            dhikr.paragraphs.mapTo(paragraphs) { para ->
                when (para) {
                    is DhikrParagraph.Body  -> ParagraphDraft(ParagraphType.BODY,  para.text)
                    is DhikrParagraph.Quran -> ParagraphDraft(ParagraphType.QURAN, para.text)
                    is DhikrParagraph.Note   -> ParagraphDraft(ParagraphType.NOTE, para.text)
                }
            }
        }
    }
}
