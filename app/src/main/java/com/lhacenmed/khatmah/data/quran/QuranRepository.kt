// data/quran/QuranRepository.kt
package com.lhacenmed.khatmah.data.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Models ────────────────────────────────────────────────────────────────────

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
 * Unifies visually/semantically equivalent Arabic characters and strips diacritics
 * (harakat) for robust search matching.
 *
 *   أ / إ / آ / ٱ (U+0671) → ا   all alef variants → bare alef
 *   ؤ             → و
 *   ئ             → ي
 *   ة             → ه
 *   ى             → ي
 *   U+064B–U+065F  stripped   harakat
 *   U+0640         stripped   kashida
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

// ── Database (private — used only by this file) ───────────────────────────────

/**
 * Opens quran.db from app-private storage, copying it from assets on first launch.
 * Read-only handle is opened once and reused for the app lifetime.
 */
private object QuranDb {

    private const val DB_NAME = "quran.db"
    private const val ASSET   = "databases/quran.db"

    @Volatile private var handle: SQLiteDatabase? = null

    fun open(context: Context): SQLiteDatabase = handle ?: synchronized(this) {
        handle ?: build(context.applicationContext).also { handle = it }
    }

    private fun build(ctx: Context): SQLiteDatabase {
        val dest = ctx.getDatabasePath(DB_NAME)
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            ctx.assets.open(ASSET).use { src -> dest.outputStream().use(src::copyTo) }
        }
        return SQLiteDatabase.openDatabase(dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }
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
        QuranDb.open(context).rawQuery(SQL_CACHE, null).use { c ->
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
        QuranDb.open(context).rawQuery(SQL_AYAS, null).use { c ->
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
        QuranDb.open(context).rawQuery(SQL_SURAHS, null).use { c ->
            buildList {
                while (c.moveToNext())
                    add(SurahInfo(c.getInt(0), c.getString(1).orEmpty(), c.getInt(2)))
            }
        }
    }

    /**
     * Two-pass normalized Arabic search.
     *
     * Pass 1 — single ayas: entry's normalized text contains the normalized query.
     * Pass 2 — consecutive pairs: for queries spanning an aya boundary, concatenated
     *          text of [i] + [i+1] is checked; result points to first aya with
     *          [SearchResult.spansPair] = true.
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
