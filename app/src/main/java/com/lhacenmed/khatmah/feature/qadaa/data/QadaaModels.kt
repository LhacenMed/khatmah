package com.lhacenmed.khatmah.feature.qadaa.data

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class Prayer(val displayName: String, @param:StringRes val nameRes: Int) {
    FAJR("Fajr",       R.string.prayer_fajr),
    DHUHR("Dhuhr",     R.string.prayer_dhuhr),
    ASR("Asr",         R.string.prayer_asr),
    MAGHRIB("Maghrib", R.string.prayer_maghrib),
    ISHA("Isha",       R.string.prayer_isha)
}

enum class FastReason(val label: String, @param:StringRes val labelRes: Int) {
    RAMADAN("Ramadan", R.string.qadaa_reason_ramadan),
    ILLNESS("Illness", R.string.qadaa_reason_illness),
    TRAVEL("Travel",   R.string.qadaa_reason_travel),
    OTHER("Other",     R.string.qadaa_reason_other)
}

enum class EstimationMethod { CONSERVATIVE, FULL }

// ── Entities ──────────────────────────────────────────────────────────────────

/**
 * One row per prayer type — pre-seeded on DB creation so UPDATE queries always
 * find a row without UPSERT complexity.
 */
@Entity(tableName = "qadaa_prayer")
data class PrayerDebtEntity(
    @PrimaryKey val prayer: String,
    val totalOwed: Int = 0,
    val totalCompleted: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One row per fast debt group (e.g. "Ramadan 1441", "Illness 2023"). */
@Entity(tableName = "qadaa_fast")
data class FastDebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reason: String,
    val ramadanYear: Int? = null,
    val label: String,
    val totalOwed: Int = 0,
    val totalCompleted: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Append-only completion log.
 *
 * - type=PRAYER + prayer=set  → single prayer entry ("Made up 2 Fajr")
 * - type=PRAYER + prayer=null → full-day entry ("Full day, 5 prayers")
 * - type=FAST  + fastId=set  → fast day entry
 *
 * [completedAt] is the streak hook — future streak computation groups rows by
 * calendar day and counts consecutive days with SUM(count) > 0.
 */
@Entity(tableName = "qadaa_log")
data class QadaaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val prayer: String? = null,
    val fastId: Long? = null,
    val count: Int,
    val note: String? = null,
    val completedAt: Long = System.currentTimeMillis(),
)

/** Log row joined with the fast group label — used by the history screen. */
data class QadaaLogWithLabel(
    @Embedded val log: QadaaLogEntity,
    @ColumnInfo(name = "fastLabel") val fastLabel: String?,
)

// ── Domain models ─────────────────────────────────────────────────────────────

data class PrayerDebt(
    val prayer: Prayer,
    val owed: Int,
    val completed: Int,
) {
    val remaining: Int get() = (owed - completed).coerceAtLeast(0)
    val progress: Float get() = if (owed == 0) 0f else (completed.toFloat() / owed).coerceIn(0f, 1f)
}

data class FastDebt(
    val id: Long,
    val reason: FastReason,
    val label: String,
    val owed: Int,
    val completed: Int,
) {
    val remaining: Int get() = (owed - completed).coerceAtLeast(0)
}

data class QadaaLogItem(
    val id: Long,
    val type: String,
    val prayer: Prayer?,
    val fastLabel: String?,
    val count: Int,
    val note: String?,
    val completedAt: Long,
)

// ── Mappers ───────────────────────────────────────────────────────────────────

fun PrayerDebtEntity.toDomain() = PrayerDebt(
    prayer    = Prayer.valueOf(prayer),
    owed      = totalOwed,
    completed = totalCompleted,
)

fun FastDebtEntity.toDomain() = FastDebt(
    id        = id,
    reason    = FastReason.valueOf(reason),
    label     = label,
    owed      = totalOwed,
    completed = totalCompleted,
)

fun QadaaLogWithLabel.toDomain() = QadaaLogItem(
    id          = log.id,
    type        = log.type,
    prayer      = log.prayer?.let { runCatching { Prayer.valueOf(it) }.getOrNull() },
    fastLabel   = fastLabel,
    count       = log.count,
    note        = log.note,
    completedAt = log.completedAt,
)