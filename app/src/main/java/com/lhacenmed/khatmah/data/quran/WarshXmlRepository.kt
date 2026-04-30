package com.lhacenmed.khatmah.data.quran

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.lhacenmed.khatmah.data.audio.DriveAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

// ── Models ────────────────────────────────────────────────────────────────────

/**
 * One clickable aya region on a Warsh mushaf page.
 * [polygon] — space-separated "x,y" pairs in the drawable's 0–235 viewport space.
 */
data class WarshAyaRegion(
    val surahNum: Int,
    val ayahNum:  Int,
    val polygon:  String,
)

/**
 * Fully prepared data for one rendered mushaf page.
 *
 * [pageNum]   — 1-based page number (1–604, full pages only, no surah variants).
 * [drawable]  — parsed vector drawable, ready to draw.
 * [viewportW] — viewport width (always 235, matching the JSON polygon coordinate space).
 * [viewportH] — viewport height (always 235).
 * [regions]   — per-aya polygon hit regions in viewport coordinate space.
 */
data class WarshPageData(
    val pageNum:   Int,
    val drawable:  Drawable,
    val viewportW: Float,
    val viewportH: Float,
    val regions:   List<WarshAyaRegion>,
)

// ── Download state ────────────────────────────────────────────────────────────

sealed class WarshDownloadState {
    /** Nothing downloaded yet. */
    object NotDownloaded : WarshDownloadState()
    /** Authenticating or fetching Drive index. */
    object Connecting    : WarshDownloadState()
    /**
     * Actively downloading. [progress] is 0f–1f (files completed / total files).
     */
    data class Downloading(val progress: Float) : WarshDownloadState()
    /** All XML + JSON files cached on disk — ready to use. */
    object Downloaded    : WarshDownloadState()
    data class Error(val message: String) : WarshDownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Manages parallel download and local caching of the Warsh mushaf drawable pages.
 *
 * Drive layout:
 *   xml-br folder — brotli-compressed Android <vector> XML drawables.
 *                   604 full-page files (001.xml.br … 604.xml.br) plus surah-variant
 *                   files (e.g. 106-surah4.xml.br). Total: 722 files.
 *   json folder   — JSON aya-polygon data, same naming convention. Total: 722 files.
 *
 * Cache layout (all under context.filesDir/warsh/):
 *   index.json          — Drive listing: { xmlBr: {stem: id}, json: {stem: id} }
 *   xml/{stem}.xml      — decompressed drawable (e.g. "106.xml", "106-surah4.xml")
 *   json/{stem}.json    — aya polygon JSON
 *
 * Rendering:
 *   Only full-page drawables are rendered (pages 1–[PAGE_COUNT] = 1–604).
 *   Surah-variant files (e.g. "106-surah4") are downloaded and cached but never
 *   shown in the pager — the full-page file already contains all content.
 *
 * Download strategy:
 *   Iterates over the actual Drive index entries (stems like "106", "106-surah4"),
 *   not assumed sequential numbers. Both XML and JSON batches run concurrently,
 *   each capped at [CONCURRENCY] parallel connections via a [Semaphore].
 *   An [AtomicInteger] tracks completed files without synchronization overhead.
 *
 * Viewport note:
 *   All drawables use android:viewportWidth/Height="235". JSON polygon coordinates
 *   are also in 0–235 space. [WarshPageData.viewportW/H] are always [VIEWPORT]
 *   so hit-testing is correctly aligned on all screen densities, regardless of
 *   the drawable's density-dependent intrinsicWidth.
 *
 * In-memory page cache: LRU, capped at [PAGE_CACHE_SIZE].
 */
class WarshXmlRepository(private val context: Context) {

    private val root    = File(context.filesDir, "warsh")
    private val xmlDir  = File(root, "xml")
    private val jsonDir = File(root, "json")

    // ── Download state ────────────────────────────────────────────────────────

    private val _downloadState = MutableStateFlow<WarshDownloadState>(
        if (isFullyDownloaded()) WarshDownloadState.Downloaded
        else WarshDownloadState.NotDownloaded
    )
    val downloadState = _downloadState.asStateFlow()

    // ── In-memory page LRU ────────────────────────────────────────────────────

