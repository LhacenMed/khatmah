package com.lhacenmed.khatmah.ui.page.quran

import android.content.Context
import android.media.MediaPlayer
import com.lhacenmed.khatmah.data.audio.DownloadState
import com.lhacenmed.khatmah.data.audio.DriveAudioRepository
import com.lhacenmed.khatmah.data.audio.TranscriptSegment
import com.lhacenmed.khatmah.data.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

// ── Models ────────────────────────────────────────────────────────────────────

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
    /** Connecting to Drive or waiting for auth. Progress bar is indeterminate. */
    object Connecting : AudioLoadState()
    /**
     * Downloading files from Drive.
     * [progress] is 0f–1f for real progress; -1f means indeterminate
     * (e.g. Content-Length absent on the response).
     */
    data class Downloading(val progress: Float) : AudioLoadState()
    object Ready                                : AudioLoadState()
    data class Error(val message: String)       : AudioLoadState()
}

// ── Manager ───────────────────────────────────────────────────────────────────

/**
 * Singleton audio manager for aya-level Quran playback.
 *
 * Files are fetched from Google Drive via [DriveAudioRepository] and cached to
 * internal storage. The selected reader is resolved from [AppPrefs.audioReaderId],
 * falling back to the first reader in the manifest.
 *
 * Progress covers the full surah file (0 → 1). The progress loop also watches
 * for the next aya boundary and auto-advances [state] so the caller can highlight
 * the correct aya without restarting the MediaPlayer.
 *
 * Download progress is surfaced through [AyaAudioState.loadState]:
 *   Idle → Connecting → Downloading(0f..1f) → Ready  (or Error)
 */
object AyaAudioManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AyaAudioState())
    val state: StateFlow<AyaAudioState> = _state.asStateFlow()

    private var player:      MediaPlayer?         = null
    private var progressJob: Job?                 = null
    private var downloadJob: Job?                 = null

    /** Sorted (ayaNum, startMs) pairs for the currently loaded surah. */
    private var ayaTimeline: List<Pair<Int, Int>> = emptyList()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Initiates download (if needed) then playback for [suraNum]/[ayaNum].
     * [surahName] is the bare name without "سورة " prefix (kept for future use).
     *
     * Emits [AudioLoadState] updates through [state.loadState] during the
     * download phase. Returns immediately — playback starts asynchronously.
     */
    fun play(context: Context, suraNum: Int, ayaNum: Int, @Suppress("UNUSED_PARAMETER") surahName: String) {
        downloadJob?.cancel()
        releasePlayer()

        val repo    = DriveAudioRepository(context)
        val readers = repo.readers().readers
        val savedId = AppPrefs.audioReaderId.value
        val reader  = readers.firstOrNull { it.id == savedId } ?: readers.firstOrNull()

        if (reader == null) {
            _state.value = AyaAudioState(
                active    = true,
                loadState = AudioLoadState.Error("No readers configured"),
            )
            return
        }

        _state.value = AyaAudioState(
            suraNum    = suraNum,
            ayaNum     = ayaNum,
            readerName = reader.name,
            active     = true,
            loadState  = AudioLoadState.Connecting,
        )

        downloadJob = scope.launch {
            repo.prepareSurah(reader.id, reader.folderId, suraNum).collect { dlState ->
                when (dlState) {
                    DownloadState.Connecting ->
                        _state.value = _state.value.copy(loadState = AudioLoadState.Connecting)

                    is DownloadState.Downloading ->
                        _state.value = _state.value.copy(
                            loadState = AudioLoadState.Downloading(dlState.progress)
                        )

                    is DownloadState.Ready ->
                        startPlayback(dlState.mp3, dlState.transcript, suraNum, ayaNum, reader.name, repo)

                    is DownloadState.Error ->
                        _state.value = _state.value.copy(
                            loadState = AudioLoadState.Error(dlState.message)
                        )
                }
            }
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
            progressJob?.cancel()
            _state.value = _state.value.copy(isPlaying = false)
        } else {
            p.start()
            _state.value = _state.value.copy(isPlaying = true)
            startProgressUpdates()
        }
    }

    fun stop() {
        downloadJob?.cancel()
        releasePlayer()
        ayaTimeline  = emptyList()
        _state.value = AyaAudioState()
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private fun startPlayback(
        mp3:        File,
        transcript: List<TranscriptSegment>,
        suraNum:    Int,
        ayaNum:     Int,
        readerName: String,
        repo:       DriveAudioRepository,
    ) {
        val source  = repo.resolvePlayback(transcript, suraNum, ayaNum)
        ayaTimeline = source.timeline

        player = MediaPlayer().apply {
            setDataSource(mp3.absolutePath)
            prepare()
            seekTo(source.seekMs)
            start()
            setOnCompletionListener { onPlaybackEnded() }
        }

        _state.value = _state.value.copy(
            ayaNum    = ayaNum,
            isPlaying = true,
            progress  = 0f,
            loadState = AudioLoadState.Ready,
        )

        startProgressUpdates()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val p = player ?: break
                if (!p.isPlaying) break

                val pos      = p.currentPosition
                val duration = p.duration.takeIf { it > 0 } ?: break
                val progress = (pos.toFloat() / duration).coerceIn(0f, 1f)

                // Auto-advance: find the aya whose time window covers [pos].
                val current = _state.value
                val curAya  = resolveAyaAt(pos)
                if (curAya != null && curAya != current.ayaNum) {
                    _state.value = current.copy(ayaNum = curAya, progress = progress)
                } else {
                    _state.value = current.copy(progress = progress)
                }

                delay(200)
            }
        }
    }

    private fun onPlaybackEnded() {
        progressJob?.cancel()
        _state.value = _state.value.copy(isPlaying = false, progress = 1f)
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        player?.apply { if (isPlaying) stop(); release() }
        player = null
    }

    /**
     * Returns the ayaNum whose time window contains [posMs], or null if the
     * timeline is empty. An aya's window is [startMs, nextStartMs).
     */
    private fun resolveAyaAt(posMs: Int): Int? {
        val timeline = ayaTimeline
        if (timeline.isEmpty()) return null
        var result = timeline.first().first
        for ((ayaNum, startMs) in timeline) {
            if (posMs >= startMs) result = ayaNum else break
        }
        return result
    }
}