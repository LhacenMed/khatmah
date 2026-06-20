package com.lhacenmed.khatmah.feature.quran.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.edit
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import com.lhacenmed.khatmah.feature.mushaf.data.db.PageEntity
import com.lhacenmed.khatmah.feature.mushaf.data.db.VersePage
import com.lhacenmed.khatmah.feature.mushaf.data.db.WordEntity
import com.lhacenmed.khatmah.shared.util.SpeedTracker
import com.lhacenmed.khatmah.shared.util.formatDownloadLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One repository for every QCF4 riwaya, driven by a [RiwayaConfig]. Replaces the former
 * per-riwaya Hafs/Warsh classes: their bodies were ~95% identical, so all that differed
 * (fonts, bundle URL, db partition, prefs) now comes from the config. Adding Qaloon is a
 * [RiwayaConfig] row — this class doesn't change.
 *
 * Assets download once as a single .7z bundle ([RiwayaConfig.bundleUrl]):
 *   fonts/ — TTF glyph files written to [fontsDir];
 *   pages/ — JSON parsed into [MushafDb] (mushaf_page / mushaf_word), then a verse→page index.
 * After that the reader works fully offline.
 */
class Qcf4Repository private constructor(
    private val ctx: Context,
    val config: RiwayaConfig,
) {
    val riwaya: Riwaya get() = config.riwaya

    private val fontsDir = File(ctx.filesDir, "${config.wordKey}-qcf4/fonts")
    private val prefs    = ctx.getSharedPreferences(config.prefsName, Context.MODE_PRIVATE)
    private val dao      = MushafDb.get(ctx).dao()
    private val scope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── In-memory typeface LRU (sized to the riwaya's font count + headroom) ─────

    private val cacheCap = config.allFontFiles.size + 2
    private val typefaceCache = object : LinkedHashMap<String, Typeface>(cacheCap, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Typeface>) = size > cacheCap
    }

    // ── Download state ──────────────────────────────────────────────────────────

    private val _downloadState = MutableStateFlow<Qcf4DownloadState>(
        if (isFullyDownloaded()) Qcf4DownloadState.Downloaded
        else Qcf4DownloadState.NotDownloaded
    )
    val downloadState = _downloadState.asStateFlow()

    /**
     * Opens a connection to [url], manually following cross-host redirects
     * (Android's HttpURLConnection only auto-follows same-host redirects).
     */
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

    // ── Public API ────────────────────────────────────────────────────────────

    /** True when all font files are present and the DB is fully populated. */
    fun isFullyDownloaded(): Boolean =
        fontsDir.exists() &&
                config.allFontFiles.all { File(fontsDir, it).exists() } &&
                prefs.getBoolean(KEY_DB_READY, false)

    /**
     * Returns the [Typeface] for [fontName] (e.g. "QCF4_Hafs_01"). Loaded from disk on first
     * use and cached; falls back to [Typeface.DEFAULT] when the file is missing.
     */
    fun typefaceFor(fontName: String): Typeface = synchronized(typefaceCache) {
        typefaceCache.getOrPut(fontName) {
            val file = File(fontsDir, config.fontFileName(fontName))
            if (file.exists()) runCatching { Typeface.createFromFile(file) }
                .getOrDefault(Typeface.DEFAULT)
            else Typeface.DEFAULT
        }
    }

    /** Resets download state to [NotDownloaded] without touching disk or DB. */
    fun resetState() {
        _downloadState.value = Qcf4DownloadState.NotDownloaded
    }

    /**
     * Returns [Qcf4Page] for [pageNum] (1-based) from the local database.
     * Throws if the page has not been downloaded yet.
     */
    suspend fun pageData(pageNum: Int): Qcf4Page = withContext(Dispatchers.IO) {
        require(pageNum in 1..RiwayaConfig.PAGE_COUNT)
        val font  = dao.pageFont(config.wordKey, pageNum) ?: ""
        val words = dao.words(config.wordKey, pageNum)
        check(words.isNotEmpty()) { "Page $pageNum not in database — download required" }

        val lines = words
            .groupBy { it.lineIdx }
            .entries
            .sortedBy { it.key }
            .map { (_, ws) ->
                Qcf4Line(
                    line  = ws.first().lineNum,
                    words = ws.map { w ->
                        Qcf4Word(w.char, w.font, w.text, w.type, w.verseKey, w.sura, w.position)
                    },
                )
            }
        Qcf4Page(pageNum, font, lines)
    }

    /** Packed (sura shl 32 or aya) → 0-based page index for this riwaya's QCF4 pagination. */
    suspend fun ayaPageIndex(): Map<Long, Int> = withContext(Dispatchers.IO) {
        dao.versePages(config.wordKey).associate { vp ->
            (vp.sura.toLong() shl 32 or vp.aya.toLong()) to (vp.pageNum - 1)
        }
    }

    /**
     * Downloads [RiwayaConfig.bundleUrl] fresh every time (no resume). Clears any previous
     * partial data first so the DB and font files are always consistent.
     */
    fun downloadAll(): Flow<Qcf4DownloadState> = flow {
        emit(Qcf4DownloadState.Connecting)
        _downloadState.value = Qcf4DownloadState.Connecting

        // Fresh start — discard any previous partial data before writing new.
        prefs.edit { putBoolean(KEY_DB_READY, false) }
        synchronized(typefaceCache) { typefaceCache.clear() }
        fontsDir.deleteRecursively()
        fontsDir.mkdirs()
        dao.clearPages(config.wordKey)
        dao.clearWords(config.wordKey)
        dao.clearVersePages(config.wordKey)

        val tmpBundle = File(ctx.cacheDir, "${config.wordKey}_qcf4_bundle.7z")
        tmpBundle.delete()

        // ── Phase 1: Stream bundle to disk with byte-level progress ───────────
        val speedTracker = SpeedTracker()
        try {
            val conn       = openWithRedirects(config.bundleUrl)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            var received   = 0L
            try {
                conn.inputStream.use { input ->
                    tmpBundle.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            yield()
                            out.write(buf, 0, n)
                            received += n
                            speedTracker.add(n.toLong())
                            _downloadState.value = Qcf4DownloadState.Downloading(
                                progress = totalBytes?.let { (received.toFloat() / it).coerceIn(0f, 0.99f) },
                                log      = formatDownloadLog(speedTracker.bytesPerSec(), received, totalBytes)
                            )
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            tmpBundle.delete()
            val err = Qcf4DownloadState.Error("Download failed: ${e.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        // ── Phase 2: Extract fonts to disk; parse and insert page JSONs ────────
        var sz: SevenZFile? = null
        var pagesDone = 0
        try {
            _downloadState.value = Qcf4DownloadState.Downloading(null, "Extracting fonts...")
            sz = SevenZFile(tmpBundle)
            var entry = sz.nextEntry
            while (entry != null) {
                yield()
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    when {
                        name.startsWith("fonts/") && name.endsWith(".ttf") -> {
                            val dest = File(fontsDir, name.removePrefix("fonts/"))
                            val tmp  = File(fontsDir, "${dest.name}.tmp")
                            tmp.outputStream().use { out ->
                                val buf = ByteArray(8_192); var n: Int
                                while (sz.read(buf).also { n = it } != -1) {
                                    yield()
                                    out.write(buf, 0, n)
                                }
                            }
                            tmp.renameTo(dest)
                        }
                        name.startsWith("pages/") && name.endsWith(".json") -> {
                            val baos = ByteArrayOutputStream()
                            val buf  = ByteArray(8_192); var n: Int
                            while (sz.read(buf).also { n = it } != -1) {
                                yield()
                                baos.write(buf, 0, n)
                            }
                            runCatching {
                                val page = JSONObject(baos.toString("UTF-8")).toQcf4Page()
                                dao.insertPageWithWords(
                                    PageEntity(config.wordKey, page.page, page.font),
                                    buildWordEntities(page),
                                )
                            }
                            pagesDone++
                            if (pagesDone % 60 == 0 || pagesDone == RiwayaConfig.PAGE_COUNT) {
                                if (currentCoroutineContext().isActive) {
                                    _downloadState.value = Qcf4DownloadState.Downloading(
                                        null, "Importing pages: $pagesDone / ${RiwayaConfig.PAGE_COUNT}"
                                    )
                                }
                            }
                        }
                    }
                }
                entry = sz.nextEntry
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = Qcf4DownloadState.Error("Extract failed: ${e.message}")
            emit(err); _downloadState.value = err
            return@flow
        } finally {
            runCatching { sz?.close() }
            runCatching { tmpBundle.delete() }
        }

        // Rebuild verse→page index from all words now in DB.
        _downloadState.value = Qcf4DownloadState.Downloading(null, "Building verse index...")
        rebuildVersePages()
        prefs.edit { putBoolean(KEY_DB_READY, true) }
        emit(Qcf4DownloadState.Downloaded)
        _downloadState.value = Qcf4DownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    /** Deletes font files and clears all QCF4 DB entries for this riwaya. */
    fun clearCache() {
        fontsDir.listFiles()?.forEach { it.delete() }
        synchronized(typefaceCache) { typefaceCache.clear() }
        prefs.edit { putBoolean(KEY_DB_READY, false) }
        _downloadState.value = Qcf4DownloadState.NotDownloaded
        scope.launch {
            dao.clearPages(config.wordKey)
            dao.clearWords(config.wordKey)
            dao.clearVersePages(config.wordKey)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derives the aya→page index from all words in DB and inserts into mushaf_verse_page.
     * Uses MIN(page_num) per aya so long ayas that span pages map to their first page.
     */
    private suspend fun rebuildVersePages() {
        val rows     = dao.verseKeyRows(config.wordKey)
        val verseMap = HashMap<Long, Int>(6400)
        for (row in rows) {
            val aya = row.verseKey.substringAfter(':').toIntOrNull() ?: continue
            val key = row.sura.toLong() shl 32 or aya.toLong()
            verseMap.merge(key, row.pageNum) { old, new -> minOf(old, new) }
        }
        val pages = verseMap.entries.map { (key, pageNum) ->
            VersePage(
                riwaya  = config.wordKey,
                sura    = (key ushr 32).toInt(),
                aya     = (key and 0xFFFFFFFFL).toInt(),
                pageNum = pageNum,
            )
        }
        dao.clearVersePages(config.wordKey)
        if (pages.isNotEmpty()) dao.insertVersePages(pages)
    }

    /** Maps a [Qcf4Page] to a flat list of [WordEntity] rows ready for DB insertion. */
    private fun buildWordEntities(page: Qcf4Page): List<WordEntity> {
        val entities = ArrayList<WordEntity>(page.lines.sumOf { it.words.size })
        page.lines.forEachIndexed { lineIdx, line ->
            line.words.forEachIndexed { wordIdx, word ->
                entities += WordEntity(
                    riwaya   = config.wordKey,
                    pageNum  = page.page,
                    lineIdx  = lineIdx,
                    lineNum  = line.line,
                    wordIdx  = wordIdx,
                    char     = word.char,
                    font     = word.font,
                    text     = word.text,
                    type     = word.type,
                    verseKey = word.verseKey,
                    // Derive sura from verse_key when the JSON omits the sura field.
                    sura     = word.sura ?: word.verseKey?.substringBefore(':')?.toIntOrNull(),
                    position = word.position,
                )
            }
        }
        return entities
    }

    companion object {
        private const val KEY_DB_READY = "db_ready"

        @SuppressLint("StaticFieldLeak")
        private val instances = HashMap<Riwaya, Qcf4Repository>()

        /** Per-riwaya singleton, so download state and caches are shared app-wide. */
        fun get(context: Context, riwaya: Riwaya): Qcf4Repository = synchronized(instances) {
            instances.getOrPut(riwaya) {
                Qcf4Repository(context.applicationContext, RiwayaConfig.of(riwaya))
            }
        }
    }
}
