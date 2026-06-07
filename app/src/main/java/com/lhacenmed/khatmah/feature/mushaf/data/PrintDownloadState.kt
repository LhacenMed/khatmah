package com.lhacenmed.khatmah.feature.mushaf.data

/** Unified download state for any [MushafPrint] that requires a download. */
sealed class PrintDownloadState {
    /** Print is DB-backed; no download needed. */
    data object NotRequired   : PrintDownloadState()
    data object NotDownloaded : PrintDownloadState()
    data object Connecting    : PrintDownloadState()
    /**
     * [progress] — 0f–1f for determinate (file download), null for indeterminate
     *              (extraction, DB import, index rebuild).
     * [log]      — human-readable step description shown in the log box.
     */
    data class  Downloading(val progress: Float?, val log: String = "") : PrintDownloadState()
    data object Downloaded    : PrintDownloadState()
    data class  Error(val message: String) : PrintDownloadState()
}