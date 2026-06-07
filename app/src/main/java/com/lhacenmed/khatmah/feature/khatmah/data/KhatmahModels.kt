package com.lhacenmed.khatmah.feature.khatmah.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "khatmah")
data class KhatmahEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startJuz: Int,
    val totalDays: Int,
    val dailyAjza: Int,
    val dailyArba: Int,
    val riwaya: String = "warsh",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
)

@Entity(
    tableName = "khatmah_session",
    foreignKeys = [ForeignKey(
        entity        = KhatmahEntity::class,
        parentColumns = ["id"],
        childColumns  = ["khatmahId"],
        onDelete      = ForeignKey.CASCADE,
    )],
    indices = [Index("khatmahId")],
)
data class KhatmahSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val khatmahId: Long,
    val dayNumber: Int,
    val startSura: Int,
    val startAya: Int,
    val endSura: Int,
    val endAya: Int,
    val startPage: Int,
    val endPage: Int,
    val isRead: Boolean = false,
)

/** Lightweight holder for a single quran_athman row during session calculation. */
data class AthmanRow(
    val startSura: Int,
    val startAya: Int,
    val endSura: Int,
    val endAya: Int,
    val endPage: Int,
)

/** UI-ready snapshot of one scheduled session for the success dialog. */
data class SessionDisplay(
    val dayNumber: Int,
    val startSuraName: String,
    val startAya: Int,
    val endSuraName: String,
    val endAya: Int,
    val startPage: Int,
    val endPage: Int,
)

/** Returned after a khatmah is persisted. */
data class KhatmahResult(
    val id: Long,
    val sessions: List<SessionDisplay>,
)