package com.lhacenmed.khatmah.feature.quran.data.download

/**
 * The one download state for the whole mushaf system. Text prints need no download
 * ([NotRequired]); QCF4 prints move through the rest of the lifecycle.
 *
 * [Downloading.progress] — 0f–1f for the determinate file download, null for the indeterminate
 *                          extraction / DB-import / index-rebuild phases.
 * [Downloading.log]      — human-readable step shown in the UI log shelf and the notification.
 */
sealed class DownloadState {
    data object NotRequired   : DownloadState()
    data object NotDownloaded : DownloadState()
    data object Connecting    : DownloadState()
    data class  Downloading(val progress: Float?, val log: String = "") : DownloadState()
    data object Downloaded    : DownloadState()
    data class  Error(val message: String) : DownloadState()
}
