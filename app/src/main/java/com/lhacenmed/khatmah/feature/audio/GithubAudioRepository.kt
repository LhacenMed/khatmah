package com.lhacenmed.khatmah.feature.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ── Reader manifest (from assets/recitations.json) ─────────────────────────────

/** One reader available for a riwaya. */
data class GhReader(val id: String, val name: String)

// ── Repository ─────────────────────────────────────────────────────────────────

/**
 * Streams reader recitations from the public **khatmah-recitations** GitHub repo over
 * `raw.githubusercontent.com` — the same delivery model as the QCF4 data bundles, but
 * one transcript + one audio file per surah.
 *
 * Unlike [DriveAudioRepository] there is no auth and no folder-index step: every file lives
 * at a deterministic URL built from riwaya + reader id + surah number, so a surah is exactly
 * one small transcript fetch followed by one streamed audio fetch.
 *
 * Reuses the shared [DownloadState] / [TranscriptSegment] / [PlaybackSource] models.
 *
 * Cache layout (filesDir/recitations/{riwaya}/{readerId}/):
 *   {surah}.opus       — completed audio
 *   {surah}.opus.tmp   — in-flight download (renamed to .opus on completion)
 *   {surah}.json       — transcript
 */
class GithubAudioRepository(private val context: Context) {

    private val cacheRoot = File(context.filesDir, "recitations")

    // ── Manifest ────────────────────────────────────────────────────────────────

