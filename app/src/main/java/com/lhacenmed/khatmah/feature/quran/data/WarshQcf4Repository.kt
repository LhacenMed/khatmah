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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ── Download state ────────────────────────────────────────────────────────────

sealed class WarshQcf4DownloadState {
    object NotDownloaded                                                  : WarshQcf4DownloadState()
    object Connecting                                                     : WarshQcf4DownloadState()
    data class Downloading(val progress: Float?, val log: String = "")    : WarshQcf4DownloadState()
    object Downloaded                                                     : WarshQcf4DownloadState()
    data class Error(val message: String)                                 : WarshQcf4DownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

class WarshQcf4Repository private constructor(private val ctx: Context) : Qcf4PageSource {

    override val riwaya = Riwaya.WARSH

    private val fontsDir = File(ctx.filesDir, "warsh-qcf4/fonts")
    private val prefs    = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao      = MushafDb.get(ctx).dao()
    private val scope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val typefaceCache = object : LinkedHashMap<String, Typeface>(54, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Typeface>) = size > 52
    }

    private val _downloadState = MutableStateFlow<WarshQcf4DownloadState>(
        if (isFullyDownloaded()) WarshQcf4DownloadState.Downloaded
        else WarshQcf4DownloadState.NotDownloaded
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

    fun isFullyDownloaded(): Boolean =
        fontsDir.exists() &&
                ALL_FONT_FILES.all { File(fontsDir, it).exists() } &&
                prefs.getBoolean(KEY_DB_READY, false)

    override fun typefaceFor(fontName: String): Typeface = synchronized(typefaceCache) {
        typefaceCache.getOrPut(fontName) {
            val file = File(fontsDir, fontFileName(fontName))
            if (file.exists()) runCatching { Typeface.createFromFile(file) }
                .getOrDefault(Typeface.DEFAULT)
            else Typeface.DEFAULT
        }
    }

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

    suspend fun ayaPageIndex(): Map<Long, Int> = withContext(Dispatchers.IO) {
        dao.versePages(RIWAYA).associate { vp ->
            (vp.sura.toLong() shl 32 or vp.aya.toLong()) to (vp.pageNum - 1)
        }
    }

    /**
     * Downloads [BUNDLE_URL] (a single .7z containing fonts/ and pages/) to a temp file,
     * then extracts in one streaming pass: TTF fonts are written to [fontsDir]; page JSONs
     * are parsed and inserted into [MushafDb] inline. Progress is 0→1 based on bytes received.
     * Idempotent — skips font files already on disk.
     */
    fun downloadAll(): Flow<WarshQcf4DownloadState> = flow {
        emit(WarshQcf4DownloadState.Connecting)
        _downloadState.value = WarshQcf4DownloadState.Connecting

        fontsDir.mkdirs()
        val tmpBundle = File(ctx.cacheDir, "warsh_qcf4_bundle.7z")
        tmpBundle.delete()

        // ── Phase 1: Stream bundle to disk with byte-level progress ───────────
        try {
            val conn       = openWithRedirects(BUNDLE_URL)
            val totalBytes = conn.contentLengthLong.takeIf { it > 0 }
            var received   = 0L
            try {
                conn.inputStream.use { input ->
                    tmpBundle.outputStream().use { out ->
                        val buf = ByteArray(65_536)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            received += n
                            _downloadState.value = WarshQcf4DownloadState.Downloading(
                                progress = totalBytes?.let { (received.toFloat() / it).coerceIn(0f, 0.99f) },
                                log      = "Downloading files..."
                            )
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            tmpBundle.delete()
            val err = WarshQcf4DownloadState.Error("Download failed: ${e.message}")
            emit(err); _downloadState.value = err; return@flow
        }

        // ── Phase 2: Extract fonts to disk; parse and insert page JSONs ────────
        // try/finally (not use{}) keeps us in the flow's coroutine scope so
        // suspend calls like dao.insertPageWithWords() are valid inline.
        var sz: SevenZFile? = null
        var pagesDone = 0
        try {
            _downloadState.value = WarshQcf4DownloadState.Downloading(null, "Extracting fonts...")
            sz = SevenZFile(tmpBundle)
            var entry = sz.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    when {
                        name.startsWith("fonts/") && name.endsWith(".ttf") -> {
                            val dest = File(fontsDir, name.removePrefix("fonts/"))
                            if (!dest.exists()) {
                                val tmp = File(fontsDir, "${dest.name}.tmp")
                                tmp.outputStream().use { out ->
                                    val buf = ByteArray(8_192); var n: Int
                                    while (sz.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                                }
                                tmp.renameTo(dest)
                            }
                            // existing fonts: nextEntry() below advances past this entry
                        }
                        name.startsWith("pages/") && name.endsWith(".json") -> {
                            val baos = ByteArrayOutputStream()
                            val buf  = ByteArray(8_192); var n: Int
                            while (sz.read(buf).also { n = it } != -1) baos.write(buf, 0, n)
                            runCatching {
                                val page = JSONObject(baos.toString("UTF-8")).toQcf4Page()
                                dao.insertPageWithWords(
                                    PageEntity(RIWAYA, page.page, page.font),
                                    buildWordEntities(page),
                                )
                            }
                            pagesDone++
                            if (pagesDone % 60 == 0 || pagesDone == PAGE_COUNT) {
                                _downloadState.value = WarshQcf4DownloadState.Downloading(
                                    null, "Importing pages: $pagesDone / $PAGE_COUNT"
                                )
                            }
                        }
                    }
                }
                entry = sz.nextEntry
            }
        } catch (e: Exception) {
            val err = WarshQcf4DownloadState.Error("Extract failed: ${e.message}")
            emit(err); _downloadState.value = err
            return@flow
        } finally {
            runCatching { sz?.close() }
            runCatching { tmpBundle.delete() }
        }

        // Rebuild verse→page index from all words now in DB.
        _downloadState.value = WarshQcf4DownloadState.Downloading(null, "Building verse index...")
        rebuildVersePages()
        prefs.edit { putBoolean(KEY_DB_READY, true) }
        emit(WarshQcf4DownloadState.Downloaded)
        _downloadState.value = WarshQcf4DownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    fun clearCache() {
        fontsDir.listFiles()?.forEach { it.delete() }
        synchronized(typefaceCache) { typefaceCache.clear() }
        prefs.edit { putBoolean(KEY_DB_READY, false) }
        _downloadState.value = WarshQcf4DownloadState.NotDownloaded
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
                // Derive sura from verse_key when the JSON omits the sura field.
                val sura = word.sura ?: word.verseKey?.substringBefore(':')?.toIntOrNull()
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
                    sura     = sura,
                    position = word.position,
                )
            }
        }
        return entities
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val PAGE_COUNT = 604

        private const val RIWAYA       = "warsh_qcf4"
        private const val PREFS_NAME   = "warsh_qcf4_prefs"
        private const val KEY_DB_READY = "db_ready"
        private const val CDN_DATA     = "https://cdn.jsdelivr.net/gh/LhacenMed/khatmah-warsh-qcf4@main"
        private const val BUNDLE_URL =
            "https://raw.githubusercontent.com/LhacenMed/khatmah-warsh-qcf4/main/warsh.7z"

        /** 50 numbered Warsh fonts + QCF4_QBSML + QCF2_QBSML (surah container) = 52 total. */
        val ALL_FONT_FILES: List<String> = buildList {
            (1..50).forEach { n -> add("QCF4_Warsh_%02d_W.ttf".format(n)) }
            add("QCF4_QBSML.ttf")
            add("QCF2_QBSML.ttf")
        }

        /**
         * Maps a JSON font name to its TTF file name.
         * QCF4_QBSML and QCF2_QBSML have no _W suffix; all Warsh glyph fonts do.
         */
        fun fontFileName(fontName: String): String = when (fontName) {
            "QCF4_QBSML", "QCF2_QBSML" -> "$fontName.ttf"
            else                         -> "${fontName}_W.ttf"
        }

        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: WarshQcf4Repository? = null

        fun get(context: Context): WarshQcf4Repository = instance ?: synchronized(this) {
            instance ?: WarshQcf4Repository(context.applicationContext).also { instance = it }
        }
    }
}