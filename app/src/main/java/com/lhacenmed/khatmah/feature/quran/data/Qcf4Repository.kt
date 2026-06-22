package com.lhacenmed.khatmah.feature.quran.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The read side of a riwaya's downloaded QCF4 assets, driven by its [RiwayaConfig]: serves
 * typefaces and queries page/verse data from [MushafDb]. Installing those assets is
 * [com.lhacenmed.khatmah.feature.quran.data.download.MushafDownloader]'s job; download progress
 * lives in [com.lhacenmed.khatmah.feature.quran.data.download.DownloadRegistry]. This class never
 * touches the network — once the assets are present it works fully offline.
 */
class Qcf4Repository private constructor(
    private val ctx: Context,
    val config: RiwayaConfig,
) {
    val riwaya: Riwaya get() = config.riwaya

    private val fontsDir = config.fontsDir(ctx)
    private val dao = MushafDb.get(ctx).dao()

    // ── In-memory typeface LRU (sized to the riwaya's font count + headroom) ─────

    private val cacheCap = config.allFontFiles.size + 2
    private val typefaceCache = object : LinkedHashMap<String, Typeface>(cacheCap, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Typeface>) = size > cacheCap
    }

    /** True when all font files are present and the DB is fully populated. */
    fun isFullyDownloaded(): Boolean = config.isDownloaded(ctx)

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

    /** Drops cached typefaces so a fresh download's fonts are picked up. */
    fun invalidateTypefaces() = synchronized(typefaceCache) { typefaceCache.clear() }

    /**
     * Returns [Qcf4Page] for [pageNum] (1-based) from the local database.
     * Throws if the page has not been downloaded yet.
     */
    suspend fun pageData(pageNum: Int): Qcf4Page = withContext(Dispatchers.IO) {
        require(pageNum in 1..RiwayaConfig.PAGE_COUNT)
        val font = dao.pageFont(config.wordKey, pageNum) ?: ""
        val words = dao.words(config.wordKey, pageNum)
        check(words.isNotEmpty()) { "Page $pageNum not in database — download required" }

        val lines = words
            .groupBy { it.lineIdx }
            .entries
            .sortedBy { it.key }
            .map { (_, ws) ->
                Qcf4Line(
                    line = ws.first().lineNum,
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

    companion object {
        @SuppressLint("StaticFieldLeak")
        private val instances = HashMap<Riwaya, Qcf4Repository>()

        /** Per-riwaya singleton, so the typeface cache is shared app-wide. */
        fun get(context: Context, riwaya: Riwaya): Qcf4Repository = synchronized(instances) {
            instances.getOrPut(riwaya) {
                Qcf4Repository(context.applicationContext, RiwayaConfig.of(riwaya))
            }
        }
    }
}
