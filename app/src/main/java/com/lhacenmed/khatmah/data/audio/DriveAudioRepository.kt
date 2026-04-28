package com.lhacenmed.khatmah.data.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ── Download state ────────────────────────────────────────────────────────────

sealed class DownloadState {
    /** Checking cache / connecting to Drive — length unknown yet. */
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

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Manages fetching and caching of reader audio files from Google Drive.
 *
 * Cache layout (all under context.filesDir/audio/):
 *   {readerId}/index.json          — folder listing: surahNum → {mp3Id, jsonId}
 *   {readerId}/{surahNum}.mp3      — completed audio file
 *   {readerId}/{surahNum}.mp3.tmp  — partial download (resumable)
 *   {readerId}/{surahNum}.json     — transcript JSON
 *
 * Resumable downloads:
 *   If a .tmp file exists when a download is requested, its byte length is sent
 *   as a Range header (Range: bytes={n}-). The server responds with 206 and the
 *   remaining bytes, which are appended to the existing .tmp file. On completion
 *   the .tmp is renamed to the final .mp3. Progress accounts for already-downloaded
 *   bytes so the progress bar never jumps backward on resume.
 *
 * Flow emitted by [prepareSurah]:
 *   Connecting → Downloading(-1f | 0f..1f) → Ready | Error
 *
 * The JSON is downloaded first (small, no progress), then the MP3 (large, resumable,
 * progress reported). Transcript is parsed before the MP3 completes so it is
 * immediately available when Ready fires.
 */
class DriveAudioRepository(private val context: Context) {

    private val cacheRoot = File(context.filesDir, "audio")

    // ── Manifest ──────────────────────────────────────────────────────────────

    fun readers(): ReaderManifest {
        val json = context.assets.open("readers.json").bufferedReader().readText()
        val arr  = JSONObject(json).getJSONArray("readers")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ReaderInfo(o.getString("id"), o.getString("name"), o.getString("folderId"))
        }
        return ReaderManifest(list)
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    /**
     * Cold [Flow] that drives the full prepare → play pipeline for one surah.
     *
     * Callers collect this flow; each emission reflects the current phase.
     * The flow completes after emitting [DownloadState.Ready] or [DownloadState.Error].
     */
    fun prepareSurah(readerId: String, folderId: String, surahNum: Int): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting)

        val dir      = readerDir(readerId)
        val mp3File  = File(dir, "$surahNum.mp3")
        val jsonFile = File(dir, "$surahNum.json")

        // ── Cache hit ─────────────────────────────────────────────────────────
        if (mp3File.exists() && jsonFile.exists()) {
            emit(DownloadState.Ready(mp3File, parseTranscript(jsonFile)))
            return@flow
        }

        // ── Auth ──────────────────────────────────────────────────────────────
        val token = runCatching { DriveAuth.token(context) }.getOrElse {
            emit(DownloadState.Error("Auth failed: ${it.message}")); return@flow
        }

        // ── Resolve file IDs ──────────────────────────────────────────────────
        val index = runCatching { resolveIndex(readerId, folderId, token) }.getOrElse {
            emit(DownloadState.Error("Index failed: ${it.message}")); return@flow
        }

        val files = index.surahs[surahNum.toString()]
        if (files == null) {
            emit(DownloadState.Error("Surah $surahNum not found in reader index")); return@flow
        }

        dir.mkdirs()

        // ── Download JSON (small — no progress needed) ────────────────────────
        if (!jsonFile.exists()) {
            runCatching { downloadSmall(files.jsonId, token, jsonFile) }.getOrElse {
                emit(DownloadState.Error("JSON download failed: ${it.message}")); return@flow
            }
        }
        val transcript = parseTranscript(jsonFile)

        // ── Download MP3 (large — resumable, progress reported) ───────────────
        if (!mp3File.exists()) {
            downloadResumable(files.mp3Id, token, mp3File).collect { emit(it) }
            if (!mp3File.exists()) {
                emit(DownloadState.Error("MP3 download incomplete")); return@flow
            }
        }

