package com.lhacenmed.khatmah.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Summary of a single surah for the index list. */
data class SurahInfo(val num: Int, val name: String, val ayaCount: Int)

/**
 * A single search hit.
 *
 * [spansPair] is true when the match spans two consecutive ayas —
 * [ayaText] then contains both ayas joined by the aya-end ornament ۝.
 */
data class SearchResult(
    val suraNum:   Int,
    val ayaNum:    Int,
    val suraName:  String,
    val ayaText:   String,
    val spansPair: Boolean = false,
)

class QuranRepository(private val context: Context) {

    // ── In-memory normalized search cache ────────────────────────────────────

    private data class CacheEntry(
        val suraNum:    Int,
        val ayaNum:     Int,
        val suraName:   String,
        val ayaText:    String,   // vocalized text for display
        val normalized: String,   // stripped + normalized for matching
    )

    @Volatile private var cache: List<CacheEntry>? = null

    /**
     * Returns the cached entry list, building it from the DB on first call.
     * The cache is built once per [QuranRepository] instance and held for its lifetime.
     */
    private suspend fun getCache(): List<CacheEntry> =
        cache ?: withContext(Dispatchers.IO) {
            cache ?: buildCache().also { cache = it }
        }

    private fun buildCache(): List<CacheEntry> =
        QuranDatabase.open(context).rawQuery(SQL_CACHE, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    CacheEntry(
                        suraNum    = c.getInt(0),
                        ayaNum     = c.getInt(1),
                        suraName   = c.getString(2).orEmpty()
                            .removePrefix("سورة ").trim(),
                        ayaText    = c.getString(3).orEmpty(),
                        // Normalize the stored arabic_text (handles ٱ, أ, etc. in DB values).
                        normalized = c.getString(4).orEmpty().normalizeArabic(),
                    )
                )
            }
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /** All 6236 ayas ordered by sura then aya number. */
    suspend fun allAyas(): List<QuranAya> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_AYAS, null).use { c ->
            val iSuraNum = c.getColumnIndexOrThrow("sura_num")
            val iSura    = c.getColumnIndexOrThrow("sura")
            val iAyaNum  = c.getColumnIndexOrThrow("aya_num")
            val iAya     = c.getColumnIndexOrThrow("aya")
            val iJuz     = c.getColumnIndexOrThrow("juz")
            buildList {
                while (c.moveToNext()) add(
                    QuranAya(
                        suraNum = c.getInt(iSuraNum),
                        sura    = c.getString(iSura).orEmpty(),
                        ayaNum  = c.getInt(iAyaNum),
                        aya     = c.getString(iAya).orEmpty(),
                        juz     = c.getString(iJuz).orEmpty(),
                    )
                )
            }
        }
    }

    /** All 114 surahs with aya counts, ordered by surah number. */
    suspend fun surahList(): List<SurahInfo> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_SURAHS, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    SurahInfo(
                        num      = c.getInt(0),
                        name     = c.getString(1).orEmpty(),
                        ayaCount = c.getInt(2),
                    )
                )
            }
        }
    }

    /**
     * Searches ayas using normalized Arabic matching.
     *
     * Pass 1 — single ayas: each entry's [CacheEntry.normalized] contains the normalized query.
     * Pass 2 — consecutive pairs: for queries that span an aya boundary, the concatenated
     *          normalized text of [entry[i] + entry[i+1]] is checked. The result points to
     *          the first aya of the pair with [SearchResult.spansPair] = true.
     *
     * All alef variants, teh marbuta, alef maqsura, and harakat are normalized on both
     * sides before comparison — see [normalizeArabic].
     */
    suspend fun search(query: String, limit: Int = 50): List<SearchResult> =
        withContext(Dispatchers.Default) {
            val normalized = query.normalizeArabic().trim()
            if (normalized.isBlank()) return@withContext emptyList()

            val entries = getCache()
            val seen    = HashSet<Long>(limit * 2)
            val results = mutableListOf<SearchResult>()

            // Pass 1: single-aya matches
            for (e in entries) {
                if (results.size >= limit) break
                if (!e.normalized.contains(normalized)) continue
                if (seen.add(ayaKey(e.suraNum, e.ayaNum))) {
                    results += SearchResult(e.suraNum, e.ayaNum, e.suraName, e.ayaText)
                }
            }

            // Pass 2: cross-aya pair matches (query spans an aya boundary)
            if (results.size < limit) {
                for (i in 0 until entries.size - 1) {
                    if (results.size >= limit) break
                    val a = entries[i]
                    val b = entries[i + 1]
                    if (!("${a.normalized} ${b.normalized}").contains(normalized)) continue
                    if (seen.add(ayaKey(a.suraNum, a.ayaNum))) {
                        results += SearchResult(
                            suraNum   = a.suraNum,
                            ayaNum    = a.ayaNum,
                            suraName  = a.suraName,
                            ayaText   = "${a.ayaText} ۝ ${b.ayaText}",
                            spansPair = true,
                        )
                    }
                }
            }

            results
        }

    private fun ayaKey(suraNum: Int, ayaNum: Int): Long =
        suraNum.toLong() shl 32 or ayaNum.toLong()

    private companion object {
        const val SQL_AYAS =
            "SELECT sura_num, sura, aya_num, aya, juz FROM quran ORDER BY sura_num, aya_num"
        const val SQL_SURAHS =
            "SELECT sura_num, sura, COUNT(aya_num) FROM quran GROUP BY sura_num ORDER BY sura_num"

        // Build the in-memory cache: JOIN with arabic_text for the unvocalized column;
        // COALESCE falls back to quran.aya for the 22 rows absent from arabic_text.
        const val SQL_CACHE = """
            SELECT q.sura_num, q.aya_num, q.sura, q.aya,
                   COALESCE(at.arabic_text, q.aya)
            FROM quran q
            LEFT JOIN arabic_text at ON at.sura = q.sura_num AND at.ayah = q.aya_num
            ORDER BY q.sura_num, q.aya_num
        """
    }
}
