package com.lhacenmed.khatmah.feature.quran.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import androidx.core.content.edit
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

// ── Download state ────────────────────────────────────────────────────────────

sealed class WarshQcf4DownloadState {
    object NotDownloaded                        : WarshQcf4DownloadState()
    object Connecting                           : WarshQcf4DownloadState()
    data class Downloading(val progress: Float) : WarshQcf4DownloadState()
    object Downloaded                           : WarshQcf4DownloadState()
    data class Error(val message: String)       : WarshQcf4DownloadState()
}

// ── Repository ────────────────────────────────────────────────────────────────

class WarshQcf4Repository private constructor(private val ctx: Context) : Qcf4PageSource {

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

    fun downloadAll(): Flow<WarshQcf4DownloadState> = flow {
        emit(WarshQcf4DownloadState.Connecting)
        _downloadState.value = WarshQcf4DownloadState.Connecting

        fontsDir.mkdirs()

        val fontTargets   = ALL_FONT_FILES.map { "$CDN_FONTS/$it" to File(fontsDir, it) }
        val existingFonts = fontTargets.count { (_, f) -> f.exists() }
        val existingNums  = dao.existingPageNums(RIWAYA).toHashSet()
        val missingPages  = (1..PAGE_COUNT).filter { it !in existingNums }
        val total         = ALL_FONT_FILES.size + PAGE_COUNT
        val completed     = AtomicInteger(existingFonts + existingNums.size)

        val initProgress = completed.get().toFloat() / total
        emit(WarshQcf4DownloadState.Downloading(initProgress))
        _downloadState.value = WarshQcf4DownloadState.Downloading(initProgress)

        var error: WarshQcf4DownloadState.Error? = null
        val sem = Semaphore(CONCURRENCY)

        coroutineScope {
            val fontJobs = fontTargets.map { (url, dest) ->
                async(Dispatchers.IO) {
                    if (dest.exists()) return@async
                    sem.withPermit {
                        runCatching { fetch(url, dest) }.onFailure {
                            error = WarshQcf4DownloadState.Error("Failed ${dest.name}: ${it.message}")
                        }
                    }
                    val p = completed.incrementAndGet().toFloat() / total
                    _downloadState.value = WarshQcf4DownloadState.Downloading(p)
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
                            error = WarshQcf4DownloadState.Error("Failed page $pageNum: ${it.message}")
                        }
                    }
                    val p = completed.incrementAndGet().toFloat() / total
                    _downloadState.value = WarshQcf4DownloadState.Downloading(p)
                }
            }

            (fontJobs + pageJobs).awaitAll()
        }

        error?.let { emit(it); _downloadState.value = it; return@flow }

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

    private fun fetch(url: String, dest: File) {
        val tmp  = File(dest.parent, "${dest.name}.tmp")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 60_000; connect()
        }
        try { conn.inputStream.use { it.copyTo(tmp.outputStream()) } } finally { conn.disconnect() }
        tmp.renameTo(dest)
    }

    private fun fetchString(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000; readTimeout = 60_000; connect()
        }
        return try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val PAGE_COUNT = 604

        private const val RIWAYA       = "warsh_qcf4"
        private const val CONCURRENCY  = 6
        private const val PREFS_NAME   = "warsh_qcf4_prefs"
        private const val KEY_DB_READY = "db_ready"
        private const val CDN_DATA     = "https://cdn.jsdelivr.net/gh/LhacenMed/khatmah-warsh-qcf4@v1.0.0"
        private const val CDN_FONTS    = "https://cdn.jsdelivr.net/gh/LhacenMed/khatmah-warsh-qcf4@v1.0.0/fonts"

        /** 50 numbered Warsh fonts + QCF4_QBSML = 51 total. */
        val ALL_FONT_FILES: List<String> = buildList {
            (1..50).forEach { n -> add("QCF4_Warsh_%02d_W.ttf".format(n)) }
            add("QCF4_QBSML.ttf")
        }

        fun fontFileName(fontName: String): String =
            if (fontName == "QCF4_QBSML") "QCF4_QBSML.ttf" else "${fontName}_W.ttf"

        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: WarshQcf4Repository? = null

        fun get(context: Context): WarshQcf4Repository = instance ?: synchronized(this) {
            instance ?: WarshQcf4Repository(context.applicationContext).also { instance = it }
        }
    }
}