    private val pageCache = object : LinkedHashMap<Int, WarshPageData>(PAGE_CACHE_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<Int, WarshPageData>) = size > PAGE_CACHE_SIZE
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * True when every stem listed in the saved Drive index has a file on disk.
     *
     * Checks against the index (not sequential numbers) so surah-variant stems
     * like "106-surah4" are included in the completeness check. Returns false
     * immediately if no index has been saved yet.
     */
    fun isFullyDownloaded(): Boolean {
        val indexFile = File(root, "index.json")
        if (!indexFile.exists()) return false
        return try {
            val index = loadIndex(indexFile)
            if (index.xmlBr.isEmpty() || index.json.isEmpty()) return false
            index.xmlBr.all { (stem, _) -> File(xmlDir,  "$stem.xml").exists()  } &&
                    index.json.all  { (stem, _) -> File(jsonDir, "$stem.json").exists() }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns [WarshPageData] for [pageNum] (1-based, 1–[PAGE_COUNT]).
     * Requires files to be on disk; results are cached in memory after first parse.
     *
     * [viewportW/H] are always [VIEWPORT] (235f) — not the drawable's intrinsicWidth
     * — so JSON polygon coordinates align correctly on all screen densities.
     */
    suspend fun pageData(pageNum: Int): WarshPageData = withContext(Dispatchers.IO) {
        require(pageNum in 1..PAGE_COUNT) { "pageNum $pageNum out of range 1..$PAGE_COUNT" }

        synchronized(pageCache) { pageCache[pageNum] }?.let { return@withContext it }

        val stem     = "%03d".format(pageNum)
        val xmlFile  = File(xmlDir,  "$stem.xml")
        val jsonFile = File(jsonDir, "$stem.json")

        val data = WarshPageData(
            pageNum   = pageNum,
            drawable  = parseDrawable(xmlFile),
            viewportW = VIEWPORT,
            viewportH = VIEWPORT,
            regions   = parseRegions(jsonFile),
        )

        synchronized(pageCache) { pageCache[pageNum] = data }
        data
    }

    /** Warms the page cache for [pageNum] without blocking the caller. */
    suspend fun prefetch(pageNum: Int) = withContext(Dispatchers.IO) {
        if (pageNum in 1..PAGE_COUNT) runCatching { pageData(pageNum) }
    }

    /**
     * Downloads all Warsh pages from Google Drive using parallel coroutines.
     *
     * Iterates over actual Drive index entries (stems like "106", "106-surah4"),
     * so all 722 XML files and all 722 JSON files are correctly downloaded
     * regardless of their naming pattern.
     *
     * Emits [WarshDownloadState] updates via the flow AND updates [_downloadState]
     * directly so observers of [downloadState] see live progress.
     * Idempotent: skips files that already exist on disk.
     */
    fun downloadAll(): Flow<WarshDownloadState> = flow {
        emit(WarshDownloadState.Connecting)
        _downloadState.value = WarshDownloadState.Connecting

        // ── Auth ──────────────────────────────────────────────────────────────
        val token = runCatching { DriveAuth.token(context) }.getOrElse {
            val err = WarshDownloadState.Error("Auth failed: ${it.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        // ── Drive index ───────────────────────────────────────────────────────
        val index = runCatching { resolveIndex(token) }.getOrElse {
            val err = WarshDownloadState.Error("Index failed: ${it.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        xmlDir.mkdirs()
        jsonDir.mkdirs()

        val totalFiles = index.xmlBr.size + index.json.size
        if (totalFiles == 0) {
            val err = WarshDownloadState.Error("Drive index returned no files")
            emit(err); _downloadState.value = err; return@flow
        }

        // Pre-count already-cached files so progress never resets on resume.
        val preExisting = index.xmlBr.keys.count { File(xmlDir,  "$it.xml").exists()  } +
                index.json.keys.count  { File(jsonDir, "$it.json").exists() }
        val completed   = AtomicInteger(preExisting)

        fun progress() = (completed.get().toFloat() / totalFiles).coerceIn(0f, 1f)

        val initState = WarshDownloadState.Downloading(progress())
        emit(initState); _downloadState.value = initState

        var encounteredError: WarshDownloadState.Error? = null
        val semaphore = Semaphore(CONCURRENCY)

        // ── Parallel download: XML + JSON concurrently ────────────────────────
        coroutineScope {
            val xmlJobs = index.xmlBr.map { (stem, fileId) ->
                async(Dispatchers.IO) {
                    val dest = File(xmlDir, "$stem.xml")
                    if (dest.exists()) return@async

                    semaphore.withPermit {
                        runCatching { downloadAndDecompress(fileId, token, dest) }
                            .onFailure {
                                encounteredError = WarshDownloadState.Error(
                                    "XML failed ($stem): ${it.message}"
                                )
                            }
                    }

                    _downloadState.value = WarshDownloadState.Downloading(
                        progress().also { completed.incrementAndGet() }
                    )
                }
            }

            val jsonJobs = index.json.map { (stem, fileId) ->
                async(Dispatchers.IO) {
                    val dest = File(jsonDir, "$stem.json")
                    if (dest.exists()) return@async

                    semaphore.withPermit {
                        runCatching { downloadSmall(fileId, token, dest) }
                            .onFailure {
                                encounteredError = WarshDownloadState.Error(
                                    "JSON failed ($stem): ${it.message}"
                                )
                            }
                    }

                    _downloadState.value = WarshDownloadState.Downloading(
                        progress().also { completed.incrementAndGet() }
                    )
                }
            }

            // Await all jobs — individual errors are collected, not thrown,
            // so all downloads complete before we check for failures.
            (xmlJobs + jsonJobs).awaitAll()
        }

        encounteredError?.let {
            emit(it); _downloadState.value = it; return@flow
        }

        emit(WarshDownloadState.Downloaded)
        _downloadState.value = WarshDownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    // ── Drive index ───────────────────────────────────────────────────────────

    private data class DriveIndex(
        val xmlBr: Map<String, String>,  // stem (e.g. "106", "106-surah4") → fileId
        val json:  Map<String, String>,
    )

    private fun resolveIndex(token: String): DriveIndex {
        val indexFile = File(root, "index.json")
        if (indexFile.exists()) return loadIndex(indexFile)
        val index = fetchIndex(token)
        root.mkdirs()
        saveIndex(indexFile, index)
        return index
    }

    private fun fetchIndex(token: String): DriveIndex {
        val xmlBr = fetchFolderFiles(FOLDER_XML_BR_ID, token)
        val json  = fetchFolderFiles(FOLDER_JSON_ID,   token)

        // Strip extension suffix to derive the stem:
        //   "106.xml.br"      → "106"
        //   "106-surah4.xml.br" → "106-surah4"
        fun Map<String, String>.stemMap(suffix: String) =
            entries.mapNotNull { (name, id) ->
                if (name.endsWith(suffix)) name.removeSuffix(suffix) to id else null
            }.toMap()

        return DriveIndex(xmlBr = xmlBr.stemMap(".xml.br"), json = json.stemMap(".json"))
    }

    /**
     * Lists all files in a Drive folder, handling pagination.
     * Returns filename → fileId map.
     */
    private fun fetchFolderFiles(folderId: String, token: String): Map<String, String> {
        val files     = mutableMapOf<String, String>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files")
                append("?q=${enc("'$folderId' in parents and trashed=false")}")
                append("&fields=${enc("nextPageToken,files(id,name)")}")
                append("&pageSize=1000")
                if (pageToken != null) append("&pageToken=${enc(pageToken!!)}")
            }
            val resp  = getJson(url, token)
            val arr   = resp.getJSONArray("files")
            pageToken = resp.optString("nextPageToken").ifEmpty { null }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                files[obj.getString("name")] = obj.getString("id")
            }
        } while (pageToken != null)
        return files
    }

    // ── Index persistence ─────────────────────────────────────────────────────

    private fun saveIndex(file: File, index: DriveIndex) {
        val xmlObj  = org.json.JSONObject(); index.xmlBr.forEach { (k, v) -> xmlObj.put(k, v) }
        val jsonObj = org.json.JSONObject(); index.json.forEach  { (k, v) -> jsonObj.put(k, v) }
        file.writeText(org.json.JSONObject()
            .apply { put("xmlBr", xmlObj); put("json", jsonObj) }
            .toString())
    }

    private fun loadIndex(file: File): DriveIndex {
        val obj     = org.json.JSONObject(file.readText())
        val xmlObj  = obj.getJSONObject("xmlBr")
        val jsonObj = obj.getJSONObject("json")
        return DriveIndex(
            xmlBr = xmlObj.keys().asSequence().associateWith { xmlObj.getString(it) },
            json  = jsonObj.keys().asSequence().associateWith { jsonObj.getString(it) },
        )
    }

    // ── File downloads ────────────────────────────────────────────────────────

    /**
     * Downloads a brotli-compressed drawable and decompresses it into [dest].
     *
     * Uses an atomic write: streams into a .tmp file, then renames to the final
     * path on success. This ensures a partial or failed download never leaves a
     * corrupt .xml file that would pass the [isFullyDownloaded] check.
     */
    private fun downloadAndDecompress(fileId: String, token: String, dest: File) {
        val tmp  = File(dest.parent, "${dest.name}.tmp")
        val conn = openConn(fileId, token)
        try {
            BrotliInputStream(conn.inputStream).use { brotli ->
                tmp.outputStream().use { out -> brotli.copyTo(out) }
            }
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(dest)
    }

    /** Downloads a small file directly (no decompression). Atomic write via .tmp. */
    private fun downloadSmall(fileId: String, token: String, dest: File) {
        val tmp  = File(dest.parent, "${dest.name}.tmp")
        val conn = openConn(fileId, token)
        try {
            conn.inputStream.use { it.copyTo(tmp.outputStream()) }
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(dest)
    }

    private fun openConn(fileId: String, token: String): HttpURLConnection =
        (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 0
            connect()
        }

    private fun getJson(url: String, token: String): org.json.JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout    = 30_000
            connect()
        }
        return org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            .also { conn.disconnect() }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Parses an Android <vector> XML file into a [Drawable].
     *
     * [VectorDrawableCompat.createFromXml] returns a compat wrapper on all API
     * levels, avoiding the ClassCastException that would occur with a direct
     * `as VectorDrawableCompat` cast on API 24+ where the framework VectorDrawable
     * is returned instead.
     */
    private fun parseDrawable(xmlFile: File): Drawable {
        val parser = android.util.Xml.newPullParser()
        xmlFile.inputStream().use { parser.setInput(it, null) }
        return VectorDrawableCompat.createFromXml(context.resources, parser)
            ?: error("Failed to parse drawable: ${xmlFile.name}")
    }

    /**
     * Parses JSON polygon data for one page.
     * Format: top-level array of { surahNumber, ayahNumber, polygon, x, y }.
     */
    private fun parseRegions(jsonFile: File): List<WarshAyaRegion> {
        val arr = JSONArray(jsonFile.readText())
        return List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            WarshAyaRegion(
                surahNum = obj.getInt("surahNumber"),
                ayahNum  = obj.getInt("ayahNumber"),
                polygon  = obj.getString("polygon"),
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Clears all cached files and resets download state. */
    fun clearCache() {
        xmlDir.listFiles()?.forEach  { it.delete() }
        jsonDir.listFiles()?.forEach { it.delete() }
        File(root, "index.json").delete()
        synchronized(pageCache) { pageCache.clear() }
        _downloadState.value = WarshDownloadState.NotDownloaded
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /**
         * Number of full mushaf pages rendered in the reader (files 001–604).
         * The Drive folder also contains surah-variant files (e.g. "106-surah4"),
         * bringing the total drawables to 722 — but only full-page files are shown.
         */
        const val PAGE_COUNT = 604

        /**
         * Viewport dimension for all Warsh drawable pages (235×235).
         * JSON polygon coordinates are in this same 0–235 space.
         * Using this constant instead of drawable.intrinsicWidth (which is
         * density-dependent) ensures correct hit-test alignment on all devices.
         */
        private const val VIEWPORT = 235f

        /** Max parallel download coroutines — balances speed vs. connection limits. */
        private const val CONCURRENCY     = 8
        private const val PAGE_CACHE_SIZE = 5

        // Google Drive folder IDs for the Warsh mushaf assets.
        private const val FOLDER_XML_BR_ID = "1fOVjmK3N-zwL1rSvLDzX7cysgQUyHS0N"
        private const val FOLDER_JSON_ID   = "12TVhLcC5NHGKXobc_DfE31TD2YbjSDq-"
    }
}