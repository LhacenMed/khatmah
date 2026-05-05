package com.lhacenmed.khatmah.feature.khatmah.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min

class KhatmahRepository(private val context: Context) {

    private val khatmahDb by lazy { KhatmahDatabase.get(context) }

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

    // ── Public calculations (pure, no IO) ─────────────────────────────────────

    /** Total Rub' Hizb from [startJuz] to end. Each Juz' = 8 Rub'. */
    fun totalRub(startJuz: Int): Int = (30 - startJuz + 1) * 8

    fun computeDays(startJuz: Int, ajza: Int, arba: Int): Int {
        val daily = (ajza * 8 + arba).coerceAtLeast(1)
        return ceil(totalRub(startJuz).toDouble() / daily).toInt()
    }

    /**
     * Given a target [days], returns the nearest valid (ajza 0–10, arba 0–6) pair.
     * Invalid rub' counts ≡ 7 (mod 8) are rounded up to the next whole juz'.
     */
    fun computeDailyAmount(startJuz: Int, days: Int): Pair<Int, Int> {
        val target = ceil(totalRub(startJuz).toDouble() / days.coerceAtLeast(1))
            .toInt().coerceIn(1, 86)
        return snapToValid(target)
    }

    fun minDays(startJuz: Int): Int =
        ceil(totalRub(startJuz).toDouble() / 86).toInt().coerceAtLeast(1)

    fun maxDays(startJuz: Int): Int = totalRub(startJuz) // 1 Rub'/day max

    // ── Persist ───────────────────────────────────────────────────────────────

    /**
     * Builds and persists a full Khatmah schedule.
     * Runs entirely on [Dispatchers.IO]; returns the new khatmah row ID.
     */
    suspend fun createKhatmah(startJuz: Int, dailyAjza: Int, dailyArba: Int): Long =
        withContext(Dispatchers.IO) {
            val sessions = openQuranDb().use { qDb ->
                buildSessions(qDb, startJuz, dailyAjza, dailyArba)
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
            id
        }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun snapToValid(rub: Int): Pair<Int, Int> {
        val rem = rub % 8
        return if (rem <= 6) (rub / 8).coerceAtMost(10) to rem
        else ((rub / 8) + 1).coerceAtMost(10) to 0
    }

    private fun buildSessions(
        qDb:       SQLiteDatabase,
        startJuz:  Int,
        dailyAjza: Int,
        dailyArba: Int,
    ): List<KhatmahSessionEntity> {
        val athman       = loadAthmanRows(qDb)
        val (juzSura, juzAya) = getJuzStart(qDb, startJuz)
        val startIdx     = athman.indexOfFirst {
            it.startSura == juzSura && it.startAya == juzAya
        }.coerceAtLeast(0)

        // 1 Rub' = 2 athman rows; floor to at least 2 (1 Rub' minimum)
        val dailyAthman  = (dailyAjza * 16 + dailyArba * 2).coerceAtLeast(2)
        val relevant     = athman.subList(startIdx, athman.size)
        val sessions     = mutableListOf<KhatmahSessionEntity>()
        var offset       = 0
        var dayNum       = 1

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