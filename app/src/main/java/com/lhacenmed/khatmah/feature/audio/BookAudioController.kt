package com.lhacenmed.khatmah.feature.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Activity-scoped audio controller for the book reader.
 *
 * Instance-owned (the book reader is its own Activity) and sources audio from
 * [GithubAudioRepository] over raw.githubusercontent. It reuses the shared
 * [AyaAudioState] / [AudioLoadState] models so the player-bar rendering stays identical to the
 * Compose readers'.
 *
 * Long-pressing another aya in the *same* surah just seeks (no re-download); a different surah
 * cancels any in-flight download and starts fresh. Call [release] from the host's `onDestroy`.
 */
class BookAudioController(context: Context) {

    private val repo  = GithubAudioRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AyaAudioState())
    val state: StateFlow<AyaAudioState> = _state.asStateFlow()

    private var player:      MediaPlayer? = null
    private var progressJob: Job?         = null
    private var downloadJob: Job?         = null

    private var ayaTimeline:   List<Pair<Int, Int>>  = emptyList()
    private var curTranscript: List<TranscriptSegment> = emptyList()
    private var curRiwaya = ""
    private var curSura   = 0

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Downloads (if needed) then plays [suraNum]/[ayaNum] for the given reader. */
    fun play(riwaya: String, readerId: String, readerName: String, suraNum: Int, ayaNum: Int) {
        // Same surah already playable → just seek to the new aya, skip the download pipeline.
        if (suraNum == curSura && riwaya == curRiwaya &&
            _state.value.loadState is AudioLoadState.Ready && player != null
        ) {
            seekToAya(ayaNum); return
        }

        downloadJob?.cancel()
        releasePlayer()
        curRiwaya = riwaya
        curSura   = suraNum

        _state.value = AyaAudioState(
            suraNum    = suraNum,
            ayaNum     = ayaNum,
            readerName = readerName,
            active     = true,
            loadState  = AudioLoadState.Connecting,
        )

        downloadJob = scope.launch {
            repo.prepareSurah(riwaya, readerId, suraNum).collect { dl ->
                when (dl) {
                    DownloadState.Connecting ->
                        _state.value = _state.value.copy(loadState = AudioLoadState.Connecting)

                    is DownloadState.Downloading ->
                        _state.value = _state.value.copy(loadState = AudioLoadState.Downloading(dl.progress))

                    is DownloadState.Ready ->
                        startPlayback(dl.mp3, dl.transcript, riwaya, suraNum, ayaNum)

                    is DownloadState.Error ->
                        _state.value = _state.value.copy(loadState = AudioLoadState.Error(dl.message))
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

    /** Stops playback and hides the bar (resets to an inactive state). */
    fun stop() {
        downloadJob?.cancel()
        releasePlayer()
        ayaTimeline   = emptyList()
        curTranscript = emptyList()
        curSura       = 0
        curRiwaya     = ""
        _state.value  = AyaAudioState()
    }

    /** Releases the player and cancels the scope — call from the host Activity's onDestroy. */
    fun release() {
        downloadJob?.cancel()
        releasePlayer()
        scope.cancel()
    }

    // ── Playback ──────────────────────────────────────────────────────────────────

    private fun startPlayback(
        audio:      File,
        transcript: List<TranscriptSegment>,
        riwaya:     String,
        suraNum:    Int,
        ayaNum:     Int,
    ) {
        curTranscript = transcript
        val source  = repo.resolvePlayback(transcript, riwaya, suraNum, ayaNum)
        ayaTimeline = source.timeline

        player = MediaPlayer().apply {
            setDataSource(audio.absolutePath)
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

    /** Seeks within the already-loaded surah to [ayaNum] and resumes playback. */
    private fun seekToAya(ayaNum: Int) {
        val p = player ?: return
        val source = repo.resolvePlayback(curTranscript, curRiwaya, curSura, ayaNum)
        p.seekTo(source.seekMs)
        if (!p.isPlaying) p.start()
        _state.value = _state.value.copy(ayaNum = ayaNum, isPlaying = true)
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

                val current = _state.value
                val curAya  = resolveAyaAt(pos)
                _state.value = if (curAya != null && curAya != current.ayaNum)
                    current.copy(ayaNum = curAya, progress = progress)
                else
                    current.copy(progress = progress)

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

    /** The aya whose window [startMs, nextStartMs) contains [posMs], or null if no timeline. */
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
