package com.lhacenmed.khatmah.feature.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class FsGroupType { DATABASES, HAFS_FONTS, WARSH_IMAGES, WARSH_SVG }

data class FsEntry(val name: String, val sizeBytes: Long)

data class FsGroup(
    val id:         String,
    val type:       FsGroupType,
    val label:      String,
    val dir:        File?,
    val entries:    List<FsEntry>,
    val totalBytes: Long,
    val canDelete:  Boolean,
    val expanded:   Boolean = false,
)

data class FileBrowserState(
    val groups:     List<FsGroup> = emptyList(),
    val isLoading:  Boolean       = true,
    val deletingId: String?       = null,
    val error:      String?       = null,
)

class FileBrowserViewModel(private val ctx: Context) : ViewModel() {

    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init { scan() }

    fun refresh() = scan()

    fun toggleExpand(id: String) = _state.update { s ->
        s.copy(groups = s.groups.map { if (it.id == id) it.copy(expanded = !it.expanded) else it })
    }

    fun delete(id: String) {
        val group = _state.value.groups.find { it.id == id }?.takeIf { it.canDelete } ?: return
        _state.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { group.dir?.deleteRecursively() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
            _state.update { it.copy(groups = buildGroups(), deletingId = null) }
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun scan() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(groups = buildGroups(), isLoading = false) }
        }
    }

    private fun buildGroups(): List<FsGroup> = buildList {
        // Databases — view-only (Room holds files open; deleting here is unsafe)
        val dbDir  = ctx.getDatabasePath("_").parentFile
        val dbList = dbDir?.listFiles()
            ?.filter { it.extension == "db" }
            ?.sortedBy { it.name }
            ?.map { FsEntry(it.name, it.length()) }
            ?: emptyList()
        add(FsGroup("databases", FsGroupType.DATABASES, "Databases",
            dbDir, dbList, dbList.sumOf { it.sizeBytes }, canDelete = false))

        // Hafs QCF4 font files
        add(dirGroup("hafs_fonts", FsGroupType.HAFS_FONTS, "Hafs QCF4 — Fonts",
            File(ctx.filesDir, "hafs-qcf4/fonts"), canDelete = true))

        // Warsh JPEG mushaf pages
        add(dirGroup("warsh_images", FsGroupType.WARSH_IMAGES, "Warsh — Mushaf Images",
            File(ctx.filesDir, "warsh-images"), canDelete = true))

        // Warsh SVG/JSON pages + Drive index
        add(dirGroup("warsh_svg", FsGroupType.WARSH_SVG, "Warsh — SVG Pages",
            File(ctx.filesDir, "warsh"), canDelete = true))
    }

    private fun dirGroup(
        id:        String,
        type:      FsGroupType,
        label:     String,
        dir:       File,
        canDelete: Boolean,
    ): FsGroup {
        val entries = if (dir.exists()) {
            dir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.name }
                .map { FsEntry(it.relativeTo(dir).path, it.length()) }
                .toList()
        } else emptyList()
        return FsGroup(id, type, label, dir.takeIf { it.exists() },
            entries, entries.sumOf { it.sizeBytes }, canDelete)
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FileBrowserViewModel(ctx) as T
    }
}