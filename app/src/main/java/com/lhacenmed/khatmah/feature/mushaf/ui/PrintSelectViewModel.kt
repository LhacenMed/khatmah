package com.lhacenmed.khatmah.feature.mushaf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.mushaf.data.*
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4DownloadState
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.WarshDownloadState
import com.lhacenmed.khatmah.feature.quran.data.WarshImageDownloadState
import com.lhacenmed.khatmah.feature.quran.data.WarshImageRepository
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4DownloadState
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.WarshXmlRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PrintSelectViewModel(app: Application) : AndroidViewModel(app) {

    private val warshImgRepo  = WarshImageRepository.get(app)
    private val warshXmlRepo  = WarshXmlRepository.get(app)
    private val warshQcf4Repo = WarshQcf4Repository.get(app)
    private val hafsQcf4Repo  = HafsQcf4Repository.get(app)

    val selected: StateFlow<MushafPrint> = MushafPrefs.selected

    val downloadStates: StateFlow<Map<String, PrintDownloadState>> = combine(
        warshImgRepo.downloadState,
        warshXmlRepo.downloadState,
        warshQcf4Repo.downloadState,
        hafsQcf4Repo.downloadState,
    ) { img, xml, warshQcf4, hafsQcf4 ->
        mapOf(
            MushafPrint.WarshImages.id to img.toPrintState(),
            MushafPrint.WarshSvg.id    to xml.toPrintState(),
            MushafPrint.WarshQcf4.id   to warshQcf4.toPrintState(),
            MushafPrint.HafsQcf4.id    to hafsQcf4.toPrintState(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        mapOf(
            MushafPrint.WarshImages.id to warshImgRepo.downloadState.value.toPrintState(),
            MushafPrint.WarshSvg.id    to warshXmlRepo.downloadState.value.toPrintState(),
            MushafPrint.WarshQcf4.id   to warshQcf4Repo.downloadState.value.toPrintState(),
            MushafPrint.HafsQcf4.id    to hafsQcf4Repo.downloadState.value.toPrintState(),
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
                MushafPrint.WarshSvg    -> warshXmlRepo.downloadAll().collect()
                MushafPrint.WarshQcf4   -> warshQcf4Repo.downloadAll().collect()
                MushafPrint.HafsQcf4    -> hafsQcf4Repo.downloadAll().collect()
                MushafPrint.WarshText,
                MushafPrint.HafsText    -> Unit
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

    private fun WarshQcf4DownloadState.toPrintState(): PrintDownloadState = when (this) {
        is WarshQcf4DownloadState.NotDownloaded -> PrintDownloadState.NotDownloaded
        is WarshQcf4DownloadState.Connecting    -> PrintDownloadState.Connecting
        is WarshQcf4DownloadState.Downloading   -> PrintDownloadState.Downloading(progress, log)
        is WarshQcf4DownloadState.Downloaded    -> PrintDownloadState.Downloaded
        is WarshQcf4DownloadState.Error         -> PrintDownloadState.Error(message)
    }

    private fun HafsQcf4DownloadState.toPrintState(): PrintDownloadState = when (this) {
        is HafsQcf4DownloadState.NotDownloaded -> PrintDownloadState.NotDownloaded
        is HafsQcf4DownloadState.Connecting    -> PrintDownloadState.Connecting
        is HafsQcf4DownloadState.Downloading   -> PrintDownloadState.Downloading(progress, log)
        is HafsQcf4DownloadState.Downloaded    -> PrintDownloadState.Downloaded
        is HafsQcf4DownloadState.Error         -> PrintDownloadState.Error(message)
    }
}