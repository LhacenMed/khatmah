package com.lhacenmed.khatmah.ui.page.quran

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.quran.QuranRepository
import com.lhacenmed.khatmah.data.quran.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuranSearchViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val query:   String             = "",
        val results: List<SearchResult> = emptyList(),
        val loading: Boolean            = false,
    )

    private val repo   = QuranRepository(app)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Updates the search query and triggers a debounced (300 ms) search.
     * Clears results immediately when [query] is blank.
     */
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(loading = true) }
            val results = repo.search(query)
            _state.update { it.copy(results = results, loading = false) }
        }
    }
}