package com.lhacenmed.khatmah.ui.page.quran

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Models ────────────────────────────────────────────────────────────────────

data class AyaAudioState(
    val suraNum:    Int     = 0,
    val ayaNum:     Int     = 0,
    val readerName: String  = "",
    val isPlaying:  Boolean = false,
    /** 0f–1f progress within the aya's segment (aya start → file end). */
    val progress:   Float   = 0f,
    val active:     Boolean = false,
)

// ── Manager ───────────────────────────────────────────────────────────────────

/**
 * Singleton audio manager for aya-level Quran playback.
 *
 * Asset layout:
 *   readers/{reader}/سورة {surahName}/{reader} - سورة {surahName}.mp3
 *   readers/{reader}/سورة {surahName}/{reader} - سورة {surahName}.txt
 *
 * The surahName passed in from the page is already stripped of the "سورة " prefix
 * (done by QuranPageBuilder / QuranRepository). We re-add it here to match the
 * asset folder/file naming convention.
 *
 * LRC format per line: [mm:ss.mmm]ayaNum
 * The [length:…] header line is ignored via the regex.
 */
object AyaAudioManager {

    // The only reader that currently has audio assets.
    const val READER_NAME = "الشيخ محمد لغظف ولد محمد سيدي"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AyaAudioState())
    val state: StateFlow<AyaAudioState> = _state.asStateFlow()

    private var player:      MediaPlayer? = null
    private var progressJob: Job?         = null
    private var ayaStartMs:  Int          = 0

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves the asset for [suraNum]/[ayaNum], seeks to the aya timestamp,
     * and begins playback. Returns false if no asset is found for this surah.
     *
     * [surahName] must be the bare name without the "سورة " prefix, e.g. "الأعلى".
     */
    fun play(context: Context, suraNum: Int, ayaNum: Int, surahName: String): Boolean {
        // Asset folders and filenames use the full prefixed name "سورة الأعلى".
        val fullName   = "سورة $surahName"
        val readerDir  = "readers/$READER_NAME/$fullName"
        val mp3Asset   = "$readerDir/$READER_NAME - $fullName.mp3"
        val lrcAsset   = "$readerDir/$READER_NAME - $fullName.txt"

        val timestamps = parseLrc(context, lrcAsset) ?: return false
        val startMs    = timestamps[ayaNum]           ?: return false

        releasePlayer()
        ayaStartMs = startMs

        val afd = runCatching { context.assets.openFd(mp3Asset) }.getOrNull() ?: return false

        player = MediaPlayer().apply {
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            prepare()
            seekTo(startMs)
            start()
            setOnCompletionListener { onPlaybackEnded() }
        }

        _state.value = AyaAudioState(
            suraNum    = suraNum,
            ayaNum     = ayaNum,
            readerName = READER_NAME,
            isPlaying  = true,
            progress   = 0f,
            active     = true,
        )

        startProgressUpdates()
        return true
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
        releasePlayer()
        _state.value = AyaAudioState()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val p = player ?: break
                if (!p.isPlaying) break
                val total    = (p.duration - ayaStartMs).toFloat()
                val elapsed  = (p.currentPosition - ayaStartMs).coerceAtLeast(0).toFloat()
                val progress = if (total > 0f) (elapsed / total).coerceIn(0f, 1f) else 0f
                _state.value = _state.value.copy(progress = progress)
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
     * Parses an LRC-style asset file.
     * Returns ayaNum → start time in milliseconds, or null if unreadable/empty.
     * The [length:…] header and any non-matching lines are silently skipped.
     */
    private fun parseLrc(context: Context, assetPath: String): Map<Int, Int>? {
        val lines = runCatching {
            context.assets.open(assetPath).bufferedReader().readLines()
        }.getOrNull() ?: return null

        val regex = Regex("""^\[(\d{2}):(\d{2})\.(\d{3})](\d+)$""")
        val map   = mutableMapOf<Int, Int>()

        for (line in lines) {
            val m = regex.matchEntire(line.trim()) ?: continue
            val (mm, ss, ms, aya) = m.destructured
            map[aya.toInt()] = mm.toInt() * 60_000 + ss.toInt() * 1_000 + ms.toInt()
        }

        return map.ifEmpty { null }
    }
}