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

// ── Download state ────────────────────────────────────────────────────────────

sealed class DownloadState {
    /** Checking cache / connecting to Drive — length unknown yet. */
    object Connecting : DownloadState()
    /** Actively downloading. [progress] is 0f–1f. */
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
 *   {readerId}/{surahNum}.mp3      — audio file
 *   {readerId}/{surahNum}.json     — transcript JSON
 *
 * Flow emitted by [prepareSurah]:
 *   Connecting → Downloading(0f..1f) → Ready | Error
 *
 * The JSON and MP3 downloads are pipelined: JSON first (small, fast), then MP3
 * (large, progress reported). While MP3 downloads, the transcript is already
 * parsed so playback can begin the moment the MP3 write completes.
 */
class DriveAudioRepository(private val context: Context) {

    private val cacheRoot = File(context.filesDir, "audio")
    private val gson      = SimpleJson  // lightweight JSON helpers below

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
     * Returns a cold [Flow] that:
     * 1. Emits [DownloadState.Connecting] immediately.
     * 2. Checks cache — if both files exist, emits [DownloadState.Ready] and completes.
     * 3. Otherwise resolves file IDs (from cached index or fresh Drive listing),
     *    downloads JSON then MP3, emitting [DownloadState.Downloading] during the MP3,
     *    then emits [DownloadState.Ready].
     */
    fun prepareSurah(readerId: String, folderId: String, surahNum: Int): Flow<DownloadState> = flow {
        emit(DownloadState.Connecting)

        val dir      = readerDir(readerId)
        val mp3File  = File(dir, "$surahNum.mp3")
        val jsonFile = File(dir, "$surahNum.json")

        // ── Cache hit ─────────────────────────────────────────────────────────
        if (mp3File.exists() && jsonFile.exists()) {
            val transcript = parseTranscript(jsonFile)
            emit(DownloadState.Ready(mp3File, transcript))
            return@flow
        }

        // ── Resolve file IDs ──────────────────────────────────────────────────
        val token = runCatching { DriveAuth.token(context) }.getOrElse {
            emit(DownloadState.Error("Auth failed: ${it.message}")); return@flow
        }

        val index = runCatching { resolveIndex(readerId, folderId, token) }.getOrElse {
            emit(DownloadState.Error("Index failed: ${it.message}")); return@flow
        }

        val files = index.surahs[surahNum.toString()]
        if (files == null) {
            emit(DownloadState.Error("Surah $surahNum not found in reader index"))
            return@flow
        }

        dir.mkdirs()

        // ── Download JSON first (small, fast) ─────────────────────────────────
        if (!jsonFile.exists()) {
            runCatching { downloadFile(files.jsonId, token, jsonFile) }.getOrElse {
                emit(DownloadState.Error("JSON download failed: ${it.message}")); return@flow
            }
        }
        val transcript = parseTranscript(jsonFile)

        // ── Download MP3 with progress ────────────────────────────────────────
        if (!mp3File.exists()) {
            downloadFileWithProgress(files.mp3Id, token, mp3File).collect { state ->
                emit(state)
            }
            // Check that the MP3 actually landed
            if (!mp3File.exists()) {
                emit(DownloadState.Error("MP3 download failed")); return@flow
            }
        }

        emit(DownloadState.Ready(mp3File, transcript))
    }.flowOn(Dispatchers.IO)

    // ── Index resolution ──────────────────────────────────────────────────────

    /**
     * Returns the reader index from disk cache, or fetches it from Drive if absent.
     * The index maps surah number strings → SurahFiles (mp3Id, jsonId).
     */
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
     * Lists all files in [folderId] and builds a map of surahNum → SurahFiles.
     * Files are named "{n}.mp3" and "{n}.json". Handles pagination via nextPageToken.
     */
    private fun fetchDriveIndex(folderId: String, token: String): ReaderIndex {
        val surahs  = mutableMapOf<String, MutableMap<String, String>>() // surahNum → {ext → fileId}
        var pageToken: String? = null

        do {
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files")
                append("?q=${encode("'$folderId' in parents and trashed=false")}")
                append("&fields=${encode("nextPageToken,files(id,name)")}")
                append("&pageSize=1000")
                if (pageToken != null) append("&pageToken=${encode(pageToken!!)}")
            }
            val resp   = getJson(url, token)
            val files  = resp.getJSONArray("files")
            pageToken  = if (resp.has("nextPageToken")) resp.getString("nextPageToken") else null

            for (i in 0 until files.length()) {
                val file = files.getJSONObject(i)
                val name = file.getString("name")   // e.g. "87.mp3" or "87.json"
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

    // ── File download ─────────────────────────────────────────────────────────

    /** Downloads [fileId] to [dest] without progress reporting (for small files). */
    private fun downloadFile(fileId: String, token: String, dest: File) {
        val url  = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val conn = openAuthConn(url, token)
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        conn.disconnect()
    }

    /**
     * Downloads [fileId] to [dest] emitting [DownloadState.Downloading] with real
     * progress derived from Content-Length. If Content-Length is absent the progress
     * stays indeterminate (emits [DownloadState.Connecting]).
     */
    private fun downloadFileWithProgress(
        fileId: String,
        token:  String,
        dest:   File,
    ): Flow<DownloadState> = flow {
        val url    = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val conn   = openAuthConn(url, token)
        val total  = conn.contentLengthLong   // -1 if unknown
        val tmp    = File(dest.parent, "${dest.name}.tmp")

        conn.inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf     = ByteArray(8 * 1024)
                var written = 0L
                var read:   Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    written += read
                    val progress = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else -1f
                    if (progress >= 0f) emit(DownloadState.Downloading(progress))
                    else               emit(DownloadState.Connecting)   // indeterminate
                }
            }
        }
        conn.disconnect()
        tmp.renameTo(dest)
    }.flowOn(Dispatchers.IO)

