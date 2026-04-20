package com.lhacenmed.khatmah.ui.page.quran

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.quran.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranViewModel(app: Application) : AndroidViewModel(app) {

    sealed class State {
        object Loading                                   : State()
        data class Ready(val pages: List<QuranPageData>) : State()
    }

    private val repo  = QuranRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Last-read page index (0-based); restored on cold start via SharedPreferences. */
    var savedPage: Int = prefs.getInt(KEY_PAGE, 0)
        private set

    init {
        viewModelScope.launch {
            // IO dispatcher: read 6 262 rows from SQLite (~50-100 ms)
            // Default dispatcher: build pages with pure arithmetic (~5-10 ms)
            val pages = withContext(Dispatchers.Default) {
                QuranPageBuilder.build(repo.allAyas())
            }
            // Clamp saved page in case the page count changed (DB update, etc.)
            savedPage = savedPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            _state.value = State.Ready(pages)
        }
    }

    fun savePage(index: Int) {
        savedPage = index
        prefs.edit { putInt(KEY_PAGE, index) }
    }

    private companion object {
        const val PREFS    = "quran_reader"
        const val KEY_PAGE = "last_page"
    }
}