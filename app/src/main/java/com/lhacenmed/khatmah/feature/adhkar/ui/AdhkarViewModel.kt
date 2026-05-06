package com.lhacenmed.khatmah.feature.adhkar.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarCategory
import com.lhacenmed.khatmah.feature.adhkar.data.BuiltInDefaults
import com.lhacenmed.khatmah.feature.adhkar.data.AdhkarRepository
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdhkarUiState(
    val categories: List<AdhkarCategory> = emptyList(),
    val isLoading: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    /**
     * Incremented on every [reload] so that [com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailPage] can observe
     * changes and re-fetch its dhikr list after an edit or reset.
     */
    val version: Int = 0,
) {
    val allSelected: Boolean
        get() = categories.isNotEmpty() && selectedIds.size == categories.size
}

/**
 * Per-session read progress for the currently open [com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailPage].
 * Keyed by category ID so that opening a different category always starts fresh.
 * Counts persist across configuration changes (VM survives rotation).
 *
 * [counts] maps page index → number of reads recorded on that page.
 */
data class DhikrSession(
    val categoryId: String = "",
    val count: Int = 0,
)

class AdhkarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AdhkarRepository(app)

    private val _uiState = MutableStateFlow(AdhkarUiState())
    val uiState: StateFlow<AdhkarUiState> = _uiState.asStateFlow()

    private val _session = MutableStateFlow(DhikrSession())
    // In-memory dhikr cache — written by the detail page on load, read by the editor
    // on entry so the navigation transition has no IO work to compete with.
    private val dhikrCache = mutableMapOf<String, List<Dhikr>>()
    val session: StateFlow<DhikrSession> = _session.asStateFlow()

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
            _uiState.update { it.copy(categories = list, isLoading = false, version = it.version + 1) }
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
            ids.forEach { dhikrCache.remove(it) }   // invalidate
            _uiState.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
            reload()
        }
    }

    // ── Create / edit category ────────────────────────────────────────────────

    fun addCategory(category: AdhkarCategory, dhikrList: List<Dhikr>) {
        viewModelScope.launch {
            val sortOrder = _uiState.value.categories.size
            repo.insertCategory(category, dhikrList, sortOrder)
            reload()
        }
    }

    /** Overwrites an existing category's metadata and full dhikr list. */
    fun updateCategory(category: AdhkarCategory, dhikrList: List<Dhikr>) {
        viewModelScope.launch {
            repo.updateCategory(category, dhikrList)
            reload()
        }
    }

    /**
     * Restores a built-in category to its original descriptor values and
     * original dhikr from [AdhkarData]. No-op for user-created categories.
     */
    fun resetCategoryToDefaults(categoryId: String) {
        viewModelScope.launch {
            repo.resetCategoryToDefaults(categoryId)
            dhikrCache.remove(categoryId)   // invalidate
            reload()
        }
    }

    // ── Built-in helpers ──────────────────────────────────────────────────────

    /**
     * Returns the original defaults for a built-in category (synchronous,
     * from in-memory data). Returns null for user-created categories.
     * Safe to call inside [remember] blocks in Compose.
     */
    fun getBuiltInDefaults(categoryId: String): BuiltInDefaults? =
        repo.getBuiltInDefaults(categoryId)

    // ── Dhikr access ──────────────────────────────────────────────────────────

    suspend fun getDhikrForCategory(categoryId: String): List<Dhikr> =
        repo.getDhikrForCategory(categoryId)

    /** Stores an already-fetched dhikr list so the editor can read it synchronously. */
    fun cacheDhikr(categoryId: String, list: List<Dhikr>) {
        dhikrCache[categoryId] = list
    }

    /**
     * Returns the cached dhikr list for [categoryId] if available.
     * Returns null on a cache miss — caller falls back to async DB fetch.
     */
    fun getCachedDhikr(categoryId: String): List<Dhikr>? = dhikrCache[categoryId]

    // ── Dhikr session ─────────────────────────────────────────────────────────

    /**
     * Starts a fresh read session for [categoryId].
     * Called when the detail page opens or when dhikr reloads after an edit/reset.
     */
    fun startSession(categoryId: String) {
        _session.value = DhikrSession(categoryId = categoryId)
    }

    /** Records one read for the currently visible dhikr. */
    fun recordRead() {
        _session.update { it.copy(count = it.count + 1) }
    }

    /** Resets the count for the current dhikr. Called on every page change. */
    fun resetCount() {
        _session.update { it.copy(count = 0) }
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    fun persistImage(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repo.copyImageToInternal(uri) }
            onResult(path)
        }
    }
}