    // ── Transcript parsing ────────────────────────────────────────────────────

    fun parseTranscript(jsonFile: File): List<TranscriptSegment> {
        val arr = JSONArray(jsonFile.readText())
        return (0 until arr.length()).map { i ->
            val seg   = arr.getJSONObject(i)
            val words = seg.getJSONArray("words")
            TranscriptSegment(
                start = seg.getDouble("start"),
                end   = seg.getDouble("end"),
                text  = seg.getString("text"),
                words = (0 until words.length()).map { j ->
                    val w = words.getJSONObject(j)
                    TranscriptWord(
                        word  = w.getString("word"),
                        start = w.getDouble("start"),
                        end   = w.getDouble("end"),
                        score = w.getDouble("score"),
                    )
                },
            )
        }
    }

    // ── Seek resolver ─────────────────────────────────────────────────────────

    /**
     * Resolves [PlaybackSource] for a given aya selection.
     *
     * Segment-to-aya mapping:
     *   If the transcript has one more segment than the surah's aya count (detected
     *   by checking if segment[0] contains "بسم"), segment[0] is a standalone basmala
     *   and segment[i] = aya i (1-based). Otherwise segment[i] = aya (i+1).
     *
     * Seek logic for a selected aya:
     *   - If it's the first recitable segment (index 0 or 1): seek to 0s.
     *   - Otherwise: seek to the `end` of the last word of the previous segment,
     *     falling back to the selected segment's `start` if words are empty.
     */
    fun resolvePlayback(segments: List<TranscriptSegment>, surahNum: Int, ayaNum: Int): PlaybackSource {
        val hasBasmala = surahNum != 1 && surahNum != 9 &&
                segments.isNotEmpty() && segments[0].words.firstOrNull()?.word?.startsWith("بِسْمِ") == true

        // Build aya timeline: ayaNum → startMs.
        // Timeline includes the basmala at ayaNum=0 if present (not user-selectable but needed for bounds).
        val timeline = mutableListOf<Pair<Int, Int>>()
        if (hasBasmala) {
            // segment[0] = basmala (aya 0), segment[i] = aya i for i >= 1
            timeline.add(0 to (segments[0].start * 1000).toInt())
            for (i in 1 until segments.size) {
                timeline.add(i to (segments[i].start * 1000).toInt())
            }
        } else {
            // segment[i] = aya (i+1)
            for (i in segments.indices) {
                timeline.add(i + 1 to (segments[i].start * 1000).toInt())
            }
        }

        // Find the segment index for the requested ayaNum.
        val segIdx = if (hasBasmala) ayaNum else ayaNum - 1
        val isFirstPlayable = segIdx <= (if (hasBasmala) 1 else 0)

        val seekMs = when {
            isFirstPlayable || segIdx <= 0 -> 0
            else -> {
                val prevSeg = segments.getOrNull(segIdx - 1)
                val prevEnd = prevSeg?.words?.lastOrNull()?.end
                    ?: prevSeg?.end
                    ?: segments[segIdx].start
                (prevEnd * 1000).toInt()
            }
        }

        return PlaybackSource(
            seekMs     = seekMs,
            timeline   = timeline.filter { it.first > 0 }, // expose only numbered ayas
            basmalaSeg = hasBasmala,
        )
    }

    // ── Index persistence ─────────────────────────────────────────────────────

    private fun saveIndex(file: File, index: ReaderIndex) {
        val obj = JSONObject()
        val surahs = JSONObject()
        index.surahs.forEach { (num, files) ->
            surahs.put(num, JSONObject().apply {
                put("mp3Id",  files.mp3Id)
                put("jsonId", files.jsonId)
            })
        }
        obj.put("surahs", surahs)
        file.writeText(obj.toString())
    }

    private fun loadIndex(file: File): ReaderIndex {
        val obj    = JSONObject(file.readText())
        val surahs = obj.getJSONObject("surahs")
        val map    = mutableMapOf<String, SurahFiles>()
        surahs.keys().forEach { key ->
            val v = surahs.getJSONObject(key)
            map[key] = SurahFiles(v.getString("mp3Id"), v.getString("jsonId"))
        }
        return ReaderIndex(map)
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun openAuthConn(url: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 0  // no timeout on large file reads
            connect()
        }

    private fun getJson(url: String, token: String): JSONObject {
        val conn = openAuthConn(url, token)
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return JSONObject(text)
    }

    private fun readerDir(readerId: String) = File(cacheRoot, readerId)

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}

// Intentional placeholder — avoids importing Gson or Moshi.
private object SimpleJson