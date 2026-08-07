package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One styled chunk of the preview aya: a single QCF4 glyph word with its own face, or — in the
 * text fallback — the whole aya in the riwaya's calligraphic face. Runs are in Arabic reading
 * order; [AyaPreviewView] lays them out right-to-left.
 */
data class PreviewRun(val text: String, val typeface: Typeface)

/**
 * Resolves the sample shown in the reader-settings brightness preview: the opening aya of
 * al-Fātiḥah (ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ) in the selected riwaya — never the basmala,
 * which Hafs numbers as aya 1 and Warsh prints separately.
 *
 * Rendering follows what the reader itself would show: the riwaya's downloaded QCF4 ligatures when
 * they're installed (identical glyphs to the book reader), otherwise the text reader's font over
 * the bundled verse text. Both paths stay riwaya-driven — no per-riwaya branch lives here.
 */
object PreviewAya {

    private const val FATIHA = 1
    /** Al-Fātiḥah is page 1 in every 604-page madinah QCF4 layout. */
    private const val FATIHA_PAGE = 1
    /** Word types are "word" and "word_with_mark"; the rest are decorations (aya_end, headers). */
    private const val TYPE_WORD_PREFIX = "word"

    /** The preview runs for [riwaya], or empty when neither source has the verse. */
    suspend fun load(context: Context, riwaya: Riwaya): List<PreviewRun> =
        withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            val repo = QuranTextRepository(ctx)
            val aya = openingAya(repo, riwaya)
            glyphRuns(ctx, riwaya, aya) ?: textRun(ctx, repo, riwaya, aya)
        }

    /**
     * Aya number of ٱلۡحَمۡدُ within al-Fātiḥah: riwayat that print the basmala as a separate line
     * start their numbering at it (Warsh → 1); those that count it as aya 1 don't (Hafs → 2).
     */
    private suspend fun openingAya(repo: QuranTextRepository, riwaya: Riwaya): Int =
        if (repo.bismillahMap(riwaya.dbKey)[FATIHA] == true) 1 else 2

    /** QCF4 words of the verse, each with its own glyph face — null when not downloaded. */
    private suspend fun glyphRuns(ctx: Context, riwaya: Riwaya, aya: Int): List<PreviewRun>? {
        val repo = Qcf4Repository.get(ctx, riwaya)
        if (!repo.isFullyDownloaded()) return null
        val verseKey = "$FATIHA:$aya"
        return runCatching {
            repo.pageData(FATIHA_PAGE).lines
                .asSequence()
                .flatMap { it.words.asSequence() }
                .filter { it.verseKey == verseKey && it.type.startsWith(TYPE_WORD_PREFIX) }
                .map { PreviewRun(it.char, repo.typefaceFor(it.font)) }
                .toList()
                .ifEmpty { null }
        }.getOrNull()
    }

    /** The bundled verse text in the riwaya's calligraphic face — the text reader's rendering. */
    private suspend fun textRun(
        ctx: Context,
        repo: QuranTextRepository,
        riwaya: Riwaya,
        aya: Int,
    ): List<PreviewRun> {
        val text = repo.verseText(riwaya.dbKey, FATIHA, aya) ?: return emptyList()
        val face = ResourcesCompat.getFont(ctx, riwaya.bodyFontRes) ?: Typeface.DEFAULT
        return listOf(PreviewRun(text, face))
    }
}
