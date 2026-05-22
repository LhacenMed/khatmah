package com.lhacenmed.khatmah.feature.quran.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import com.lhacenmed.khatmah.feature.mushaf.data.db.PageEntity
import com.lhacenmed.khatmah.feature.mushaf.data.db.VersePage
import com.lhacenmed.khatmah.feature.mushaf.data.db.WordEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.content.edit

// ── Download state ────────────────────────────────────────────────────────────

sealed class HafsQcf4DownloadState {
    object NotDownloaded                              : HafsQcf4DownloadState()
    object Connecting                                 : HafsQcf4DownloadState()
    data class Downloading(val progress: Float)       : HafsQcf4DownloadState()
    object Downloaded                                 : HafsQcf4DownloadState()
    data class Error(val message: String)             : HafsQcf4DownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Manages local caching of QCF4 Hafs mushaf assets.
 *
 * All assets are downloaded once — no internet connection required after that.
 *
 * Download strategy:
 *   Fonts (48 TTF files) — downloaded to [fontsDir]; loaded into a typeface LRU on demand.
 *   Page data (604 pages) — downloaded, parsed, and stored in [MushafDb] (mushaf_word table).
 *   Verse→page index     — derived from word data; stored in mushaf_verse_page table.
 *
 * Cache layout:
 *   [context.filesDir]/hafs-qcf4/fonts/  — 48 QCF4 TTF font files
 *   [MushafDb] (mushaf.db)               — page, word, and verse-page tables
 */
class HafsQcf4Repository private constructor(private val ctx: Context) : Qcf4PageSource {

    private val fontsDir = File(ctx.filesDir, "hafs-qcf4/fonts")
    private val prefs    = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao      = MushafDb.get(ctx).dao()
    private val scope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── In-memory typeface LRU ────────────────────────────────────────────────

    private val typefaceCache = object : LinkedHashMap<String, Typeface>(52, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Typeface>) = size > 50
    }

    // ── Download state ────────────────────────────────────────────────────────

    private val _downloadState = MutableStateFlow<HafsQcf4DownloadState>(
        if (isFullyDownloaded()) HafsQcf4DownloadState.Downloaded
        else HafsQcf4DownloadState.NotDownloaded
    )
    val downloadState = _downloadState.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * True when all font files are present and the DB is fully populated.
     * Safe to call from any thread — SharedPrefs and file checks only.
     */
    fun isFullyDownloaded(): Boolean =
        fontsDir.exists() &&
                ALL_FONT_FILES.all { File(fontsDir, it).exists() } &&
                prefs.getBoolean(KEY_DB_READY, false)

    /**
     * Returns the [Typeface] for [fontName] (e.g. "QCF4_Hafs_01").
     * Loads from disk synchronously; cached in memory after first load.
     * Falls back to [Typeface.DEFAULT] if the file is missing.
     */
    override fun typefaceFor(fontName: String): Typeface = synchronized(typefaceCache) {
        typefaceCache.getOrPut(fontName) {
            val file = File(fontsDir, fontFileName(fontName))
            if (file.exists()) runCatching { Typeface.createFromFile(file) }
                .getOrDefault(Typeface.DEFAULT)
            else Typeface.DEFAULT
        }
    }

