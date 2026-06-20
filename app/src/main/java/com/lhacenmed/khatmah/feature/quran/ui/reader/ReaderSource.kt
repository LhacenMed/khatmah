package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.feature.mushaf.data.MushafFormat
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.ui.reader.book.Qcf4ReaderSource
import com.lhacenmed.khatmah.feature.quran.ui.reader.text.TextReaderSource

/** The two ways the reader renders a page. Chosen from the selected print's format. */
enum class ReaderMode { TEXT, QCF4 }

/**
 * Everything the reader *shell* needs from its content, independent of how a page is drawn. One
 * implementation per [ReaderMode] keeps [ReaderActivity] mode-agnostic: it only deals in page
 * counts, an aya→page map, per-page meta, and opaque page fragments.
 */
interface ReaderSource {
    val mode: ReaderMode
    val riwaya: Riwaya
    /** Page-windowed (Khatmah session) reading. Only the page-based QCF4 mushaf supports it. */
    val supportsSession: Boolean

    /** Prepares the content (building text pages if needed) and returns its total page count. */
    suspend fun prepare(): Int

    /** Packed (sura shl 32 or aya) → 0-based page index, in this content's own pagination. */
    suspend fun ayaPageIndex(): Map<Long, Int>

    /** page (1-based) → [PageMeta] for the toolbar, slider popup and page-info overlay. */
    suspend fun pageMeta(): Map<Int, PageMeta>

    /** The page fragment for [page] (1-based). */
    fun newPageFragment(page: Int): Fragment
}

/** Resolves the [ReaderSource] for [print] — the single place mode is decided. */
fun readerSourceFor(context: Context, print: MushafPrint): ReaderSource =
    if (print.format == MushafFormat.QCF4) Qcf4ReaderSource(context, print.riwaya)
    else TextReaderSource.get(context, print.riwaya)
