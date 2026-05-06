package com.lhacenmed.khatmah.feature.mushaf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.mushaf.data.*
import com.lhacenmed.khatmah.feature.quran.data.WarshDownloadState
import com.lhacenmed.khatmah.feature.quran.data.WarshImageDownloadState
import com.lhacenmed.khatmah.feature.quran.data.WarshImageRepository
import com.lhacenmed.khatmah.feature.quran.data.WarshXmlRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PrintSelectViewModel(app: Application) : AndroidViewModel(app) {

    // Singleton repos — state persists across ViewModel recreations.
    private val warshImgRepo = WarshImageRepository.get(app)
    private val warshXmlRepo = WarshXmlRepository.get(app)

    val selected: StateFlow<MushafPrint> = MushafPrefs.selected

    /** Map of printId → current [PrintDownloadState]. Seeded with real disk state. */
    val downloadStates: StateFlow<Map<String, PrintDownloadState>> = combine(
        warshImgRepo.downloadState,
        warshXmlRepo.downloadState,
    ) { img, xml ->
        mapOf(
            MushafPrint.WarshImages.id to img.toPrintState(),
            MushafPrint.WarshSvg.id    to xml.toPrintState(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        mapOf(
            MushafPrint.WarshImages.id to warshImgRepo.downloadState.value.toPrintState(),
            MushafPrint.WarshSvg.id    to warshXmlRepo.downloadState.value.toPrintState(),
        ),
    )

    fun select(print: MushafPrint) {
        val state = stateOf(print)
        if (print.requiresDownload && state !is PrintDownloadState.Downloaded) return
        MushafPrefs.set(getApplication(), print)
    }

    fun download(print: MushafPrint) {
        viewModelScope.launch {
            when (print) {
                MushafPrint.WarshImages -> warshImgRepo.downloadAll().collect()
                MushafPrint.WarshSvg   -> warshXmlRepo.downloadAll().collect()
                MushafPrint.WarshText  -> Unit
            }
        }
    }

    private fun stateOf(print: MushafPrint): PrintDownloadState =
        downloadStates.value[print.id]
            ?: if (print.requiresDownload) PrintDownloadState.NotDownloaded
            else PrintDownloadState.NotRequired

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun WarshImageDownloadState.toPrintState(): PrintDownloadState = when (this) {
        is WarshImageDownloadState.NotDownloaded -> PrintDownloadState.NotDownloaded
        is WarshImageDownloadState.Connecting    -> PrintDownloadState.Connecting
        is WarshImageDownloadState.Downloading   -> PrintDownloadState.Downloading(progress)
        is WarshImageDownloadState.Downloaded    -> PrintDownloadState.Downloaded
        is WarshImageDownloadState.Error         -> PrintDownloadState.Error(message)
    }

    private fun WarshDownloadState.toPrintState(): PrintDownloadState = when (this) {
        is WarshDownloadState.NotDownloaded -> PrintDownloadState.NotDownloaded
        is WarshDownloadState.Connecting    -> PrintDownloadState.Connecting
        is WarshDownloadState.Downloading   -> PrintDownloadState.Downloading(progress)
        is WarshDownloadState.Downloaded    -> PrintDownloadState.Downloaded
        is WarshDownloadState.Error         -> PrintDownloadState.Error(message)
    }
}