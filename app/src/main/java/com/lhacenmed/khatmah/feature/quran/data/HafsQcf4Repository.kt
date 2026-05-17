package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import android.graphics.Typeface
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
 * Download strategy:
 *   Fonts (48 TTF files) — downloaded upfront via [downloadAll].
 *   Page JSON (604 files) — fetched on demand from CDN, cached locally.
 *   verses.json           — downloaded upfront; drives aya→page index.
 *
 * Cache layout (under context.filesDir/hafs-qcf4/):
 *   fonts/     — QCF4_Hafs_01_W.ttf … QCF4_Hafs_47_W.ttf + QCF4_QBSML.ttf
 *   pages/     — 001.json … 604.json (fetched on demand)
 *   verses.json
 */
class HafsQcf4Repository private constructor(private val ctx: Context) {

    private val root     = File(ctx.filesDir, "hafs-qcf4")
    private val fontsDir = File(root, "fonts")
    private val pagesDir = File(root, "pages")

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

    /** True when all font files and verses.json are present on disk. */
    fun isFullyDownloaded(): Boolean {
        if (!fontsDir.exists()) return false
        return ALL_FONT_FILES.all { File(fontsDir, it).exists() } &&
                File(root, "verses.json").exists()
    }

    /**
     * Returns the [Typeface] for [fontName] (e.g. "QCF4_Hafs_01").
     * Loads from disk synchronously; cached in memory after first load.
     * Falls back to [Typeface.DEFAULT] if the file is missing.
     */
    fun typefaceFor(fontName: String): Typeface = synchronized(typefaceCache) {
        typefaceCache.getOrPut(fontName) {
            val file = File(fontsDir, fontFileName(fontName))
            if (file.exists()) runCatching { Typeface.createFromFile(file) }
                .getOrDefault(Typeface.DEFAULT)
            else Typeface.DEFAULT
        }
    }

    /**
     * Returns [Qcf4Page] for [pageNum] (1-based).
     * Reads from local cache; fetches from CDN on first access.
     */
    suspend fun pageData(pageNum: Int): Qcf4Page = withContext(Dispatchers.IO) {
        require(pageNum in 1..PAGE_COUNT)
        val file = File(pagesDir, "%03d.json".format(pageNum))
        if (!file.exists()) {
            pagesDir.mkdirs()
            fetch("$CDN_DATA/pages/%03d.json".format(pageNum), file)
        }
        JSONObject(file.readText()).toQcf4Page()
    }

    /**
     * Parses verses.json and returns a map of packed (sura shl 32 or aya) → 0-based page index.
     * Returns empty map if verses.json is not yet downloaded.
     */
    suspend fun ayaPageIndex(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val file = File(root, "verses.json")
        if (!file.exists()) return@withContext emptyMap()
        val map = HashMap<Long, Int>(6300)
        val obj = JSONObject(file.readText())
        for (key in obj.keys()) {
            val parts = key.split(":")
            if (parts.size != 2) continue
            val sura = parts[0].toIntOrNull() ?: continue
            val aya  = parts[1].toIntOrNull() ?: continue
            val page = obj.getJSONObject(key).optInt("page", -1)
            if (page > 0) map[sura.toLong() shl 32 or aya.toLong()] = page - 1  // 0-based index
        }
        map
    }

    /**
     * Downloads all fonts + verses.json.
     * Emits [HafsQcf4DownloadState] updates and mirrors them to [downloadState].
     * Idempotent — skips files already on disk.
     */
    fun downloadAll(): Flow<HafsQcf4DownloadState> = flow {
        emit(HafsQcf4DownloadState.Connecting)
        _downloadState.value = HafsQcf4DownloadState.Connecting

        fontsDir.mkdirs()
        root.mkdirs()

        // Fonts + verses.json
        val targets: List<Pair<String, File>> = ALL_FONT_FILES.map { name ->
            "$CDN_FONTS/$name" to File(fontsDir, name)
        } + ("$CDN_DATA/verses.json" to File(root, "verses.json"))

        val total       = targets.size
        val preExisting = targets.count { (_, dest) -> dest.exists() }
        val completed   = AtomicInteger(preExisting)

        fun progress() = (completed.get().toFloat() / total).coerceIn(0f, 1f)

        val initState = HafsQcf4DownloadState.Downloading(progress())
        emit(initState); _downloadState.value = initState

        var encounteredError: HafsQcf4DownloadState.Error? = null
        val semaphore = Semaphore(CONCURRENCY)

        coroutineScope {
            targets.map { (url, dest) ->
                async(Dispatchers.IO) {
                    if (dest.exists()) return@async
                    semaphore.withPermit {
                        runCatching { fetch(url, dest) }.onFailure {
                            encounteredError = HafsQcf4DownloadState.Error(
                                "Failed (${dest.name}): ${it.message}"
                            )
                        }
                    }
                    completed.incrementAndGet()
                    _downloadState.value = HafsQcf4DownloadState.Downloading(progress())
                }
            }.awaitAll()
        }

        encounteredError?.let {
            emit(it); _downloadState.value = it; return@flow
        }

        emit(HafsQcf4DownloadState.Downloaded)
        _downloadState.value = HafsQcf4DownloadState.Downloaded
    }.flowOn(Dispatchers.IO)

    fun clearCache() {
        fontsDir.listFiles()?.forEach { it.delete() }
        pagesDir.listFiles()?.forEach { it.delete() }
        File(root, "verses.json").delete()
        synchronized(typefaceCache) { typefaceCache.clear() }
        _downloadState.value = HafsQcf4DownloadState.NotDownloaded
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Atomic write: stream to .tmp, then rename to dest on success. */
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

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val PAGE_COUNT = 604

        private const val CONCURRENCY = 6
        private const val CDN_FONTS = "https://cdn.jsdelivr.net/gh/MohamadHajjRabee/quran-qcf4@main/fonts"
        private const val CDN_DATA  = "https://raw.githubusercontent.com/MohamadHajjRabee/quran-qcf4/main"

        /** All 48 font file names as they appear on disk and CDN. */
        val ALL_FONT_FILES: List<String> = buildList {
            (1..47).forEach { n -> add("QCF4_Hafs_%02d_W.ttf".format(n)) }
            add("QCF4_QBSML.ttf")
        }

        /**
         * Maps a JSON font name (e.g. "QCF4_Hafs_01") to its TTF file name.
         * QCF4_QBSML has no _W suffix; all Hafs fonts do.
         */
        fun fontFileName(fontName: String): String =
            if (fontName == "QCF4_QBSML") "QCF4_QBSML.ttf" else "${fontName}_W.ttf"

        @Volatile private var instance: HafsQcf4Repository? = null

        fun get(context: Context): HafsQcf4Repository = instance ?: synchronized(this) {
            instance ?: HafsQcf4Repository(context.applicationContext).also { instance = it }
        }
    }
}