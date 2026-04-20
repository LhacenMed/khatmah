package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.data.adhkar.AdhkarCategory
import com.lhacenmed.khatmah.data.adhkar.AdhkarRepository
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AthkarUiState(
    val categories: List<AdhkarCategory> = emptyList(),
    val isLoading: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    val allSelected: Boolean
        get() = categories.isNotEmpty() && selectedIds.size == categories.size
}

class AdhkarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AdhkarRepository(app)

    private val _uiState = MutableStateFlow(AthkarUiState())
    val uiState: StateFlow<AthkarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            reload()
        }
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = repo.getCategories()
            _uiState.update { it.copy(categories = list, isLoading = false) }
        }
    }

    // ── Selection mode ────────────────────────────────────────────────────────

    fun enterSelectionMode(id: String) =
        _uiState.update { it.copy(selectionMode = true, selectedIds = setOf(id)) }

    fun toggleSelection(id: String) =
        _uiState.update { s ->
            s.copy(selectedIds = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id)
        }

    fun toggleSelectAll() =
        _uiState.update { s ->
            if (s.allSelected) s.copy(selectedIds = emptySet())
            else s.copy(selectedIds = s.categories.map { it.id }.toSet())
        }

    fun exitSelectionMode() =
        _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repo.deleteCategories(ids)
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            reload()
        }
    }

    // ── Add category ──────────────────────────────────────────────────────────

    fun addCategory(category: AdhkarCategory, dhikrList: List<Dhikr>) {
        viewModelScope.launch {
            val sortOrder = _uiState.value.categories.size
            repo.insertCategory(category, dhikrList, sortOrder)
            reload()
        }
    }

    // ── Dhikr access ──────────────────────────────────────────────────────────

    suspend fun getDhikrForCategory(categoryId: String): List<Dhikr> =
        repo.getDhikrForCategory(categoryId)

    // ── Image ─────────────────────────────────────────────────────────────────

    fun persistImage(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repo.copyImageToInternal(uri) }
            onResult(path)
        }
    }
}