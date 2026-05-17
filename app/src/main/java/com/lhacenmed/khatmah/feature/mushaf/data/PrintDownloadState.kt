package com.lhacenmed.khatmah.feature.mushaf.data

/** Unified download state for any [MushafPrint] that requires a download. */
sealed class PrintDownloadState {
    /** Print is DB-backed; no download needed. */
    data object NotRequired   : PrintDownloadState()
    data object NotDownloaded : PrintDownloadState()
    data object Connecting    : PrintDownloadState()
    data class  Downloading(val progress: Float) : PrintDownloadState()
    data object Downloaded    : PrintDownloadState()
    data class  Error(val message: String) : PrintDownloadState()
}