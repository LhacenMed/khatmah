package com.lhacenmed.khatmah.ui.page.tabs.adhkar.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.data.adhkar.DhikrParagraph
import java.util.UUID

// ── Draft models (composition-scoped, not persisted) ─────────────────────────

enum class ParagraphDraftType(val labelRes: Int) {
    BODY(R.string.adhkar_paragraph_body),
    QURAN(R.string.adhkar_paragraph_quran),
    NOTE(R.string.adhkar_paragraph_note),
}

class ParagraphDraftState {
    var type by mutableStateOf(ParagraphDraftType.BODY)
    var text by mutableStateOf("")
    val key = UUID.randomUUID().toString()

    fun toParagraph(): DhikrParagraph? =
        if (text.isBlank()) null else when (type) {
            ParagraphDraftType.BODY  -> DhikrParagraph.Body(text)
            ParagraphDraftType.QURAN -> DhikrParagraph.Quran(text)
            ParagraphDraftType.NOTE  -> DhikrParagraph.Note(text)
        }
}

class DhikrDraftState {
    val key = UUID.randomUUID().toString()
    var repetitions by mutableIntStateOf(1)
    val paragraphs = mutableStateListOf(ParagraphDraftState())

    val isValid: Boolean get() = paragraphs.any { it.text.isNotBlank() }

    fun toDhikr(): Dhikr = Dhikr(
        paragraphs  = paragraphs.mapNotNull { it.toParagraph() },
        repetitions = repetitions,
    )

    companion object {
        /** Builds a draft pre-filled from an existing [Dhikr]. */
        fun from(dhikr: Dhikr): DhikrDraftState = DhikrDraftState().apply {
            repetitions = dhikr.repetitions
            paragraphs.clear()
            paragraphs.addAll(dhikr.paragraphs.map { para ->
                ParagraphDraftState().apply {
                    when (para) {
                        is DhikrParagraph.Body  -> { type = ParagraphDraftType.BODY;  text = para.text }
                        is DhikrParagraph.Quran -> { type = ParagraphDraftType.QURAN; text = para.text }
                        is DhikrParagraph.Note  -> { type = ParagraphDraftType.NOTE;  text = para.text }
                    }
                }
            })
        }
    }
}