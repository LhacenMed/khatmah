package com.lhacenmed.khatmah.feature.quran.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.data.DivType
import com.lhacenmed.khatmah.feature.quran.data.DivisionsRiwaya
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.db.DivisionEntity
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A hizb is four rub' al-hizb, so every fourth marker opens one. */
private const val RUBS_PER_HIZB = 4

/**
 * Builds the index for the print currently selected.
 *
 * The three indexes come from one read and are published together, because they are one answer to
 * one question — where everything is in *this* mushaf. Hizb markers are not stored: the bundled
 * riwaya data records the 240 rub' al-hizb, and a hizb is every fourth of them, so the sixty are
 * derived rather than duplicated.
 *
 * Everything is rebuilt when the selected print changes. The markers themselves are shared (see
 * [DivisionsRiwaya]), but every print paginates on its own — Hafs and Warsh differ, and each QCF4
 * print differs again — so an index built for one print would send the reader to the wrong page in
 * another.
 */
class FullIndexViewModel(context: Context) : ViewModel() {

    private val _index = MutableStateFlow(IndexData())
    val index: StateFlow<IndexData> = _index.asStateFlow()

    init {
        val appContext = context.applicationContext
        // StateFlow only emits on change, so this fires once per real switch of print.
        viewModelScope.launch {
            MushafPrefs.selected.collect { print -> _index.value = load(appContext, print) }
        }
    }

    private suspend fun load(appContext: Context, print: MushafPrint): IndexData =
        withContext(Dispatchers.IO) {
            val riwaya = print.riwaya.dbKey
            val dao = MushafDb.get(appContext).dao()
            val surahs = QuranTextRepository(appContext).surahList(riwaya)
            val pageStarts = dao.allPageStarts(riwaya)
            // Markers from [DivisionsRiwaya]; their pages from the print actually selected.
            val juzaa = dao.divisions(DivisionsRiwaya, DivType.JUZ)
            val ruba3 = dao.divisions(DivisionsRiwaya, DivType.RUB)
            val nameOf = dao.surahs(riwaya).associate { it.num to it.name }

            // Pages resolved from the same pagination the reader uses (see [pageResolver]).
            val pageOf = pageResolver(appContext, print, pageStarts)

            val entries = buildList {
                surahs.forEach { surah ->
                    add(IndexEntry(
                        kind = IndexKind.SURAH,
                        num = surah.num,
                        title = surah.name,
                        subtitle = null,
                        page = pageOf(surah.num, 1),
                        suraNum = surah.num,
                        ayaNum = 1,
                    ))
                }
                juzaa.forEach { juz ->
                    add(juz.toEntry(IndexKind.JUZ, juz.num, R.string.full_index_juz, appContext, nameOf, pageOf))
                }
                ruba3.hizbStarts().forEach { (hizb, marker) ->
                    add(marker.toEntry(IndexKind.HIZB, hizb, R.string.full_index_hizb, appContext, nameOf, pageOf))
                }
            }
            IndexData(entries)
        }

    /**
     * Page lookup for an aya, matching the reader the [print] opens in. QCF4 prints render their own
     * downloaded layout (e.g. Warsh QCF4 is paginated independently of the generic 604-page Warsh),
     * so their surah/juz' pages come from the QCF4 verse→page index; other prints use
     * `mushaf_page_start`. Falls back to page starts when the QCF4 index isn't populated yet.
     */
    private suspend fun pageResolver(
        appContext: Context,
        print: MushafPrint,
        pageStarts: List<PageStartEntity>,
    ): (sura: Int, aya: Int) -> Int {
        val byStart: (Int, Int) -> Int = { sura, aya -> pageFor(pageStarts, sura, aya) }
        if (!print.isQcf4) return byStart

        val source = Qcf4Repository.get(appContext, print.riwaya)
        val ayaPage = runCatching { source.ayaPageIndex() }.getOrDefault(emptyMap())
        if (ayaPage.isEmpty()) return byStart
        return { sura, aya ->
            ayaPage[(sura.toLong() shl 32) or aya.toLong()]?.plus(1) ?: byStart(sura, aya)
        }
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FullIndexViewModel(ctx.applicationContext) as T
    }
}

/**
 * The rub' markers that open a hizb, paired with the hizb they open. The first of every four, so
 * 240 markers yield the sixty ahzab in the order they are read.
 */
private fun List<DivisionEntity>.hizbStarts(): List<Pair<Int, DivisionEntity>> =
    filter { (it.num - 1) % RUBS_PER_HIZB == 0 }
        .map { (it.num - 1) / RUBS_PER_HIZB + 1 to it }

/** A division marker as an index row: what it is called, and where it begins. */
private fun DivisionEntity.toEntry(
    kind: IndexKind,
    number: Int,
    titleRes: Int,
    context: Context,
    nameOf: Map<Int, String>,
    pageOf: (Int, Int) -> Int,
) = IndexEntry(
    kind = kind,
    num = number,
    title = context.getString(titleRes, number),
    subtitle = nameOf[sura],
    page = pageOf(sura, aya),
    suraNum = sura,
    ayaNum = aya,
)

private fun pageFor(pageStarts: List<PageStartEntity>, sura: Int, aya: Int): Int {
    var result = pageStarts.firstOrNull()?.pageNum ?: 1
    for (ps in pageStarts) {
        if (ps.sura < sura || (ps.sura == sura && ps.aya <= aya)) result = ps.pageNum
        else break
    }
    return result
}
