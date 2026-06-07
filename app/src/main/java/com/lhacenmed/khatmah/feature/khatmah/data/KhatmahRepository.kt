package com.lhacenmed.khatmah.feature.khatmah.data

import android.content.Context
import com.lhacenmed.khatmah.feature.mushaf.data.DivType
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.mushaf.data.db.DivisionEntity
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDao
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import com.lhacenmed.khatmah.feature.mushaf.data.db.PageStartEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

/** Sura names + juz + first aya text needed by TodayTab to display session info. */
data class SessionMeta(
    val startSuraName: String,
    val endSuraName:   String,
    val juzNum:        Int,
    val firstAyaText:  String,
)

class KhatmahRepository(private val context: Context) {

    private val khatmahDb by lazy { KhatmahDatabase.get(context) }
    private val mushafDao  by lazy { MushafDb.get(context).dao() }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Maximum representable daily reading: 10 Juz' + 7 Rub' = 87 Rub'. */
    private val MAX_DAILY_RUB = 87

    // ── Riwaya ────────────────────────────────────────────────────────────────

    /** Current riwaya DB key — read live so khatmah always uses the selected riwaya. */
    private val riwayaKey: String
        get() = MushafPrefs.selected.value.riwaya.dbKey

    // ── Name cache ────────────────────────────────────────────────────────────

    /** (riwayaKey → surahNum → name). Invalidated when riwaya changes. */
    @Volatile private var cachedNames: Pair<String, Map<Int, String>>? = null

    /**
     * Pre-warms the surah name cache from [MushafDb] so TodayTab loads instantly.
     * No-op if the DB hasn't been seeded yet (re-queried on next real access).
     */
    suspend fun warmCache() = withContext(Dispatchers.IO) {
        val key = riwayaKey
        if (cachedNames?.let { it.first == key && it.second.isNotEmpty() } == true) return@withContext
        val names = mushafDao.surahs(key).associate { it.num to it.name }
        if (names.isNotEmpty()) cachedNames = key to names
    }

    private suspend fun suraNames(key: String): Map<Int, String> {
        cachedNames?.let { (r, n) -> if (r == key && n.isNotEmpty()) return n }
        return withContext(Dispatchers.IO) {
            mushafDao.surahs(key).associate { it.num to it.name }.also {
                if (it.isNotEmpty()) cachedNames = key to it
            }
        }
    }

    // ── Today / session flow wrappers ─────────────────────────────────────────

    fun activeKhatmahFlow(): Flow<KhatmahEntity?> = khatmahDb.dao().activeKhatmah()

    fun currentSession(khatmahId: Long): Flow<KhatmahSessionEntity?> =
        khatmahDb.dao().firstUnread(khatmahId)

    fun readCount(khatmahId: Long): Flow<Int> =
        khatmahDb.dao().readCount(khatmahId)

    suspend fun markSessionRead(id: Long) = withContext(Dispatchers.IO) {
        khatmahDb.dao().markRead(id)
    }

    suspend fun sessionMeta(startSura: Int, startAya: Int, endSura: Int, riwayaKey: String): SessionMeta =
        withContext(Dispatchers.IO) {
            val names  = suraNames(riwayaKey)
            val juzNum = mushafDao.divisionForVerse(riwayaKey, DivType.JUZ, startSura, startAya)?.num ?: 1
            val text   = mushafDao.verse(riwayaKey, startSura, startAya)?.text.orEmpty()
            SessionMeta(
                startSuraName = names[startSura].orEmpty(),
                endSuraName   = names[endSura].orEmpty(),
                juzNum        = juzNum,
                firstAyaText  = text,
            )
        }

    // ── Public calculations (pure, no IO) ─────────────────────────────────────

    /** Total Rub' from [startJuz] to end of Quran. Each Juz' = 8 Rub'. */
    fun totalRub(startJuz: Int): Int = (30 - startJuz + 1) * 8

    fun computeDays(startJuz: Int, ajza: Int, arba: Int): Int {
        val daily = (ajza * 8 + arba).coerceAtLeast(1)
        return ceil(totalRub(startJuz).toDouble() / daily).toInt()
    }

