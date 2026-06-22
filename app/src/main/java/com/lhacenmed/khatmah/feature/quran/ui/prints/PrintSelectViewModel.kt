package com.lhacenmed.khatmah.feature.quran.ui.prints

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.download.DownloadRegistry
import com.lhacenmed.khatmah.feature.quran.data.download.DownloadService
import com.lhacenmed.khatmah.feature.quran.data.download.DownloadState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the print selector. It only *observes* [DownloadRegistry] and asks [DownloadService] to
 * start/cancel — the download itself runs in the service, so leaving this screen never stops it.
 * QCF4 prints are the only downloadable ones; TEXT prints are always available.
 */
class PrintSelectViewModel(app: Application) : AndroidViewModel(app) {

    init {
        DownloadRegistry.seed(app)
    }

    val selected: StateFlow<MushafPrint> = MushafPrefs.selected

    /** Download state per print id (e.g. "hafs_qcf4"); text prints fall back to NotRequired in the UI. */
    val downloadStates: StateFlow<Map<String, DownloadState>> =
        DownloadRegistry.states
            .map { byRiwaya -> byRiwaya.mapKeys { (riwaya, _) -> "${riwaya.dbKey}_qcf4" } }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                DownloadRegistry.states.value.mapKeys { (riwaya, _) -> "${riwaya.dbKey}_qcf4" },
            )

    fun select(print: MushafPrint) {
        val state = downloadStates.value[print.id]
        if (print.requiresDownload && state !is DownloadState.Downloaded) return
        MushafPrefs.set(getApplication(), print)
    }

    fun download(print: MushafPrint) {
        if (!print.requiresDownload) return
        DownloadService.start(getApplication(), print.riwaya)
    }

    fun cancelDownload(print: MushafPrint) {
        DownloadService.cancel(getApplication(), print.riwaya)
    }
}
