package com.lhacenmed.khatmah.feature.mushaf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.mushaf.data.*
import com.lhacenmed.khatmah.feature.quran.data.Qcf4DownloadState
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Drives the print selector. QCF4 prints are the only downloadable ones, so the VM watches one
 * [Qcf4Repository] per riwaya; TEXT prints are always available. Everything is keyed off
 * [MushafRegistry], so a new riwaya needs no change here.
 */
class PrintSelectViewModel(app: Application) : AndroidViewModel(app) {

    /** One repository per downloadable (QCF4) riwaya. */
    private val qcf4Repos: Map<Riwaya, Qcf4Repository> =
        MushafRegistry.all
            .filter { it.format == MushafFormat.QCF4 }
            .associate { it.riwaya to Qcf4Repository.get(app, it.riwaya) }

    /** Active download job per print id — cancelled on user request. */
    private val activeDownloads = mutableMapOf<String, Job>()

    val selected: StateFlow<MushafPrint> = MushafPrefs.selected

    val downloadStates: StateFlow<Map<String, PrintDownloadState>> = combine(
        qcf4Repos.map { (riwaya, repo) -> repo.downloadState.map { riwaya to it } }
    ) { states ->
        states.associate { (riwaya, state) -> qcf4Id(riwaya) to state.toPrintState() }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        qcf4Repos.entries.associate { (riwaya, repo) ->
            qcf4Id(riwaya) to repo.downloadState.value.toPrintState()
        },
    )

    fun select(print: MushafPrint) {
        val state = stateOf(print)
        if (print.requiresDownload && state !is PrintDownloadState.Downloaded) return
        MushafPrefs.set(getApplication(), print)
    }

    fun download(print: MushafPrint) {
        val repo = qcf4Repos[print.riwaya] ?: return
        if (activeDownloads[print.id]?.isActive == true) return
        activeDownloads[print.id] = viewModelScope.launch { repo.downloadAll().collect() }
    }

    fun cancelDownload(print: MushafPrint) {
        activeDownloads.remove(print.id)?.cancel()
        qcf4Repos[print.riwaya]?.resetState()
    }

    private fun stateOf(print: MushafPrint): PrintDownloadState =
        downloadStates.value[print.id]
            ?: if (print.requiresDownload) PrintDownloadState.NotDownloaded
            else PrintDownloadState.NotRequired

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Print id of the QCF4 print for [riwaya] (matches [MushafPrint.id]). */
    private fun qcf4Id(riwaya: Riwaya) = "${riwaya.dbKey}_qcf4"

    private fun Qcf4DownloadState.toPrintState(): PrintDownloadState = when (this) {
        is Qcf4DownloadState.NotDownloaded -> PrintDownloadState.NotDownloaded
        is Qcf4DownloadState.Connecting    -> PrintDownloadState.Connecting
        is Qcf4DownloadState.Downloading   -> PrintDownloadState.Downloading(progress, log)
        is Qcf4DownloadState.Downloaded    -> PrintDownloadState.Downloaded
        is Qcf4DownloadState.Error         -> PrintDownloadState.Error(message)
    }
}
