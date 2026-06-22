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

    /** One-shot variant for non-reactive callers (e.g. the reminder receiver). */
    @Query("SELECT * FROM khatmah WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeKhatmahOnce(): KhatmahEntity?

    @Query("SELECT * FROM khatmah_session WHERE khatmahId = :khatmahId AND isRead = 0 ORDER BY dayNumber ASC LIMIT 1")
    suspend fun firstUnreadOnce(khatmahId: Long): KhatmahSessionEntity?

    @Query("SELECT * FROM khatmah_session WHERE khatmahId = :khatmahId ORDER BY dayNumber")
    fun sessions(khatmahId: Long): Flow<List<KhatmahSessionEntity>>

    @Query("SELECT * FROM khatmah_session WHERE khatmahId = :khatmahId AND isRead = 0 ORDER BY dayNumber ASC LIMIT 1")
    fun firstUnread(khatmahId: Long): Flow<KhatmahSessionEntity?>

    @Query("UPDATE khatmah_session SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("SELECT COUNT(*) FROM khatmah_session WHERE khatmahId = :khatmahId AND isRead = 1")
    fun readCount(khatmahId: Long): Flow<Int>
}