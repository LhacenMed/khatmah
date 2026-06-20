package com.lhacenmed.khatmah.feature.quran.data

/** Download lifecycle of a riwaya's QCF4 assets. One type for every riwaya. */
sealed class Qcf4DownloadState {
    object NotDownloaded                                            : Qcf4DownloadState()
    object Connecting                                               : Qcf4DownloadState()
    data class Downloading(val progress: Float?, val log: String = "") : Qcf4DownloadState()
    object Downloaded                                              : Qcf4DownloadState()
    data class Error(val message: String)                          : Qcf4DownloadState()
}