    /**
     * Given a target [days], returns the nearest valid (ajza 0–10, arba 0–7) pair,
     * capped so daily rub never exceeds totalRub([startJuz]).
     */
    fun computeDailyAmount(startJuz: Int, days: Int): Pair<Int, Int> {
        val cap = totalRub(startJuz).coerceAtMost(MAX_DAILY_RUB)
        val target = ceil(totalRub(startJuz).toDouble() / days.coerceAtLeast(1))
            .toInt().coerceIn(1, cap)
        return snapToValid(target)
    }

    /**
     * Clamps (ajza, arba) so the daily Rub' never exceeds totalRub([startJuz])
     * or the UI maximum (87), then decomposes into a valid (ajza, arba) pair.
     */
    fun snapDailyAmount(startJuz: Int, ajza: Int, arba: Int): Pair<Int, Int> {
        val cap = totalRub(startJuz).coerceAtMost(MAX_DAILY_RUB)
        val raw = (ajza * 8 + arba).coerceIn(1, cap)
        return snapToValid(raw)
    }

    fun minDays(startJuz: Int): Int =
        ceil(totalRub(startJuz).toDouble() / MAX_DAILY_RUB).toInt().coerceAtLeast(1)

    fun maxDays(startJuz: Int): Int = totalRub(startJuz) // 1 Rub'/day

    // ── Persist ───────────────────────────────────────────────────────────────

    /**
     * Builds and persists a full Khatmah schedule for the currently selected riwaya.
     * Runs entirely on [Dispatchers.IO]; returns a [KhatmahResult] with the new
     * row ID and UI-ready session snapshots for the success dialog.
     */
    suspend fun createKhatmah(startJuz: Int, dailyAjza: Int, dailyArba: Int): KhatmahResult =
        withContext(Dispatchers.IO) {
            val key      = riwayaKey
            val names    = suraNames(key)
            val sessions = buildSessions(key, startJuz, dailyAjza, dailyArba)

            val id = khatmahDb.dao().insertKhatmah(
                KhatmahEntity(
                    startJuz  = startJuz,
                    totalDays = sessions.size,
                    dailyAjza = dailyAjza,
                    dailyArba = dailyArba,
                    riwaya    = key,
                )
            )
            khatmahDb.dao().insertSessions(sessions.map { it.copy(khatmahId = id) })

            val displays = sessions.map { s ->
                SessionDisplay(
                    dayNumber     = s.dayNumber,
                    startSuraName = names[s.startSura] ?: s.startSura.toString(),
                    startAya      = s.startAya,
                    endSuraName   = names[s.endSura] ?: s.endSura.toString(),
                    endAya        = s.endAya,
                    startPage     = s.startPage,
                    endPage       = s.endPage,
                )
            }
            KhatmahResult(id, displays)
        }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Decomposes [rub] into (ajza 0–10, arba 0–7).
     * arba=7 is now a valid first-class value; no rounding-up to the next juz'.
     */
    private fun snapToValid(rub: Int): Pair<Int, Int> =
        (rub / 8).coerceAtMost(10) to (rub % 8)

