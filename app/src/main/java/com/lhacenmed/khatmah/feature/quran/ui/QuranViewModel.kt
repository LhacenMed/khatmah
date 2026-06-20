package com.lhacenmed.khatmah.feature.quran.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.QuranRepository
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.WarshXmlRepository
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranPageBuilder
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranPageData
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranViewModel(
    app: Application,
    private val handle: SavedStateHandle,
) : AndroidViewModel(app) {

    sealed class State {
        object Loading : State()
        /** Text-based mushaf — carries riwaya for font selection and bismillah text. */
        data class Ready(
            val pages:     List<QuranPageData>,
            val riwaya:    Riwaya,
            val bismillah: String,
        ) : State()
        /** Image reader mode: 604 mushaf pages, no text rendering. */
        data class ImageReady(val pageCount: Int) : State()
        /** Warsh XML reader mode: 604 vector mushaf pages rendered via VectorDrawable. */
        data class XmlReady(val pageCount: Int) : State()
        data class Qcf4Ready(val pageCount: Int, val print: MushafPrint) : State()
    }

    private val repo  = QuranRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state       = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Non-null while a page jump is pending (triggered by a search result selection).
     * Consumed by the pager via [consumeJump] once scrolled.
     */
    private val _pendingJump = MutableStateFlow<Int?>(null)
    val pendingJump: StateFlow<Int?> = _pendingJump.asStateFlow()

    /** Last-read page index (0-based); restored on cold start via SharedPreferences. */
    var savedPage: Int = prefs.getInt(KEY_PAGE, 0)
        private set

    /** Packed (suraNum shl 32 or ayaNum) → page index. Built once after pages are loaded. */
    private var ayaPageIndex: Map<Long, Int> = emptyMap()

    init {
        viewModelScope.launch {
            when (MushafPrefs.selected.value) {
                MushafPrint.WarshImages -> initImageMode()
                MushafPrint.WarshSvg    -> initXmlMode()
                MushafPrint.HafsQcf4    -> initQcf4Mode(MushafPrint.HafsQcf4)
                MushafPrint.WarshQcf4   -> initQcf4Mode(MushafPrint.WarshQcf4)
                else                    -> initTextMode()   // WarshText (default)
            }
        }
    }

    private suspend fun initImageMode() {
        ayaPageIndex = withContext(Dispatchers.IO) { repo.ayaPageIndex("warsh") }

        val targetSura = handle.get<Int>("suraNum") ?: 0
        savedPage = if (targetSura > 0) {
            val targetAya = (handle.get<Int>("ayaNum") ?: 0).coerceAtLeast(1)
            pageForAya(targetSura, targetAya) ?: 0
        } else {
            savedPage.coerceIn(0, MUSHAF_PAGE_COUNT - 1)
        }
        _state.value = State.ImageReady(MUSHAF_PAGE_COUNT)
    }

    private suspend fun initXmlMode() {
        val warshRepo = WarshXmlRepository(getApplication())
        if (!warshRepo.isFullyDownloaded()) { initTextMode(); return }

        // XML pages match Warsh mushaf — reuse the Warsh aya→page index.
        ayaPageIndex = withContext(Dispatchers.IO) { repo.ayaPageIndex("warsh") }

        val targetSura = handle.get<Int>("suraNum") ?: 0
        savedPage = if (targetSura > 0) {
            val targetAya = (handle.get<Int>("ayaNum") ?: 0).coerceAtLeast(1)
            pageForAya(targetSura, targetAya) ?: 0
        } else {
            savedPage.coerceIn(0, PAGE_COUNT - 1)
        }
        _state.value = State.XmlReady(PAGE_COUNT)
    }

    private suspend fun initQcf4Mode(print: MushafPrint) {
        val (pageCount, index) = when (print) {
            MushafPrint.HafsQcf4 -> {
                val qcf4 = HafsQcf4Repository.get(getApplication())
                if (!qcf4.isFullyDownloaded()) { initTextMode(); return }
                HafsQcf4Repository.PAGE_COUNT to withContext(Dispatchers.IO) { qcf4.ayaPageIndex() }
            }
            MushafPrint.WarshQcf4 -> {
                val qcf4 = WarshQcf4Repository.get(getApplication())
                if (!qcf4.isFullyDownloaded()) { initTextMode(); return }
                WarshQcf4Repository.PAGE_COUNT to withContext(Dispatchers.IO) { qcf4.ayaPageIndex() }
            }
            else -> { initTextMode(); return }
        }
        ayaPageIndex = index
        val targetSura = handle.get<Int>("suraNum") ?: 0
        savedPage = if (targetSura > 0) {
            val targetAya = (handle.get<Int>("ayaNum") ?: 0).coerceAtLeast(1)
            pageForAya(targetSura, targetAya) ?: 0
        } else {
            savedPage.coerceIn(0, pageCount - 1)
        }
        _state.value = State.Qcf4Ready(pageCount, print)
    }

    private suspend fun initTextMode() {
        val riwaya    = MushafPrefs.selected.value.riwaya
        val riwayaKey = riwaya.dbKey

        val ayas         = withContext(Dispatchers.IO) { repo.allAyas(riwayaKey) }
        val bismillahMap = withContext(Dispatchers.IO) { repo.bismillahMap(riwayaKey) }

        val pages = withContext(Dispatchers.Default) {
            QuranPageBuilder.build(ayas, bismillahMap)
        }
        ayaPageIndex = buildAyaPageIndex(pages)

        // Hardcode basmala per riwaya to ensure correct script and numbering behavior.
        val bismillah = when (riwaya) {
            Riwaya.WARSH -> "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ"
            Riwaya.HAFS  -> "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
        }

        val targetSura = handle.get<Int>("suraNum") ?: 0
        savedPage = if (targetSura > 0) {
            val targetAya = (handle.get<Int>("ayaNum") ?: 0).coerceAtLeast(1)
            pageForAya(targetSura, targetAya) ?: findSuraPage(targetSura, pages)
        } else {
            savedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
        _state.value = State.Ready(pages, riwaya, bismillah)
    }

    fun savePage(index: Int) {
        savedPage = index
        prefs.edit { putInt(KEY_PAGE, index) }
    }

    /**
     * Requests an immediate pager scroll to the page containing [suraNum] / [ayaNum].
     * If the aya is not yet indexed (VM still loading), the request is silently dropped.
     */
    fun requestJump(suraNum: Int, ayaNum: Int) {
        _pendingJump.value = pageForAya(suraNum, ayaNum)
    }

    /** Called by the pager after it has scrolled to the pending page. */
    fun consumeJump() {
        _pendingJump.value = null
    }

    /** Returns the 0-based page index for [suraNum] / [ayaNum], or null if not mapped. */
    fun pageForAya(suraNum: Int, ayaNum: Int): Int? =
        ayaPageIndex[suraNum.toLong() shl 32 or ayaNum.toLong()]

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildAyaPageIndex(pages: List<QuranPageData>): Map<Long, Int> {
        val map = HashMap<Long, Int>(6300)
        pages.forEachIndexed { idx, page ->
            page.segments.forEach { seg ->
                if (seg is QuranSegment.Aya) {
                    map[seg.suraNum.toLong() shl 32 or seg.ayaNum.toLong()] = idx
                }
            }
        }
        return map
    }

    /** Fallback: index of the first page whose header belongs to [suraNum]. */
    private fun findSuraPage(suraNum: Int, pages: List<QuranPageData>): Int =
        pages.indexOfFirst { page ->
            page.segments.any { it is QuranSegment.SuraHeader && it.num == suraNum }
        }.coerceAtLeast(0)

    companion object {
        // PREFS/KEY_PAGE are the shared last-read-page contract, also read/written by the
        // native book reader (BookReaderActivity) so reading position stays in sync.
        const val PREFS             = "quran_reader"
        const val KEY_PAGE          = "last_page"
        const val MUSHAF_PAGE_COUNT = 604
        const val PAGE_COUNT        = WarshXmlRepository.PAGE_COUNT
    }
}