        emit(DownloadState.Ready(mp3File, transcript))
    }.flowOn(Dispatchers.IO)

    // ── Seek resolver ─────────────────────────────────────────────────────────

    /**
     * Resolves [PlaybackSource] for the selected aya.
     *
     * Segment → aya mapping:
     *   If segment[0] starts with "بِسْمِ" (standalone basmala) and the surah is
     *   not Al-Fatiha (1) or At-Tawbah (9), segment[i] maps to aya i (1-based).
     *   Otherwise segment[i] maps to aya i+1 (0-based indexing, no basmala segment).
     *
     * Seek rules:
     *   • First playable aya  → seekMs = 0  (ignore transcript timing)
     *   • Any other aya       → seekMs = segment.start * 1000
     */
    fun resolvePlayback(
        segments: List<TranscriptSegment>,
        surahNum: Int,
        ayaNum:   Int,
    ): PlaybackSource {
        val hasBasmala = surahNum != 1 && surahNum != 9 &&
                segments.isNotEmpty() &&
                // Detect standalone basmala by checking segment[0] timing:
                // basmala segments are always the very first and start near 0s.
                // We identify them structurally — surah 1 and 9 are excluded above,
                // and all other surahs with a separate basmala will have one more
                // segment than their aya count.
                segments.size > ayaCountHint(surahNum)

        // Build aya → startMs timeline (numbered ayas only, basmala excluded).
        val timeline: List<Pair<Int, Int>> = buildList {
            if (hasBasmala) {
                // segment[0] = basmala, segment[i] = aya i  (i >= 1)
                for (i in 1 until segments.size) {
                    add(i to (segments[i].start * 1000).toInt())
                }
            } else {
                // segment[i] = aya i+1
                for (i in segments.indices) {
                    add(i + 1 to (segments[i].start * 1000).toInt())
                }
            }
        }

        // Segment index for the requested aya.
        val segIdx = if (hasBasmala) ayaNum else ayaNum - 1
        val isFirst = segIdx <= (if (hasBasmala) 1 else 0)

        val seekMs = if (isFirst) 0
        else ((segments.getOrNull(segIdx)?.start ?: 0.0) * 1000).toInt()

        return PlaybackSource(seekMs = seekMs, timeline = timeline)
    }

    // ── Index resolution ──────────────────────────────────────────────────────

    private suspend fun resolveIndex(
        readerId: String,
        folderId: String,
        token:    String,
    ): ReaderIndex = withContext(Dispatchers.IO) {
        val indexFile = File(readerDir(readerId), "index.json")
        if (indexFile.exists()) return@withContext loadIndex(indexFile)

        val index = fetchDriveIndex(folderId, token)
        readerDir(readerId).mkdirs()
        saveIndex(indexFile, index)
        index
    }

    /**
     * Lists all files in [folderId] and builds surahNum → SurahFiles.
     * Handles pagination via nextPageToken.
     */
    private fun fetchDriveIndex(folderId: String, token: String): ReaderIndex {
        val surahs    = mutableMapOf<String, MutableMap<String, String>>()
        var pageToken: String? = null

        do {
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files")
                append("?q=${encode("'$folderId' in parents and trashed=false")}")
                append("&fields=${encode("nextPageToken,files(id,name)")}")
                append("&pageSize=1000")
                if (pageToken != null) append("&pageToken=${encode(pageToken!!)}")
            }
            val resp  = getJson(url, token)
            val files = resp.getJSONArray("files")
            pageToken = resp.optString("nextPageToken").ifEmpty { null }

            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val name = file.getString("name")
                val id   = file.getString("id")
                val dot  = name.lastIndexOf('.').takeIf { it >= 0 } ?: continue
                val num  = name.substring(0, dot)
                val ext  = name.substring(dot + 1)
                if (ext == "mp3" || ext == "json") {
                    surahs.getOrPut(num) { mutableMapOf() }[ext] = id
                }
            }
        } while (pageToken != null)

        val result = surahs
            .filter { (_, m) -> m.containsKey("mp3") && m.containsKey("json") }
            .mapValues { (_, m) -> SurahFiles(m["mp3"]!!, m["json"]!!) }

        return ReaderIndex(result)
    }

    // ── File downloads ────────────────────────────────────────────────────────

    /** Downloads [fileId] entirely to [dest] — no progress, for small files. */
    private fun downloadSmall(fileId: String, token: String, dest: File) {
        val conn = openDriveConn(fileId, token, resumeFrom = 0L)
        conn.inputStream.use { it.copyTo(dest.outputStream()) }
        conn.disconnect()
    }

    /**
     * Downloads [fileId] to [dest] with resumable support and progress reporting.
     *
     * If a partial file ([dest].tmp) exists its byte count is sent as a Range
     * header, and new bytes are appended. Progress is computed over the full
     * file size (already-downloaded + remaining) so it never regresses on resume.
     */
    private fun downloadResumable(
        fileId: String,
        token:  String,
        dest:   File,
    ): Flow<DownloadState> = flow {
        val tmp       = File(dest.parent, "${dest.name}.tmp")
        val resumeAt  = if (tmp.exists()) tmp.length() else 0L

        val conn      = openDriveConn(fileId, token, resumeFrom = resumeAt)
        val status    = conn.responseCode

        // 416 = Range Not Satisfiable → server file is smaller than our .tmp;
        // the .tmp is corrupt, delete and restart from 0.
        if (status == HttpURLConnection.HTTP_REQ_TOO_LONG /* 413 */ ||
            status == 416 /* Range Not Satisfiable */) {
            conn.disconnect()
            tmp.delete()
            downloadResumable(fileId, token, dest).collect { emit(it) }
            return@flow
        }

        // Content-Range response tells us the total file size; for a fresh
        // download use Content-Length directly.
        val totalBytes: Long = when (status) {
            206  -> conn.getHeaderField("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull() ?: -1L
            else -> conn.contentLengthLong
        }

        val alreadyBytes = if (status == 206) resumeAt else 0L
        val outStream    = tmp.outputStream().apply { if (status == 206) channel.position(alreadyBytes) }

        // For append mode we reopen as RandomAccessFile to seek to end.
        val raf = java.io.RandomAccessFile(tmp, "rw").apply { seek(length()) }

        conn.inputStream.use { input ->
            val buf     = ByteArray(32 * 1024)
            var written = alreadyBytes
            var read:   Int
            while (input.read(buf).also { read = it } != -1) {
                raf.write(buf, 0, read)
                written += read
                val progress = if (totalBytes > 0) (written.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
                emit(DownloadState.Downloading(progress))
            }
        }
        raf.close()
        outStream.close()
        conn.disconnect()

        tmp.renameTo(dest)
    }.flowOn(Dispatchers.IO)

    // ── Transcript parsing ────────────────────────────────────────────────────

    /**
     * Parses only [start] and [end] from each segment array entry.
     * The words array is intentionally skipped — it is not used at the app layer
     * and skipping it avoids parsing tens of KB of word-level data per surah.
     */
    fun parseTranscript(jsonFile: File): List<TranscriptSegment> {
        val arr = JSONArray(jsonFile.readText())
        return List(arr.length()) { i ->
            val seg = arr.getJSONObject(i)
            TranscriptSegment(start = seg.getDouble("start"), end = seg.getDouble("end"))
        }
    }

    // ── Index persistence ─────────────────────────────────────────────────────

    private fun saveIndex(file: File, index: ReaderIndex) {
        val surahs = JSONObject()
        index.surahs.forEach { (num, files) ->
            surahs.put(num, JSONObject().apply {
                put("mp3Id",  files.mp3Id)
                put("jsonId", files.jsonId)
            })
        }
        file.writeText(JSONObject().apply { put("surahs", surahs) }.toString())
    }

    private fun loadIndex(file: File): ReaderIndex {
        val surahs = JSONObject(file.readText()).getJSONObject("surahs")
        return ReaderIndex(
            surahs.keys().asSequence().associateWith { key ->
                val v = surahs.getJSONObject(key)
                SurahFiles(v.getString("mp3Id"), v.getString("jsonId"))
            }
        )
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /**
     * Opens a Drive media connection for [fileId].
     * If [resumeFrom] > 0 the Range header is added for partial content.
     */
    private fun openDriveConn(fileId: String, token: String, resumeFrom: Long): HttpURLConnection =
        (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
            connectTimeout = 15_000
            readTimeout    = 0
            connect()
        }

    private fun getJson(url: String, token: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 30_000
            connect()
        }
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(text)
    }

    private fun readerDir(readerId: String) = File(cacheRoot, readerId)

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ── Aya count hint for basmala detection ──────────────────────────────────

    /**
     * Returns the number of ayas in [surahNum] from the standard Quran count.
     * Used only to detect whether segment[0] is a standalone basmala by comparing
     * against segments.size. A mismatch (segments.size > ayaCount) means basmala
     * occupies segment[0].
     *
     * Stored as a flat IntArray indexed by surah number (1-based, index 0 unused).
     */
    private fun ayaCountHint(surahNum: Int): Int =
        if (surahNum in 1..114) AYA_COUNTS[surahNum] else 0

    companion object {
        private val AYA_COUNTS = intArrayOf(
            0,   // index 0 unused
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
    }
}