    /**
     * Returns [Qcf4Page] for [pageNum] (1-based) from the local database.
     * Throws [IllegalStateException] if the page has not been downloaded yet.
     */
    override suspend fun pageData(pageNum: Int): Qcf4Page = withContext(Dispatchers.IO) {
        require(pageNum in 1..PAGE_COUNT)
        val font  = dao.pageFont(RIWAYA, pageNum) ?: ""
        val words = dao.words(RIWAYA, pageNum)
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

    /**
     * Returns a map of packed (sura shl 32 or aya) → 0-based page index.
     * Reads from the local database; returns an empty map if not yet populated.
     */
    suspend fun ayaPageIndex(): Map<Long, Int> = withContext(Dispatchers.IO) {
        dao.versePages(RIWAYA).associate { vp ->
            (vp.sura.toLong() shl 32 or vp.aya.toLong()) to (vp.pageNum - 1)
        }
    }

    /**
     * Downloads all fonts (48 TTF) and all page JSONs (604 pages) in one shot.
     * Fonts are stored to disk; pages are parsed and inserted into [MushafDb].
     * Emits [HafsQcf4DownloadState] progress updates. Idempotent — skips existing assets.
     */
    fun downloadAll(): Flow<HafsQcf4DownloadState> = flow {
        emit(HafsQcf4DownloadState.Connecting)
        _downloadState.value = HafsQcf4DownloadState.Connecting

        fontsDir.mkdirs()

        val fontTargets    = ALL_FONT_FILES.map { "$CDN_FONTS/$it" to File(fontsDir, it) }
        val existingFonts  = fontTargets.count { (_, f) -> f.exists() }
        // Non-suspend DB call — safe on IO thread (flowOn ensures IO context).
        val existingNums   = dao.existingPageNums(RIWAYA).toHashSet()
        val missingPages   = (1..PAGE_COUNT).filter { it !in existingNums }
        val total          = ALL_FONT_FILES.size + PAGE_COUNT
        val completed      = AtomicInteger(existingFonts + existingNums.size)

        val initProgress = completed.get().toFloat() / total
        emit(HafsQcf4DownloadState.Downloading(initProgress))
        _downloadState.value = HafsQcf4DownloadState.Downloading(initProgress)

        var error: HafsQcf4DownloadState.Error? = null
        val sem = Semaphore(CONCURRENCY)

        coroutineScope {
            val fontJobs = fontTargets.map { (url, dest) ->
                async(Dispatchers.IO) {
                    if (dest.exists()) return@async
                    sem.withPermit {
                        runCatching { fetch(url, dest) }.onFailure {
                            error = HafsQcf4DownloadState.Error("Failed ${dest.name}: ${it.message}")
                        }
                    }
                    val p = completed.incrementAndGet().toFloat() / total
                    _downloadState.value = HafsQcf4DownloadState.Downloading(p)
                }
            }

            val pageJobs = missingPages.map { pageNum ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        runCatching {
                            val json = fetchString("$CDN_DATA/pages/%03d.json".format(pageNum))
                            val page = JSONObject(json).toQcf4Page()
                            dao.insertPageWithWords(
                                PageEntity(RIWAYA, page.page, page.font),
                                buildWordEntities(page),
                            )
                        }.onFailure {
                            error = HafsQcf4DownloadState.Error("Failed page $pageNum: ${it.message}")
                        }
                    }
                    val p = completed.incrementAndGet().toFloat() / total
                    _downloadState.value = HafsQcf4DownloadState.Downloading(p)
                }
            }

            (fontJobs + pageJobs).awaitAll()
        }

        error?.let { emit(it); _downloadState.value = it; return@flow }

        // Rebuild verse→page index from all words now in DB.
        // Handles resumed downloads correctly by considering all pages, not just new ones.
        rebuildVersePages()

