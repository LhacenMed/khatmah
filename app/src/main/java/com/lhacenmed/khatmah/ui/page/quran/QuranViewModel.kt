package com.lhacenmed.khatmah.ui.page.quran

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.quran.QuranAya
import com.lhacenmed.khatmah.data.quran.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuranViewModel(app: Application) : AndroidViewModel(app) {

    sealed class State {
        object Loading                         : State()
        data class Ready(val ayas: List<QuranAya>) : State()
    }

    private val repo  = QuranRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Last-read page index (0-based); restored across cold starts via SharedPreferences. */
    var savedPage: Int = prefs.getInt(KEY_PAGE, 0)
        private set

    // ── Page cache ─────────────────────────────────────────────────────────────
    // Keyed by (pageWidth, pageHeight) encoded as a Long so that screen
    // rotations or font-size changes automatically invalidate the cache.
    private var cacheKey:  Long                  = -1L
    private var pageCache: List<QuranPageData>   = emptyList()

    init {
        viewModelScope.launch {
            _state.value = State.Ready(repo.allAyas())
        }
    }

    fun savePage(index: Int) {
        savedPage = index
        prefs.edit { putInt(KEY_PAGE, index) }
    }

    /** Returns cached pages if the key matches, otherwise null. */
    fun getCachedPages(key: Long): List<QuranPageData>? =
        if (key == cacheKey && pageCache.isNotEmpty()) pageCache else null

    fun cachePages(key: Long, pages: List<QuranPageData>) {
        cacheKey  = key
        pageCache = pages
    }

    private companion object {
        const val PREFS    = "quran_reader"
        const val KEY_PAGE = "last_page"
    }
}