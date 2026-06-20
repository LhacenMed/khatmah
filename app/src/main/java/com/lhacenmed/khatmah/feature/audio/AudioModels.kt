package com.lhacenmed.khatmah.feature.audio

import java.io.File

// ── Download state ────────────────────────────────────────────────────────────

sealed class DownloadState {
    /** Checking cache / connecting — length unknown yet. */
    object Connecting : DownloadState()
    /**
     * Actively downloading. [progress] is 0f–1f when Content-Length is known;
     * -1f when the server did not send Content-Length (indeterminate).
     */
    data class Downloading(val progress: Float) : DownloadState()
    /** Both files are ready on disk. */
    data class Ready(val mp3: File, val transcript: List<TranscriptSegment>) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

// ── Player UI state ─────────────────────────────────────────────────────────────

data class AyaAudioState(
    val suraNum:    Int            = 0,
    val ayaNum:     Int            = 0,
    val readerName: String         = "",
    val isPlaying:  Boolean        = false,
    /** 0f–1f progress across the full surah audio file. */
    val progress:   Float          = 0f,
    val active:     Boolean        = false,
    val loadState:  AudioLoadState = AudioLoadState.Idle,
)

sealed class AudioLoadState {
    object Idle       : AudioLoadState()
    /** Connecting / waiting for the first byte. Progress bar is indeterminate. */
    object Connecting : AudioLoadState()
    /**
     * Downloading the surah audio.
     * [progress] is 0f–1f for real progress; -1f means indeterminate
     * (e.g. Content-Length absent on the response).
     */
    data class Downloading(val progress: Float) : AudioLoadState()
    object Ready                                : AudioLoadState()
    data class Error(val message: String)       : AudioLoadState()
}

// ── Transcript JSON ───────────────────────────────────────────────────────────

/**
 * One recited segment — maps 1:1 to an aya (or the standalone basmala).
 * Word-level data is intentionally omitted; only segment-level timing is used.
 */
data class TranscriptSegment(
    val start: Double,
    val end:   Double,
)

// ── Playback source resolved from transcript ──────────────────────────────────

/**
 * Resolved seek position and aya timeline for a surah session.
 *
 * [seekMs]   — where to seek before starting playback for the selected aya.
 * [timeline] — ordered list of (ayaNum, startMs) for auto-advance.
 */
data class PlaybackSource(
    val seekMs:   Int,
    val timeline: List<Pair<Int, Int>>,
)