        prefs.edit { putBoolean(KEY_DB_READY, true) }
        emit(HafsQcf4DownloadState.Downloaded)
        _downloadState.value = HafsQcf4DownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    /** Deletes font files and clears all DB entries for this riwaya. */
    fun clearCache() {
        fontsDir.listFiles()?.forEach { it.delete() }
        synchronized(typefaceCache) { typefaceCache.clear() }
        prefs.edit { putBoolean(KEY_DB_READY, false)}
        _downloadState.value = HafsQcf4DownloadState.NotDownloaded
        scope.launch { dao.clearRiwaya(RIWAYA) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derives the aya→page index from all words in DB and inserts into mushaf_verse_page.
     * Uses MIN(page_num) per aya so long ayas that span pages map to their first page.
     */
    private suspend fun rebuildVersePages() {
        val rows     = dao.verseKeyRows(RIWAYA)
        val verseMap = HashMap<Long, Int>(6400)
        for (row in rows) {
            val aya = row.verseKey.substringAfter(':').toIntOrNull() ?: continue
            val key = row.sura.toLong() shl 32 or aya.toLong()
            verseMap.merge(key, row.pageNum) { old, new -> minOf(old, new) }
        }
        val pages = verseMap.entries.map { (key, pageNum) ->
            VersePage(
                riwaya  = RIWAYA,
                sura    = (key ushr 32).toInt(),
                aya     = (key and 0xFFFFFFFFL).toInt(),
                pageNum = pageNum,
            )
        }
        dao.clearVersePages(RIWAYA)
        if (pages.isNotEmpty()) dao.insertVersePages(pages)
    }

    /** Maps a [Qcf4Page] to a flat list of [WordEntity] rows ready for DB insertion. */
    private fun buildWordEntities(page: Qcf4Page): List<WordEntity> {
        val entities = ArrayList<WordEntity>(page.lines.sumOf { it.words.size })
        page.lines.forEachIndexed { lineIdx, line ->
            line.words.forEachIndexed { wordIdx, word ->
                entities += WordEntity(
                    riwaya   = RIWAYA,
                    pageNum  = page.page,
                    lineIdx  = lineIdx,
                    lineNum  = line.line,
                    wordIdx  = wordIdx,
                    char     = word.char,
                    font     = word.font,
                    text     = word.text,
                    type     = word.type,
                    verseKey = word.verseKey,
                    sura     = word.sura ?: word.verseKey?.substringBefore(':')?.toIntOrNull(),
                    position = word.position,
                )
            }
        }
        return entities
    }

    /** Atomic binary file write: stream to .tmp then rename on success. */
    private fun fetch(url: String, dest: File) {
        val tmp  = File(dest.parent, "${dest.name}.tmp")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout    = 60_000
            connect()
        }
        try {
            conn.inputStream.use { it.copyTo(tmp.outputStream()) }
        } finally {
            conn.disconnect()
        }
        tmp.renameTo(dest)
    }

    /** Downloads a URL and returns the response body as a UTF-8 string. */
    private fun fetchString(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout    = 60_000
            connect()
        }
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val PAGE_COUNT = 604

        private const val RIWAYA       = "hafs"
        private const val CONCURRENCY  = 6
        private const val PREFS_NAME   = "hafs_qcf4_prefs"
        private const val KEY_DB_READY = "db_ready"
        private const val CDN_FONTS = "https://cdn.jsdelivr.net/gh/LhacenMed/khatmah-hafs-qcf4@main/fonts"
        private const val CDN_DATA  = "https://cdn.jsdelivr.net/gh/LhacenMed/khatmah-hafs-qcf4@main"

        /** 47 numbered Hafs fonts + QCF4_QBSML + QCF2_QBSML (surah container) = 49 total. */
        val ALL_FONT_FILES: List<String> = buildList {
            (1..47).forEach { n -> add("QCF4_Hafs_%02d_W.ttf".format(n)) }
            add("QCF4_QBSML.ttf")
            add("QCF2_QBSML.ttf")
        }

        /**
         * Maps a JSON font name to its TTF file name.
         * QCF4_QBSML and QCF2_QBSML have no _W suffix.
         */
        fun fontFileName(fontName: String): String = when (fontName) {
            "QCF4_QBSML", "QCF2_QBSML" -> "$fontName.ttf"
            else                         -> "${fontName}_W.ttf"
        }

        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: HafsQcf4Repository? = null

        fun get(context: Context): HafsQcf4Repository = instance ?: synchronized(this) {
            instance ?: HafsQcf4Repository(context.applicationContext).also { instance = it }
        }
    }
}