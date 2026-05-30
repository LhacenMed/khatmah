package com.lhacenmed.khatmah.feature.qadaa.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class QadaaRepository(context: Context) {

    private val dao = QadaaDatabase.get(context).dao()

    // ── Observe ───────────────────────────────────────────────────────────────

    fun prayerDebts(): Flow<List<PrayerDebt>> =
        dao.prayerDebts().map { entities ->
            entities.map(PrayerDebtEntity::toDomain).sortedBy { it.prayer.ordinal }
        }

    fun activeFastDebts(): Flow<List<FastDebt>> =
        dao.activeFastDebts().map { it.map(FastDebtEntity::toDomain) }

    fun logs(): Flow<List<QadaaLogItem>> =
        dao.logsWithLabel().map { it.map(QadaaLogWithLabel::toDomain) }

    fun todayPrayersDone(todayStartMs: Long): Flow<Int> =
        dao.todayPrayersDone(todayStartMs)

    // ── Add debt ──────────────────────────────────────────────────────────────

    /** Increments each prayer's owed total. Zero counts are skipped. */
    suspend fun addPrayers(counts: Map<Prayer, Int>) = withContext(Dispatchers.IO) {
        counts.forEach { (prayer, count) ->
            if (count > 0) dao.addPrayerOwed(prayer.name, count)
        }
    }

    /**
     * Adds [count] fast days to the matching group.
     * Ramadan groups are deduplicated by [ramadanYear] — later additions
     * go into the existing group rather than creating a duplicate.
     */
    suspend fun addFasts(
        count: Int,
        reason: FastReason,
        label: String,
        ramadanYear: Int? = null,
    ) = withContext(Dispatchers.IO) {
        val existing = if (reason == FastReason.RAMADAN && ramadanYear != null)
            dao.fastByRamadanYear(ramadanYear) else null

        if (existing != null) {
            dao.addFastOwed(existing.id, count)
        } else {
            dao.insertFast(FastDebtEntity(reason = reason.name, ramadanYear = ramadanYear, label = label, totalOwed = count))
        }
    }

    // ── Mark done ─────────────────────────────────────────────────────────────

    /**
     * Marks prayers as made up and logs the session.
     *
     * [fullDay]=true → one consolidated log entry (prayer=null) for all 5 prayers,
     * displayed in history as "Full day (N prayers)".
     * [fullDay]=false → one log entry per prayer type with count > 0.
     *
     * DB clamps completion to never exceed owed (see DAO query).
     */
    suspend fun markPrayersDone(
        counts: Map<Prayer, Int>,
        note: String? = null,
        fullDay: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (fullDay) {
            var total = 0
            Prayer.values().forEach { prayer ->
                val c = (counts[prayer] ?: 1).coerceAtLeast(0)
                if (c > 0) { dao.addPrayerCompleted(prayer.name, c); total += c }
            }
            if (total > 0) dao.insertLog(QadaaLogEntity(type = "PRAYER", count = total, note = note))
        } else {
            counts.forEach { (prayer, count) ->
                if (count > 0) {
                    dao.addPrayerCompleted(prayer.name, count)
                    dao.insertLog(QadaaLogEntity(type = "PRAYER", prayer = prayer.name, count = count, note = note))
                }
            }
        }
    }

    /** Marks [count] fast days as made up for [fastId]. */
    suspend fun markFastsDone(count: Int, fastId: Long, note: String? = null) =
        withContext(Dispatchers.IO) {
            dao.addFastCompleted(fastId, count)
            dao.insertLog(QadaaLogEntity(type = "FAST", fastId = fastId, count = count, note = note))
        }

    // ── Streak ────────────────────────────────────────────────────────────────

    /**
     * Calculates (currentStreak, longestStreak) based on calendar days where
     * the total prayers made up is >= [dailyGoal].
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun streaks(dailyGoalFlow: Flow<Int>): Flow<Pair<Int, Int>> = combine(
        dao.logsWithLabel(),
        dailyGoalFlow,
    ) { logs, dailyGoal ->
        val achievedDays = logs
            .filter { it.log.type == "PRAYER" }
            .groupBy { Instant.ofEpochMilli(it.log.completedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .filterValues { items -> items.sumOf { it.log.count } >= dailyGoal }
            .keys
            .sortedDescending() // newest to oldest

        if (achievedDays.isEmpty()) return@combine Pair(0, 0)

        // Calculate current streak
        var currentStreak = 0
        val today = LocalDate.now()
        var checkDate = today
        
        // The streak is alive if today is achieved, OR if yesterday is achieved (user still has time today).
        if (achievedDays.contains(today) || achievedDays.contains(today.minusDays(1))) {
            // Start counting backwards from the most recent possible achieved day in the streak
            checkDate = if (achievedDays.contains(today)) today else today.minusDays(1)
            while (achievedDays.contains(checkDate)) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        // Calculate longest streak
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: LocalDate? = null

        // iterate from oldest to newest (by reversing the descending list)
        for (date in achievedDays.reversed()) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                if (date == prevDate.plusDays(1)) {
                    tempStreak++
                } else {
                    tempStreak = 1
                }
            }
            if (tempStreak > longestStreak) longestStreak = tempStreak
            prevDate = date
        }

        Pair(currentStreak, longestStreak)
    }
}