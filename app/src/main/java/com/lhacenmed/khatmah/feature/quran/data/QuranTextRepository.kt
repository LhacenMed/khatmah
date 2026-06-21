package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import com.lhacenmed.khatmah.feature.quran.data.DivType
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.normalizeArabic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Models ────────────────────────────────────────────────────────────────────

data class QuranAya(
    val suraNum: Int,
    val sura:    String,   // surah Arabic name
    val ayaNum:  Int,
    val aya:     String,   // verse text
    val juz:     String,   // formatted Arabic string e.g. "الجزء ١٥"
)

data class SearchResult(
    val suraNum:   Int,
    val ayaNum:    Int,
    val suraName:  String,
    val ayaText:   String,
    val spansPair: Boolean = false,
)

data class SurahInfo(val num: Int, val name: String, val ayaCount: Int)

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Reads Quran *text* data from [MushafDb] (seeded from bundled riwaya JSON files): the verse
 * stream the native text reader paginates, and the normalized search index. All methods take a
 * [riwaya] key ("hafs" | "warsh") so the whole feature stays riwaya-driven.
 *
 * QCF4 glyph data lives in [Qcf4Repository]; this repo is purely the text layer.
 */
class QuranTextRepository(context: Context) {

    private val dao = MushafDb.get(context).dao()

    // ── In-memory search cache ────────────────────────────────────────────────

    private data class CacheEntry(
        val suraNum:    Int,
        val ayaNum:     Int,
        val suraName:   String,
        val ayaText:    String,
        val normalized: String,
    )

    // Pair of (riwaya, entries) — invalidated when riwaya changes.
    @Volatile private var searchCache: Pair<String, List<CacheEntry>>? = null

    private suspend fun getSearchCache(riwaya: String): List<CacheEntry> {
        searchCache?.let { (r, e) -> if (r == riwaya) return e }
        return withContext(Dispatchers.IO) {
            val verses  = dao.verses(riwaya)
            val names   = dao.surahs(riwaya).associate { it.num to it.name }
            val entries = verses.map { v ->
                CacheEntry(v.sura, v.aya, names[v.sura].orEmpty(), v.text, v.normalized)
            }
            searchCache = riwaya to entries
            entries
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all ayas with surah name and juz label — the input the text reader paginates.
     * Juz is computed via two-pointer scan over sorted juz markers (O(n)).
     */
    suspend fun allAyas(riwaya: String): List<QuranAya> = withContext(Dispatchers.IO) {
        val verses  = dao.verses(riwaya)               // sorted by sura, aya
        val names   = dao.surahs(riwaya).associate { it.num to it.name }
        val juzList = dao.divisions(riwaya, DivType.JUZ) // sorted by sura, aya

        var juzIdx = 0
        verses.map { v ->
            // Advance juz pointer while the next juz boundary is still ≤ this aya.
            while (juzIdx + 1 < juzList.size) {
                val nj = juzList[juzIdx + 1]
                if (nj.sura < v.sura || (nj.sura == v.sura && nj.aya <= v.aya)) juzIdx++
                else break
            }
            val juzNum = juzList.getOrNull(juzIdx)?.num ?: 1
            QuranAya(
                suraNum = v.sura,
                sura    = names[v.sura].orEmpty(),
                ayaNum  = v.aya,
                aya     = v.text,
                juz     = "الجزء ${juzNum.toArabicNum()}",
            )
        }
    }

    /** suraNum → whether a basmala precedes it. Drives where the text reader shows the basmala. */
    suspend fun bismillahMap(riwaya: String): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        dao.surahs(riwaya).associate { it.num to it.bismillahPre }
    }

    /** Surah list for the index tabs. */
    suspend fun surahList(riwaya: String): List<SurahInfo> = withContext(Dispatchers.IO) {
        dao.surahs(riwaya).map { SurahInfo(it.num, it.name, it.ayat) }
    }

    /**
     * QCF4 page range for [surahNum] — first page (aya 1) to the page of its last aya ([lastAya]) —
     * used to open a single surah as a page-windowed session in the book reader. Null if the page
     * anchors aren't in the DB. Resolving the end from the last aya (not the next surah) stays correct
     * even when several short surahs share a page.
     */
    suspend fun pageRangeForSurah(riwaya: String, surahNum: Int, lastAya: Int): IntRange? =
        withContext(Dispatchers.IO) {
            val start = dao.pageForVerse(riwaya, surahNum, 1) ?: return@withContext null
            val end = dao.pageForVerse(riwaya, surahNum, lastAya.coerceAtLeast(1)) ?: start
            start..end
        }

    /**
     * Two-pass normalized Arabic search.
     *
     * Pass 1: single ayas whose normalized text contains the normalized query.
     * Pass 2: consecutive pairs spanning an aya boundary.
     */
    suspend fun search(query: String, riwaya: String, limit: Int = 50): List<SearchResult> =
        withContext(Dispatchers.Default) {
            val normalized = query.normalizeArabic().trim()
            if (normalized.isBlank()) return@withContext emptyList()

            val entries = getSearchCache(riwaya)
            val seen    = HashSet<Long>(limit * 2)
            val results = mutableListOf<SearchResult>()

            for (e in entries) {
                if (results.size >= limit) break
                if (!e.normalized.contains(normalized)) continue
                if (seen.add(ayaKey(e.suraNum, e.ayaNum)))
                    results += SearchResult(e.suraNum, e.ayaNum, e.suraName, e.ayaText)
            }

            if (results.size < limit) {
                for (i in 0 until entries.size - 1) {
                    if (results.size >= limit) break
                    val a = entries[i]; val b = entries[i + 1]
                    if (!("${a.normalized} ${b.normalized}").contains(normalized)) continue
                    if (seen.add(ayaKey(a.suraNum, a.ayaNum)))
                        results += SearchResult(
                            a.suraNum, a.ayaNum, a.suraName,
                            "${a.ayaText} ۝ ${b.ayaText}", spansPair = true,
                        )
                }
            }
            results
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ayaKey(suraNum: Int, ayaNum: Int): Long =
        suraNum.toLong() shl 32 or ayaNum.toLong()
}

// ── Arabic numeral formatter ──────────────────────────────────────────────────

private fun Int.toArabicNum(): String =
    toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