    /**
     * Builds session entities from division markers in [MushafDb].
     *
     * Warsh uses THUMN divisions (480 entries → finer granularity).
     * Hafs  uses RUB  divisions (240 entries → rub' al-hizb).
     *
     * Daily segment count:
     *   Warsh: dailyAjza × 16 + dailyArba × 2   (thumn units; 1 rub' = 2 thumn)
     *   Hafs : dailyAjza ×  8 + dailyArba        (rub'  units)
     */
    private suspend fun buildSessions(
        key:       String,
        startJuz:  Int,
        dailyAjza: Int,
        dailyArba: Int,
    ): List<KhatmahSessionEntity> {
        val warsh      = key == Riwaya.WARSH.dbKey
        val divType    = if (warsh) DivType.THUMN else DivType.RUB
        val divStarts  = mushafDao.divisions(key, divType)       // sorted by num → ascending sura/aya
        val juzStarts  = mushafDao.divisions(key, DivType.JUZ)
        val verses     = mushafDao.verseIdentities(key)          // sorted by sura, aya
        val pageStarts = mushafDao.allPageStarts(key)            // sorted by page_num

        val juzStart = juzStarts.find { it.num == startJuz } ?: juzStarts.firstOrNull()
        ?: return emptyList()

        val segments = buildSegments(divStarts, verses, pageStarts)

        val startIdx = segments.indexOfFirst { s ->
            s.startSura > juzStart.sura ||
                    (s.startSura == juzStart.sura && s.startAya >= juzStart.aya)
        }.coerceAtLeast(0)

        // Units: 1 rub' = 2 thumn; daily reading expressed in base segment units.
        val dailySegs = if (warsh) {
            (dailyAjza * 16 + dailyArba * 2).coerceAtLeast(2)
        } else {
            (dailyAjza * 8 + dailyArba).coerceAtLeast(1)
        }

        val relevant = segments.subList(startIdx, segments.size)
        val result   = mutableListOf<KhatmahSessionEntity>()
        var offset   = 0
        var dayNum   = 1

        while (offset < relevant.size) {
            val end   = min(offset + dailySegs - 1, relevant.size - 1)
            val first = relevant[offset]
            val last  = relevant[end]
            result += KhatmahSessionEntity(
                khatmahId = 0L,
                dayNumber = dayNum++,
                startSura = first.startSura,
                startAya  = first.startAya,
                endSura   = last.endSura,
                endAya    = last.endAya,
                startPage = pageForVerse(pageStarts, first.startSura, first.startAya),
                endPage   = last.endPage,
            )
            offset += dailySegs
        }
        return result
    }

    /**
     * Converts flat division start markers into (start → end) [AthmanRow] pairs.
     * End of segment i = verse immediately before start of segment i+1.
     * End of last segment = last verse of the Quran.
     */
    private fun buildSegments(
        starts:     List<DivisionEntity>,
        verses:     List<MushafDao.VerseIdentity>,
        pageStarts: List<PageStartEntity>,
    ): List<AthmanRow> {
        if (starts.isEmpty() || verses.isEmpty()) return emptyList()
        return starts.mapIndexedNotNull { i, s ->
            val endVerse = if (i < starts.lastIndex) {
                val next = starts[i + 1]
                verseBefore(verses, next.sura, next.aya)
            } else {
                verses.last()
            } ?: return@mapIndexedNotNull null
            AthmanRow(
                startSura = s.sura,
                startAya  = s.aya,
                endSura   = endVerse.sura,
                endAya    = endVerse.aya,
                endPage   = pageForVerse(pageStarts, endVerse.sura, endVerse.aya),
            )
        }
    }

    /**
     * Binary search — returns the last [MushafDao.VerseIdentity] strictly before (sura, aya).
     * [verses] must be sorted by sura then aya.
     */
    private fun verseBefore(
        verses: List<MushafDao.VerseIdentity>,
        sura:   Int,
        aya:    Int,
    ): MushafDao.VerseIdentity? {
        var lo = 0; var hi = verses.lastIndex; var best: MushafDao.VerseIdentity? = null
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v   = verses[mid]
            if (v.sura < sura || (v.sura == sura && v.aya < aya)) { best = v; lo = mid + 1 }
            else hi = mid - 1
        }
        return best
    }

    /**
     * Returns the 1-based page number for (sura, aya) using pre-loaded [pageStarts]
     * (sorted by page_num, which is monotonically increasing with sura/aya in any mushaf).
     */
    private fun pageForVerse(pageStarts: List<PageStartEntity>, sura: Int, aya: Int): Int {
        var result = pageStarts.firstOrNull()?.pageNum ?: 1
        for (ps in pageStarts) {
            if (ps.sura < sura || (ps.sura == sura && ps.aya <= aya)) result = ps.pageNum
            else break
        }
        return result
    }
}