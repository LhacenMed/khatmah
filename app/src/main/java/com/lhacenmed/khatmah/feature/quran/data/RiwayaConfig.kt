package com.lhacenmed.khatmah.feature.quran.data

import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya

/**
 * Everything that varies between QCF4 mushaf riwayat, in one place. Adding a new riwaya
 * (e.g. Qaloon) is a single [entries] row — no new repository, no new download state, no new
 * branch anywhere else. The whole QCF4 stack is driven off this table via [of].
 *
 * Two partition keys matter and are deliberately distinct:
 *  - [wordKey] partitions the QCF4 glyph data (mushaf_page / mushaf_word / mushaf_verse_page).
 *    Hafs reuses the base key ("hafs"); Warsh has its own ("warsh_qcf4") so it never collides
 *    with the shared Warsh text data.
 *  - The base data (surahs / page starts / verses, shared with the text reader) is keyed by the
 *    enum's [Riwaya.dbKey] ("hafs" | "warsh"); callers read it straight off [riwaya].
 */
data class RiwayaConfig(
    val riwaya: Riwaya,
    /** QCF4 glyph-data partition key in [com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb]. */
    val wordKey: String,
    /** Per-riwaya SharedPreferences file holding the "db ready" flag. */
    val prefsName: String,
    /** .7z bundle of the riwaya's TTF fonts and page JSON. */
    val bundleUrl: String,
    /** Count of numbered QCF4 glyph fonts (e.g. 47 Hafs, 50 Warsh). */
    private val glyphFontCount: Int,
    /** Numbered glyph-font prefix, e.g. "QCF4_Hafs" → `QCF4_Hafs_01_W.ttf`. */
    private val glyphFontPrefix: String,
    /** Per-sura ayah counts (114 entries) — riwaya-specific; used for surah-header circles. */
    private val ayaCounts: IntArray,
) {
    /** All TTF files the bundle must contain: the numbered glyphs + the two QBSML faces. */
    val allFontFiles: List<String> = buildList {
        (1..glyphFontCount).forEach { n -> add("%s_%02d_W.ttf".format(glyphFontPrefix, n)) }
        add("QCF4_QBSML.ttf")
        add("QCF2_QBSML.ttf")
    }

    /** Maps a JSON font name to its TTF file. The QBSML faces have no `_W` suffix. */
    fun fontFileName(fontName: String): String = when (fontName) {
        "QCF4_QBSML", "QCF2_QBSML" -> "$fontName.ttf"
        else -> "${fontName}_W.ttf"
    }

    /** Ayah count for [suraNum] (1..114), or 0 out of range. */
    fun ayaCount(suraNum: Int): Int = if (suraNum in 1..114) ayaCounts[suraNum - 1] else 0

    companion object {
        /** Every QCF4 mushaf is the standard 604-page madinah layout. */
        const val PAGE_COUNT = 604

        fun of(riwaya: Riwaya): RiwayaConfig = entries.getValue(riwaya)

        private val entries: Map<Riwaya, RiwayaConfig> = listOf(
            RiwayaConfig(
                riwaya = Riwaya.HAFS,
                wordKey = "hafs",
                prefsName = "hafs_qcf4_prefs",
                bundleUrl = "https://raw.githubusercontent.com/LhacenMed/khatmah-hafs-qcf4/main/hafs.7z",
                glyphFontCount = 47,
                glyphFontPrefix = "QCF4_Hafs",
                ayaCounts = SURA_AYA_COUNTS_HAFS,
            ),
            RiwayaConfig(
                riwaya = Riwaya.WARSH,
                wordKey = "warsh_qcf4",
                prefsName = "warsh_qcf4_prefs",
                bundleUrl = "https://raw.githubusercontent.com/LhacenMed/khatmah-warsh-qcf4/main/warsh.7z",
                glyphFontCount = 50,
                glyphFontPrefix = "QCF4_Warsh",
                ayaCounts = SURA_AYA_COUNTS_WARSH,
            ),
        ).associateBy { it.riwaya }
    }
}

private val SURA_AYA_COUNTS_HAFS = intArrayOf(
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
    123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
    34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
    54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
    60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
    14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
    28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
    15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
    11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
    5, 4, 5, 6,
)

private val SURA_AYA_COUNTS_WARSH = intArrayOf(
    7, 285, 200, 175, 122, 167, 206, 76, 130, 109,
    121, 111, 44, 54, 99, 128, 110, 105, 99, 134,
    111, 76, 119, 62, 77, 226, 95, 88, 69, 59,
    33, 30, 73, 54, 46, 82, 182, 86, 72, 84,
    53, 50, 89, 56, 36, 34, 39, 29, 18, 45,
    60, 47, 61, 55, 77, 99, 28, 21, 24, 13,
    14, 11, 11, 18, 12, 12, 31, 52, 52, 44,
    30, 28, 18, 55, 39, 31, 50, 40, 45, 42,
    29, 19, 36, 25, 22, 17, 19, 26, 32, 20,
    15, 21, 11, 8, 8, 20, 5, 8, 9, 11,
    10, 8, 3, 9, 5, 5, 6, 3, 6, 3,
    5, 4, 5, 6,
)
