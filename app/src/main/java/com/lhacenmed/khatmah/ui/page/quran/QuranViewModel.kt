package com.lhacenmed.khatmah.ui.page.quran

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.quran.QuranRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.content.edit

class QuranViewModel(app: Application) : AndroidViewModel(app) {

    sealed class State {
        data object Loading : State()
        data class Ready(val pages: List<QuranPage>) : State()
    }

    private val repo  = QuranRepository(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Last-read page index (0-based). Restored across cold starts. */
    val savedPage: Int get() = prefs.getInt(KEY_PAGE, 0)

    init {
        viewModelScope.launch {
            _state.value = State.Ready(buildQuranPages(repo.allAyas()))
        }
    }

    fun savePage(index: Int) = prefs.edit { putInt(KEY_PAGE, index) }

    private companion object {
        const val PREFS    = "quran_reader"
        const val KEY_PAGE = "last_page"
    }
}