package com.lhacenmed.khatmah.ui.page.quran

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.quran.QuranRepository
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
        object Loading                                    : State()
        data class Ready(val pages: List<QuranPageData>) : State()
    }

    private val repo  = QuranRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state       = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _pendingJump = MutableStateFlow<Int?>(null)
    val pendingJump: StateFlow<Int?> = _pendingJump.asStateFlow()

    /** Last-read page index (0-based); restored on cold start via SharedPreferences. */
    var savedPage: Int = prefs.getInt(KEY_PAGE, 0)
        private set

    private var ayaPageIndex: Map<Long, Int> = emptyMap()

    /**
     * Must be called once from [QuranReaderScreen] after [BoxWithConstraints] resolves
     * the usable page dimensions. Safe to call multiple times — rebuilds only once.
     *
     * [fontScale] is sourced from [LocalDensity.current.fontScale] so StaticLayout
     * measurements use the same sp→px factor as Compose text rendering.
     */
    fun init(pageHeightPx: Int, contentWidthPx: Int, density: Float, fontScale: Float) {
        if (_state.value !is State.Loading) return
        viewModelScope.launch {
            val pages = withContext(Dispatchers.Default) {
                QuranPaginator.build(
                    context        = getApplication(),
                    ayas           = repo.allAyas(),
                    pageHeightPx   = pageHeightPx,
                    contentWidthPx = contentWidthPx,
                    density        = density,
                    fontScale      = fontScale,
                )
            }
            ayaPageIndex = buildAyaIndex(pages)

            val targetSura = handle.get<Int>("suraNum") ?: 0
            savedPage = if (targetSura > 0) {
                val targetAya = (handle.get<Int>("ayaNum") ?: 0).coerceAtLeast(1)
                pageForAya(targetSura, targetAya) ?: findSuraPage(targetSura, pages)
            } else {
                savedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            }
            _state.value = State.Ready(pages)
        }
    }

    fun savePage(index: Int) {
        savedPage = index
        prefs.edit { putInt(KEY_PAGE, index) }
    }

    fun requestJump(suraNum: Int, ayaNum: Int) {
        _pendingJump.value = pageForAya(suraNum, ayaNum)
    }

    fun consumeJump() { _pendingJump.value = null }

    fun pageForAya(suraNum: Int, ayaNum: Int): Int? =
        ayaPageIndex[suraNum.toLong() shl 32 or ayaNum.toLong()]

    private fun buildAyaIndex(pages: List<QuranPageData>): Map<Long, Int> {
        val map = HashMap<Long, Int>(6300)
        pages.forEachIndexed { idx, page ->
            page.segments.forEach { seg ->
                if (seg is QuranSegment.Aya)
                    map[seg.suraNum.toLong() shl 32 or seg.ayaNum.toLong()] = idx
            }
        }
        return map
    }

    private fun findSuraPage(suraNum: Int, pages: List<QuranPageData>): Int =
        pages.indexOfFirst { p ->
            p.segments.any { it is QuranSegment.SuraHeader && it.num == suraNum }
        }.coerceAtLeast(0)

    private companion object {
        const val PREFS    = "quran_reader"
        const val KEY_PAGE = "last_page"
    }
}