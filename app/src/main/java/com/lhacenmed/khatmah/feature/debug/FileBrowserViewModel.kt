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
import kotlinx.coroutines.withContext
import java.io.File

data class FileBrowserState(
    val currentDir: File,
    val files:      List<File> = emptyList(),
    val loading:    Boolean    = true,
    val infoText:   String?    = null,
)

class FileBrowserViewModel(rootDir: File) : ViewModel() {

    val rootDir: File = rootDir

    private val _state = MutableStateFlow(FileBrowserState(currentDir = rootDir))
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init { loadDir(rootDir) }

    fun openDir(dir: File) {
        _state.update { it.copy(currentDir = dir, loading = true) }
        loadDir(dir)
    }

    fun rename(oldPath: String, newName: String): Boolean {
        val ok = NativeBindings.rename(oldPath, newName)
        if (ok) reload()
        return ok
    }

    fun delete(path: String): Boolean {
        val ok = NativeBindings.delete(path)
        if (ok) reload()
        return ok
    }

    fun metadata(path: String): FileMetadata? = NativeBindings.metadata(path)

    fun dismissInfo() = _state.update { it.copy(infoText = null) }

    fun showInfo(text: String) = _state.update { it.copy(infoText = text) }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun reload() = loadDir(_state.value.currentDir)

    private fun loadDir(dir: File) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { listDirectoryFiles(dir) }
            _state.update { it.copy(files = list, loading = false) }
        }
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FileBrowserViewModel(ctx.dataDir) as T
    }
}