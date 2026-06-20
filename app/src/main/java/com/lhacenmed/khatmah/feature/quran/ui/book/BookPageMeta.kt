package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.Context
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb

private val EAST_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/** Converts an integer to Eastern-Arabic-Indic numerals. */
private fun east(n: Int): String = n.toString().map { EAST_DIGITS[it - '0'] }.joinToString("")

/**
 * Toolbar + page-info text for a page.
 *
 * The page's representative sura is the one of the **first** ayah on the page (per mushaf
 * convention, the header names the sura at the top of the page), and the juz is that ayah's
 * juz. Exposes both the toolbar strings and the overlay pieces so the activity (toolbar) and
 * the page view (header/footer overlay) share one source.
 */
data class BookMeta(val suraName: String, val juz: Int, val page: Int) {
    val toolbarTitle: String get() = "سُورَةُ $suraName"
    val toolbarSubtitle: String get() = "صفحة ${east(page)}، جزء ${east(juz)}"
    val headerSura: String get() = suraName
    val headerJuz: String get() = "جزء ${east(juz)}"
    val footerPage: String get() = east(page)

    // Bottom page-jump slider popup: "page N" line + "sura - juz J" line.
    val sliderPage: String get() = "صفحة ${east(page)}"
    val sliderSuraJuz: String get() = "$suraName - الجزء ${east(juz)}"
}

object BookPageMeta {

    // Canonical juz' boundaries as (sura, ayah) start positions — the same fixed table
    // Quran Android encodes, independent of pagination so it holds for any riwaya.
    private val JUZ_SURA = intArrayOf(
        1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 11, 12, 15, 17,
        18, 21, 23, 25, 27, 29, 33, 36, 39, 41, 46, 51, 58, 67, 78,
    )
    private val JUZ_AYA = intArrayOf(
        1, 142, 253, 93, 24, 148, 82, 111, 88, 41, 93, 6, 53, 1, 1,
        75, 1, 1, 21, 56, 46, 31, 28, 32, 47, 1, 31, 1, 1, 1,
    )

    // Precomputed metadata cache for all pages. Instantly loaded and resolved per riwaya.
    @Volatile
    private var metaCache: Pair<String, Map<Int, BookMeta>>? = null

    /**
     * Loads the fully resolved metadata for all pages for the given [riwayaKey].
     * Driven directly by page start anchors from the DB, skipping word parsing entirely.
     */
    suspend fun loadMetaForRiwaya(context: Context, riwayaKey: String): Map<Int, BookMeta> {
        metaCache?.let { (key, map) -> if (key == riwayaKey) return map }
        
        val ctx = context.applicationContext
        val dao = MushafDb.get(ctx).dao()
        val suras = dao.surahs(riwayaKey).associate { it.num to it.name }
        val starts = dao.allPageStarts(riwayaKey)
        
        val map = starts.associate { start ->
            val s = start.sura
            val a = start.aya
            val bare = (suras[s] ?: "").removePrefix("سورة ").trim()
            start.pageNum to BookMeta(bare, juzForVerse(s, a), start.pageNum)
        }
        
        metaCache = riwayaKey to map
        return map
    }

    private fun juzForVerse(sura: Int, aya: Int): Int {
        var juz = 1
        for (i in JUZ_SURA.indices) {
            val starts = JUZ_SURA[i] < sura || (JUZ_SURA[i] == sura && JUZ_AYA[i] <= aya)
            if (starts) juz = i + 1 else break
        }
        return juz
    }

    /** "صفحة N" for the slider popup before a page's full meta has loaded. */
    fun pageLabel(page: Int): String = "صفحة ${east(page)}"
}
