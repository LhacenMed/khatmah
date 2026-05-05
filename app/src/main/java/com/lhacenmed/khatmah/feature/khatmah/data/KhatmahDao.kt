package com.lhacenmed.khatmah.feature.khatmah.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KhatmahDao {

    @Insert
    suspend fun insertKhatmah(khatmah: KhatmahEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<KhatmahSessionEntity>)

    @Query("SELECT * FROM khatmah WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun activeKhatmah(): Flow<KhatmahEntity?>

    @Query("SELECT * FROM khatmah_session WHERE khatmahId = :khatmahId ORDER BY dayNumber")
    fun sessions(khatmahId: Long): Flow<List<KhatmahSessionEntity>>
}