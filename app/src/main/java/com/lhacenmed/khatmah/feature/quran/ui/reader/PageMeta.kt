package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.HeaderGlyphType
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.RiwayaConfig
import com.lhacenmed.khatmah.feature.quran.data.db.HeaderGlyphEntity
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb

private val EAST_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/** Converts an integer to Eastern-Arabic-Indic numerals. */
private fun east(n: Int): String = n.toString().map { EAST_DIGITS[it - '0'] }.joinToString("")

/**
 * Toolbar + page-info text for one page. Shared by the activity (toolbar) and the page views
 * (header/footer overlay) so they always agree. The representative sura is the one of the first
 * ayah on the page (mushaf convention); the juz is that ayah's juz.
 */
data class PageMeta(
    val suraName: String,
    val juz: Int,
    val page: Int,
    /** QCF4_QBSML running-head glyph for this page's sura(s); "" when none (e.g. al-Fatiha). */
    val suraGlyph: String = "",
    /** QCF4_QBSML glyph for this page's juz number; "" when not downloaded. */
    val juzGlyph: String = "",
) {
    val toolbarTitle: String get() = "سُورَةُ $suraName"
    val toolbarSubtitle: String get() = "صفحة ${east(page)}، جزء ${east(juz)}"
    val headerSura: String get() = suraName
    val headerJuz: String get() = "جزء ${east(juz)}"
    val footerPage: String get() = east(page)

    // Bottom page-jump slider popup: "page N" line + "sura - juz J" line.
    val sliderPage: String get() = "صفحة ${east(page)}"
    val sliderSuraJuz: String get() = "$suraName - الجزء ${east(juz)}"
}

/**
 * Builds [PageMeta] for the QCF4 reader straight from the DB page-start anchors (no word parsing).
 * The text reader builds its own meta from its dynamic pages but reuses [juzForVerse] here.
 */
object ReaderMeta {

    // Canonical juz' boundaries as (sura, ayah) start positions — a fixed table independent of
    // pagination, so it holds for any riwaya.
    private val JUZ_SURA = intArrayOf(
        1, 2, 2, 3, 4, 4, 5, 6, 7, 8, 9, 11, 12, 15, 17,
        18, 21, 23, 25, 27, 29, 33, 36, 39, 41, 46, 51, 58, 67, 78,
    )
    private val JUZ_AYA = intArrayOf(
        1, 142, 253, 93, 24, 148, 82, 111, 88, 41, 93, 6, 53, 1, 1,
        75, 1, 1, 21, 56, 46, 31, 28, 32, 47, 1, 31, 1, 1, 1,
    )

    // Precomputed metadata cache for all pages of the most-recent riwaya.
    @Volatile
    private var metaCache: Pair<String, Map<Int, PageMeta>>? = null

    /**
     * Loads the fully resolved metadata for all pages of [riwayaKey], driven by page start anchors.
     */
    suspend fun loadForRiwaya(context: Context, riwayaKey: String): Map<Int, PageMeta> {
        metaCache?.let { (key, map) -> if (key == riwayaKey) return map }

        val ctx = context.applicationContext
        val dao = MushafDb.get(ctx).dao()
        val suras = dao.surahs(riwayaKey).associate { it.num to it.name }
        val starts = dao.allPageStarts(riwayaKey)

        // Running-head glyphs live in the QCF4 download partition (wordKey), not the text key.
        val wordKey = Riwaya.valueOf(riwayaKey.uppercase()).config.wordKey
        val glyphs = dao.headerGlyphs(wordKey)
        val juzGlyphs = glyphs.filter { it.type == HeaderGlyphType.JUZ }.associate { it.num to it.char }
        // The sura running head is a run-length map (glyph from its page onward); fill it forward
        // so any page resolves in O(1). Pages before the first entry (al-Fatiha) have no glyph.
        val suraByPage = fillForward(glyphs.filter { it.type == HeaderGlyphType.PAGE })

        val map = starts.associate { start ->
            val bare = (suras[start.sura] ?: "").removePrefix("سورة ").trim()
            val juz = juzForVerse(start.sura, start.aya)
            start.pageNum to PageMeta(
                suraName = bare,
                juz = juz,
                page = start.pageNum,
                suraGlyph = suraByPage[start.pageNum] ?: "",
                juzGlyph = juzGlyphs[juz] ?: "",
            )
        }

        metaCache = riwayaKey to map
        return map
    }

    /**
     * Expands the run-length [rows] (a glyph effective from its `num` page onward) into a per-page
     * map. Pages before the first entry are absent (e.g. al-Fatiha, which has no running-head glyph).
     */
    private fun fillForward(rows: List<HeaderGlyphEntity>): Map<Int, String> {
        if (rows.isEmpty()) return emptyMap()
        val sorted = rows.sortedBy { it.num }
        val out = HashMap<Int, String>(RiwayaConfig.PAGE_COUNT)
        var idx = 0
        var cur = ""
        for (page in 1..RiwayaConfig.PAGE_COUNT) {
            while (idx < sorted.size && sorted[idx].num <= page) { cur = sorted[idx].char; idx++ }
            if (cur.isNotEmpty()) out[page] = cur
        }
        return out
    }

    /** Juz' number (1..30) that [sura]:[aya] belongs to. */
    fun juzForVerse(sura: Int, aya: Int): Int {
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
