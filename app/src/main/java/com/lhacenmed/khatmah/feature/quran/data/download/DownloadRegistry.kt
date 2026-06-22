package com.lhacenmed.khatmah.feature.quran.data.download

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for QCF4 download progress, keyed by [Riwaya]. The
 * [DownloadService] writes state here as it works; the UI only ever observes [states]. Because
 * this lives outside any Activity/ViewModel, leaving the print screen — or the screen being
 * recreated — never loses an in-flight download: reopening simply re-reads the same flow.
 */
object DownloadRegistry {

    private val _states = MutableStateFlow<Map<Riwaya, DownloadState>>(emptyMap())
    val states: StateFlow<Map<Riwaya, DownloadState>> = _states.asStateFlow()

    /** Fills any unknown riwaya state from disk (downloaded vs. not). Cheap; idempotent. */
    fun seed(ctx: Context) {
        val app = ctx.applicationContext
        val seeded = _states.value.toMutableMap()
        var changed = false
        for (riwaya in Riwaya.entries) {
            if (riwaya !in seeded) {
                seeded[riwaya] =
                    if (riwaya.config.isDownloaded(app)) DownloadState.Downloaded
                    else DownloadState.NotDownloaded
                changed = true
            }
        }
        if (changed) _states.value = seeded
    }

    fun update(riwaya: Riwaya, state: DownloadState) {
        _states.value = _states.value + (riwaya to state)
    }

    fun stateOf(riwaya: Riwaya): DownloadState =
        _states.value[riwaya] ?: DownloadState.NotDownloaded

    /** True while a download for [riwaya] is connecting or transferring. */
    fun isActive(riwaya: Riwaya): Boolean = when (stateOf(riwaya)) {
        DownloadState.Connecting, is DownloadState.Downloading -> true
        else -> false
    }
}
