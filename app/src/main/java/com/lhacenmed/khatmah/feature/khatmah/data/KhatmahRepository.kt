package com.lhacenmed.khatmah.feature.khatmah.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

/** Sura names + juz number needed by TodayTab to display session info. */
data class SessionMeta(val startSuraName: String, val endSuraName: String, val juzNum: Int)

class KhatmahRepository(private val context: Context) {

    private val khatmahDb by lazy { KhatmahDatabase.get(context) }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Maximum representable daily reading: 10 Juz' + 7 Rub' = 87 Rub'. */
    private val MAX_DAILY_RUB = 87

    // ── Quran DB ──────────────────────────────────────────────────────────────

    private fun openQuranDb(): SQLiteDatabase {
        val file = context.getDatabasePath("quran.db")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            context.assets.open("databases/quran.db").use { it.copyTo(file.outputStream()) }
        }
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun loadAthmanRows(db: SQLiteDatabase): List<AthmanRow> {
        val rows = mutableListOf<AthmanRow>()
        db.rawQuery(
            "SELECT start_sura, start_ayah, end_sura, CAST(end_ayah AS INTEGER), end_page_number" +
                    " FROM quran_athman",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                rows += AthmanRow(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4))
            }
        }
        return rows
    }

    private fun loadSuraNames(db: SQLiteDatabase): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        db.rawQuery("SELECT id_sura, sura FROM quran_index", null).use { c ->
            while (c.moveToNext()) map[c.getInt(0)] = c.getString(1)
        }
        return map
    }

    private fun getJuzStart(db: SQLiteDatabase, juz: Int): Pair<Int, Int> {
        db.rawQuery(
            "SELECT sura, aya FROM quran_ajzaa WHERE num_joza=? LIMIT 1",
            arrayOf(juz.toString()),
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0) to c.getInt(1)
        }
        return 1 to 1
    }

    private fun getStartPage(db: SQLiteDatabase, sura: Int, aya: Int): Int {
        db.rawQuery(
            "SELECT page_number FROM arabic_text WHERE sura=? AND ayah=? LIMIT 1",
            arrayOf(sura.toString(), aya.toString()),
        ).use { c ->
            if (c.moveToFirst()) return c.getInt(0)
        }
        return 1
    }

    // ── Sura-name cache ───────────────────────────────────────────────────────

    @Volatile private var cachedSuraNames: Map<Int, String>? = null

    private fun getJuzForSura(db: SQLiteDatabase, sura: Int): Int {
        db.rawQuery(
            "SELECT num_joza FROM quran_ajzaa WHERE sura <= ? ORDER BY sura DESC LIMIT 1",
            arrayOf(sura.toString()),
        ).use { c -> if (c.moveToFirst()) return c.getInt(0) }
        return 1
    }

    suspend fun sessionMeta(startSura: Int, endSura: Int): SessionMeta =
        withContext(Dispatchers.IO) {
            openQuranDb().use { db ->
                val names = cachedSuraNames
                    ?: loadSuraNames(db).also { cachedSuraNames = it }
                SessionMeta(
                    startSuraName = names[startSura].orEmpty(),
                    endSuraName   = names[endSura].orEmpty(),
                    juzNum        = getJuzForSura(db, startSura),
                )
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
     * Builds and persists a full Khatmah schedule.
     * Runs entirely on [Dispatchers.IO]; returns a [KhatmahResult] with the new
     * row ID and UI-ready session snapshots for the success dialog.
     */
    suspend fun createKhatmah(startJuz: Int, dailyAjza: Int, dailyArba: Int): KhatmahResult =
        withContext(Dispatchers.IO) {
            val (sessions, suraNames) = openQuranDb().use { qDb ->
                buildSessions(qDb, startJuz, dailyAjza, dailyArba) to loadSuraNames(qDb)
            }
            val id = khatmahDb.dao().insertKhatmah(
                KhatmahEntity(
                    startJuz  = startJuz,
                    totalDays = sessions.size,
                    dailyAjza = dailyAjza,
                    dailyArba = dailyArba,
                )
            )
            khatmahDb.dao().insertSessions(sessions.map { it.copy(khatmahId = id) })

            val displays = sessions.map { s ->
                SessionDisplay(
                    dayNumber     = s.dayNumber,
                    startSuraName = suraNames[s.startSura] ?: s.startSura.toString(),
                    startAya      = s.startAya,
                    endSuraName   = suraNames[s.endSura] ?: s.endSura.toString(),
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

    private fun buildSessions(
        qDb:       SQLiteDatabase,
        startJuz:  Int,
        dailyAjza: Int,
        dailyArba: Int,
    ): List<KhatmahSessionEntity> {
        val athman            = loadAthmanRows(qDb)
        val (juzSura, juzAya) = getJuzStart(qDb, startJuz)
        val startIdx          = athman.indexOfFirst {
            it.startSura == juzSura && it.startAya == juzAya
        }.coerceAtLeast(0)

        // 1 Rub' = 2 athman rows; floor to at least 2 (1 Rub' minimum)
        val dailyAthman = (dailyAjza * 16 + dailyArba * 2).coerceAtLeast(2)
        val relevant    = athman.subList(startIdx, athman.size)
        val sessions    = mutableListOf<KhatmahSessionEntity>()
        var offset      = 0
        var dayNum      = 1

        while (offset < relevant.size) {
            val chunkEnd = min(offset + dailyAthman - 1, relevant.size - 1)
            val first    = relevant[offset]
            val last     = relevant[chunkEnd]
            sessions += KhatmahSessionEntity(
                khatmahId = 0L,
                dayNumber = dayNum++,
                startSura = first.startSura,
                startAya  = first.startAya,
                endSura   = last.endSura,
                endAya    = last.endAya,
                startPage = getStartPage(qDb, first.startSura, first.startAya),
                endPage   = last.endPage,
            )
            offset += dailyAthman
        }
        return sessions
    }
}