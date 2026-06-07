package com.lhacenmed.khatmah.feature.qadaa.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QadaaDao {

    // ── Prayer debt ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM qadaa_prayer ORDER BY prayer ASC")
    fun prayerDebts(): Flow<List<PrayerDebtEntity>>

    @Query("UPDATE qadaa_prayer SET totalOwed = totalOwed + :count, updatedAt = :now WHERE prayer = :prayer")
    suspend fun addPrayerOwed(prayer: String, count: Int, now: Long = System.currentTimeMillis())

    /**
     * Clamps completion so totalCompleted never exceeds totalOwed —
     * preventing negative remaining counts even if called with a large value.
     */
    @Query("""
        UPDATE qadaa_prayer
        SET totalCompleted = MIN(totalOwed, totalCompleted + :count),
            updatedAt = :now
        WHERE prayer = :prayer
    """)
    suspend fun addPrayerCompleted(prayer: String, count: Int, now: Long = System.currentTimeMillis())

    // ── Fast debt ─────────────────────────────────────────────────────────────

    /** Only groups with remaining > 0 shown in the active list. */
    @Query("SELECT * FROM qadaa_fast WHERE totalCompleted < totalOwed ORDER BY createdAt ASC")
    fun activeFastDebts(): Flow<List<FastDebtEntity>>

    @Query("SELECT * FROM qadaa_fast WHERE reason = 'RAMADAN' AND ramadanYear = :year LIMIT 1")
    suspend fun fastByRamadanYear(year: Int): FastDebtEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFast(debt: FastDebtEntity): Long

    @Query("UPDATE qadaa_fast SET totalOwed = totalOwed + :count WHERE id = :id")
    suspend fun addFastOwed(id: Long, count: Int)

    @Query("""
        UPDATE qadaa_fast
        SET totalCompleted = MIN(totalOwed, totalCompleted + :count)
        WHERE id = :id
    """)
    suspend fun addFastCompleted(id: Long, count: Int)

    // ── Log ───────────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertLog(log: QadaaLogEntity)

    @Query("""
        SELECT l.*, f.label AS fastLabel
        FROM qadaa_log l
        LEFT JOIN qadaa_fast f ON l.fastId = f.id
        ORDER BY l.completedAt DESC
    """)
    fun logsWithLabel(): Flow<List<QadaaLogWithLabel>>

    /** Total prayers made up since [fromMs] — drives the today's goal ring. */
    @Query("SELECT COALESCE(SUM(count), 0) FROM qadaa_log WHERE type = 'PRAYER' AND completedAt >= :fromMs")
    fun todayPrayersDone(fromMs: Long): Flow<Int>
}