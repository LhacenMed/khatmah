package com.lhacenmed.khatmah.feature.quran.data

import android.content.Context
import com.lhacenmed.khatmah.feature.mushaf.data.DivType
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import com.lhacenmed.khatmah.feature.mushaf.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.mushaf.data.db.VerseEntity
import com.lhacenmed.khatmah.feature.mushaf.data.normalizeArabic
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

data class SurahInfo(val num: Int, val name: String, val ayaCount: Int)

data class SearchResult(
    val suraNum:   Int,
    val ayaNum:    Int,
    val suraName:  String,
    val ayaText:   String,
    val spansPair: Boolean = false,
)

// ── Repository ────────────────────────────────────────────────────────────────

/**
 * Reads Quran text data from [MushafDb] (seeded from bundled riwaya JSON files).
 * All methods accept a [riwaya] key ("hafs" | "warsh") to support riwaya switching.
 */
class QuranRepository(private val context: Context) {

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
     * Returns all ayas with surah name and juz label for the text page builder.
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

    /** Returns surah list for the index tab. */
    suspend fun surahList(riwaya: String): List<SurahInfo> = withContext(Dispatchers.IO) {
        dao.surahs(riwaya).map { SurahInfo(it.num, it.name, it.ayat) }
    }

    /**
     * Returns a map of bismillah_pre per surah number.
     * Used by [QuranPageBuilder] to decide where to show the basmala.
     */
    suspend fun bismillahMap(riwaya: String): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        dao.surahs(riwaya).associate { it.num to it.bismillahPre }
    }

    /**
     * Returns packed (sura shl 32 or aya) → 0-based page index.
     * Derived from [mushaf_page_start] via two-pointer scan (O(n)). Used by image/xml modes.
     */
    suspend fun ayaPageIndex(riwaya: String): Map<Long, Int> = withContext(Dispatchers.IO) {
        val starts = dao.allPageStarts(riwaya)           // sorted by page_num
        val verses = dao.verses(riwaya)                  // sorted by sura, aya
        val map    = HashMap<Long, Int>(verses.size)

        var pageIdx = 0
        for (v in verses) {
            // Advance page pointer while the next page start is ≤ this verse.
            while (pageIdx + 1 < starts.size) {
                val ns = starts[pageIdx + 1]
                if (ns.sura < v.sura || (ns.sura == v.sura && ns.aya <= v.aya)) pageIdx++
                else break
            }
            val pageNum = starts.getOrNull(pageIdx)?.pageNum ?: 1
            map[(v.sura.toLong() shl 32) or v.aya.toLong()] = pageNum - 1
        }
        map
    }

    /**
     * Two-pass normalized Arabic search — matches existing search UX.
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

    /** True if the bundled data for [riwaya] has been seeded into the DB. */
    suspend fun isSeeded(riwaya: String): Boolean = withContext(Dispatchers.IO) {
        dao.verseCount(riwaya) > 0
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ayaKey(suraNum: Int, ayaNum: Int): Long =
        suraNum.toLong() shl 32 or ayaNum.toLong()
}

// ── Arabic numeral formatter ──────────────────────────────────────────────────

private fun Int.toArabicNum(): String =
    toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")