    /** Readers configured for [riwaya] ("hafs" | "warsh"); empty when none. */
    fun readersFor(riwaya: String): List<GhReader> {
        val json = context.assets.open("recitations.json").bufferedReader().readText()
        val arr  = JSONObject(json).optJSONArray(riwaya) ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GhReader(o.getString("id"), o.getString("name"))
        }
    }

    // ── Prepare → play pipeline ───────────────────────────────────────────────────

    /**
     * Cold [Flow] driving the prepare → play pipeline for one surah:
     *   Connecting → Downloading(0f..1f | -1f) → Ready | Error
     *
     * The transcript is fetched first (small, no progress) so it is parsed and ready by the
     * time [DownloadState.Ready] fires; the audio is streamed to a `.tmp` file and renamed on
     * completion, so an interrupted download never leaves a half-written `.opus`.
     */
    fun prepareSurah(riwaya: String, readerId: String, surahNum: Int): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting)

        val dir   = readerDir(riwaya, readerId).apply { mkdirs() }
        val audio = File(dir, "$surahNum.opus")
        val json  = File(dir, "$surahNum.json")

        // ── Cache hit ───────────────────────────────────────────────────────────
        if (audio.exists() && json.exists()) {
            emit(DownloadState.Ready(audio, parseTranscript(json))); return@flow
        }

        // ── Transcript (small — no progress) ──────────────────────────────────────
        if (!json.exists()) {
            runCatching { downloadSmall(transcriptUrl(riwaya, readerId, surahNum), json) }.getOrElse {
                emit(DownloadState.Error("تعذّر تحميل النص: ${it.message}")); return@flow
            }
        }
        val transcript = parseTranscript(json)

        // ── Audio (large — progress reported) ─────────────────────────────────────
        if (!audio.exists()) {
            try {
                downloadAudio(audioUrl(riwaya, readerId, surahNum), audio).collect { emit(it) }
            } catch (e: Exception) {
                emit(DownloadState.Error("تعذّر تحميل التلاوة: ${e.message}")); return@flow
            }
            if (!audio.exists()) { emit(DownloadState.Error("اكتمل التحميل بشكل غير مكتمل")); return@flow }
        }

        emit(DownloadState.Ready(audio, transcript))
    }.flowOn(Dispatchers.IO)

    // ── Seek resolver (riwaya-aware) ──────────────────────────────────────────────

    /**
     * Resolves [PlaybackSource] for the selected aya — segment→aya mapping identical to
     * [DriveAudioRepository.resolvePlayback], but the basmala-detection aya count is taken
     * from the [riwaya]'s own count table (Warsh and Hafs differ).
     */
    fun resolvePlayback(
        segments: List<TranscriptSegment>,
        riwaya:   String,
        surahNum: Int,
        ayaNum:   Int,
    ): PlaybackSource {
        val hasBasmala = surahNum != 1 && surahNum != 9 &&
                segments.isNotEmpty() &&
                segments.size > ayaCountHint(riwaya, surahNum)

        val timeline: List<Pair<Int, Int>> = buildList {
            if (hasBasmala) {
                for (i in 1 until segments.size) add(i to (segments[i].start * 1000).toInt())
            } else {
                for (i in segments.indices) add(i + 1 to (segments[i].start * 1000).toInt())
            }
        }

        val segIdx  = if (hasBasmala) ayaNum else ayaNum - 1
        val isFirst = segIdx <= (if (hasBasmala) 1 else 0)
        val seekMs  = if (isFirst) 0
        else ((segments.getOrNull(segIdx)?.start ?: 0.0) * 1000).toInt()

        return PlaybackSource(seekMs = seekMs, timeline = timeline)
    }

    // ── Transcript parsing ─────────────────────────────────────────────────────────

    /** Parses only [TranscriptSegment.start]/[end]; the per-word array is skipped. */
    fun parseTranscript(jsonFile: File): List<TranscriptSegment> {
        val arr = JSONArray(jsonFile.readText())
        return List(arr.length()) { i ->
            val seg = arr.getJSONObject(i)
            TranscriptSegment(start = seg.getDouble("start"), end = seg.getDouble("end"))
        }
    }

    // ── Downloads ─────────────────────────────────────────────────────────────────

    /** Downloads [url] entirely to [dest] — for small files (transcript JSON). */
    private fun downloadSmall(url: String, dest: File) {
        val conn = openWithRedirects(url)
        try {
            dest.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Streams [url] to `[dest].tmp` with byte-level progress, then renames to [dest].
     * Emits [DownloadState.Downloading] (0f..1f, or -1f when Content-Length is absent);
     * never emits Ready — the caller does that once the file is in place.
     */
    private fun downloadAudio(url: String, dest: File): Flow<DownloadState> = flow {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        tmp.delete()
        val conn = openWithRedirects(url)
        try {
            val total    = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var received = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        yield()
                        out.write(buf, 0, n)
                        received += n
                        emit(DownloadState.Downloading(
                            if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else -1f
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            tmp.delete(); throw e
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(dest)
    }.flowOn(Dispatchers.IO)

    // ── HTTP helper ─────────────────────────────────────────────────────────────────

    /** Opens [url], manually following cross-host redirects (matches the QCF4 repos). */
    private fun openWithRedirects(url: String): HttpURLConnection {
        var location = url
        repeat(5) {
            val conn = (URL(location).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout    = 0
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next != null) { location = next; return@repeat }
            }
            return conn
        }
        error("Too many redirects for $url")
    }

    // ── Paths ─────────────────────────────────────────────────────────────────────

    private fun readerDir(riwaya: String, readerId: String) = File(cacheRoot, "$riwaya/$readerId")

    private fun audioUrl(riwaya: String, readerId: String, surah: Int) =
        "$BASE/$riwaya/$readerId/audio/$surah.opus"

    private fun transcriptUrl(riwaya: String, readerId: String, surah: Int) =
        "$BASE/$riwaya/$readerId/transcript/$surah.json"

    private fun ayaCountHint(riwaya: String, surahNum: Int): Int =
        if (surahNum in 1..114) (if (riwaya == "warsh") AYA_COUNTS_WARSH else AYA_COUNTS_HAFS)[surahNum] else 0

    companion object {
        private const val BASE =
            "https://raw.githubusercontent.com/LhacenMed/khatmah-recitations/main"

        // Aya counts per surah (1-based, index 0 unused) — used only to detect a standalone
        // basmala segment by comparing against the transcript's segment count.
        private val AYA_COUNTS_HAFS = intArrayOf(
            0,
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
            123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
            112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
            54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
            60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
            14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
            28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
            29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
            15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
            11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
            5, 4, 5, 6,
        )

        private val AYA_COUNTS_WARSH = intArrayOf(
            0,
            7, 285, 200, 175, 122, 167, 206, 76, 130, 109,
            121, 111, 44, 54, 99, 128, 110, 105, 99, 134,
            111, 76, 119, 62, 77, 226, 95, 88, 69, 59,
            33, 30, 73, 54, 46, 82, 182, 86, 72, 84,
            53, 50, 89, 56, 36, 34, 39, 29, 18, 45,
            60, 47, 61, 55, 77, 99, 28, 21, 24, 13,
            14, 11, 11, 18, 12, 12, 31, 52, 52, 44,
            30, 28, 18, 55, 39, 31, 50, 40, 45, 42,
            29, 19, 36, 25, 22, 17, 19, 26, 32, 20,
            15, 21, 11, 8, 8, 20, 5, 8, 9, 11,
            10, 8, 3, 9, 5, 5, 6, 3, 6, 3,
            5, 4, 5, 6,
        )
    }
}
