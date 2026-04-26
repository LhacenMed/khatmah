package com.lhacenmed.khatmah.data.quran

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Data ──────────────────────────────────────────────────────────────────────

data class QuranAya(
    val suraNum: Int,
    val sura:    String,
    val ayaNum:  Int,
    val aya:     String,
    val juz:     String,
)

data class SurahInfo(val num: Int, val name: String, val ayaCount: Int)

data class SearchResult(
    val suraNum:   Int,
    val ayaNum:    Int,
    val suraName:  String,
    val ayaText:   String,
    val spansPair: Boolean = false,
)

// ── Arabic normalizer ─────────────────────────────────────────────────────────

/**
 * Normalizes Arabic text for search by unifying visually/semantically equivalent
 * characters and stripping diacritics (harakat).
 *
 *   أ / إ / آ / ٱ (U+0671) → ا   all alef variants → bare alef
 *   ؤ             → و              waw with hamza
 *   ئ             → ي              yeh with hamza above
 *   ة             → ه              teh marbuta → heh
 *   ى             → ي              alef maqsura → yeh
 *   U+064B–U+065F  stripped        Arabic combining diacritics (harakat)
 *   U+0640         stripped        tatweel / kashida
 */
fun String.normalizeArabic(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        if (c in '\u064B'..'\u065F' || c == '\u0640') continue
        sb.append(when (c) {
            'أ', 'إ', 'آ', '\u0671' -> 'ا'
            'ؤ'                      -> 'و'
            'ئ'                      -> 'ي'
            'ة'                      -> 'ه'
            'ى'                      -> 'ي'
            else                     -> c
        })
    }
    return sb.toString()
}

// ── Repository ────────────────────────────────────────────────────────────────

class QuranRepository(private val context: Context) {

    private data class CacheEntry(
        val suraNum:    Int,
        val ayaNum:     Int,
        val suraName:   String,
        val ayaText:    String,
        val normalized: String,
    )

    @Volatile private var cache: List<CacheEntry>? = null

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
                        suraName   = c.getString(2).orEmpty().removePrefix("سورة ").trim(),
                        ayaText    = c.getString(3).orEmpty(),
                        normalized = c.getString(4).orEmpty().normalizeArabic(),
                    )
                )
            }
        }

    suspend fun allAyas(): List<QuranAya> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_AYAS, null).use { c ->
            val iSuraNum = c.getColumnIndexOrThrow("sura_num")
            val iSura    = c.getColumnIndexOrThrow("sura")
            val iAyaNum  = c.getColumnIndexOrThrow("aya_num")
            val iAya     = c.getColumnIndexOrThrow("aya")
            val iJuz     = c.getColumnIndexOrThrow("juz")
            buildList {
                while (c.moveToNext()) add(
                    QuranAya(c.getInt(iSuraNum), c.getString(iSura).orEmpty(),
                        c.getInt(iAyaNum),  c.getString(iAya).orEmpty(),
                        c.getString(iJuz).orEmpty())
                )
            }
        }
    }

    suspend fun surahList(): List<SurahInfo> = withContext(Dispatchers.IO) {
        QuranDatabase.open(context).rawQuery(SQL_SURAHS, null).use { c ->
            buildList {
                while (c.moveToNext())
                    add(SurahInfo(c.getInt(0), c.getString(1).orEmpty(), c.getInt(2)))
            }
        }
    }

    /**
     * Searches ayas using normalized Arabic matching.
     *
     * Pass 1 — single ayas: each entry's normalized text contains the normalized query.
     * Pass 2 — consecutive pairs: for queries that span an aya boundary, the concatenated
     *          normalized text of [entry[i] + entry[i+1]] is checked. The result points to
     *          the first aya of the pair with [SearchResult.spansPair] = true.
     */
    suspend fun search(query: String, limit: Int = 50): List<SearchResult> =
        withContext(Dispatchers.Default) {
            val normalized = query.normalizeArabic().trim()
            if (normalized.isBlank()) return@withContext emptyList()

            val entries = getCache()
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
                        results += SearchResult(a.suraNum, a.ayaNum, a.suraName,
                            "${a.ayaText} ۝ ${b.ayaText}", spansPair = true)
                }
            }
            results
        }

    private fun ayaKey(suraNum: Int, ayaNum: Int): Long =
        suraNum.toLong() shl 32 or ayaNum.toLong()

    private companion object {
        const val SQL_AYAS   = "SELECT sura_num,sura,aya_num,aya,juz FROM quran ORDER BY sura_num,aya_num"
        const val SQL_SURAHS = "SELECT sura_num,sura,COUNT(aya_num) FROM quran GROUP BY sura_num ORDER BY sura_num"
        const val SQL_CACHE  = """
            SELECT q.sura_num,q.aya_num,q.sura,q.aya,COALESCE(at.arabic_text,q.aya)
            FROM quran q
            LEFT JOIN arabic_text at ON at.sura=q.sura_num AND at.ayah=q.aya_num
            ORDER BY q.sura_num,q.aya_num
        """
    }
}
