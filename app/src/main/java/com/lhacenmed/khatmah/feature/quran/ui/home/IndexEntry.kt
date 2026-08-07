package com.lhacenmed.khatmah.feature.quran.ui.home

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.data.normalizeArabic

/** The three ways the mushaf is indexed. Declaration order is the tab order. */
enum class IndexKind(@param:StringRes val tabRes: Int) {
    SURAH(R.string.full_index_tab_surahs),
    JUZ(R.string.full_index_tab_ajza),
    HIZB(R.string.full_index_tab_ahzab),
}

/**
 * One line of the index, whichever index it belongs to.
 *
 * A surah, a juz' and a hizb differ only in what they are called and where they begin — they are
 * all "a name, and the page it opens at". Holding them in one shape is what lets the three tabs
 * share a row layout and an adapter, and what lets search run across all of them at once instead of
 * once per tab.
 *
 * [page] is resolved against the pagination of the print currently selected, so the row opens the
 * reader exactly where it says it will.
 */
data class IndexEntry(
    val kind: IndexKind,
    val num: Int,
    val title: String,
    /** Where this division begins, for the divisions; a surah is its own answer and leaves it null. */
    val subtitle: String?,
    val page: Int,
    val suraNum: Int,
    val ayaNum: Int,
) {
    /** Stable across reloads: a kind never holds two of the same number. */
    val id: Long get() = kind.ordinal * ID_STRIDE + num

    /**
     * What search matches against — the words stripped of diacritics, plus the number, so a hizb
     * can be reached by typing it as readily as by naming the surah it starts in.
     */
    val searchKey: String = "$title ${subtitle.orEmpty()} $num".normalizeArabic()

    private companion object {
        const val ID_STRIDE = 1000L
    }
}

/**
 * The whole index, loaded once per print.
 *
 * Both readings of it are prepared here rather than at each use: the tabs want it split by kind,
 * search wants it flat, and computing either on every keystroke or every bind would be work repeated
 * for an answer that has not changed.
 */
class IndexData(val all: List<IndexEntry> = emptyList()) {

    val byKind: Map<IndexKind, List<IndexEntry>> = all.groupBy { it.kind }

    /** Matches on any word of a row, so "بقرة" finds the surah and the ajza' that open in it. */
    fun search(query: String): List<IndexEntry> {
        val needle = query.trim().normalizeArabic()
        if (needle.isEmpty()) return emptyList()
        return all.filter { it.searchKey.contains(needle) }
    }
}
