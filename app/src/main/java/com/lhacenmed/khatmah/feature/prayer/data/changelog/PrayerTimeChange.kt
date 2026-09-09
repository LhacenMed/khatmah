package com.lhacenmed.khatmah.feature.prayer.data.changelog

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One prayer's time being set, changed or handed back to the calculation.
 *
 * Self-describing on purpose: the time before, the time after, the time the app would have said,
 * and the whole calculation and place that produced it. A row read long after the fact still means
 * what it meant when it was written, without having to ask anything that has since moved on —
 * which is also what lets [newMinute] against [calculatedMinute] say what a mosque in a place
 * actually calls, rather than only that someone disagreed with us.
 *
 * These rows are the outbox: one exists exactly as long as the server has not taken it.
 */
@Entity(
    tableName = "prayer_time_change",
    indices   = [Index(value = ["event_id"], unique = true)],
)
data class PrayerTimeChange(
    /**
     * Local rowid, and the order the changes happened in. Sent as the server's `client_seq`, which
     * orders a device's changes when its clock cannot — a timezone move, a manually set date.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "client_seq")        val clientSeq: Long = 0,

    /** Minted here so a retry after a timed-out request lands as the same row, not a second one. */
    @ColumnInfo(name = "event_id")          val eventId: String,
    @ColumnInfo(name = "occurred_at")       val occurredAt: Long,

    val prayer:                             String,
    /** Minutes since midnight. Null [oldMinute]: it was calculated. Null [newMinute]: it is again. */
    @ColumnInfo(name = "old_minute")        val oldMinute: Int?,
    @ColumnInfo(name = "new_minute")        val newMinute: Int?,
    @ColumnInfo(name = "calculated_minute") val calculatedMinute: Int,

    val lat:                                Double,
    val lng:                                Double,
    val city:                               String,
    @ColumnInfo(name = "country_code")      val countryCode: String,
    @ColumnInfo(name = "tz_id")             val tzId: String,

    @ColumnInfo(name = "method_id")         val methodId: String,
    val juristic:                           String,
    @ColumnInfo(name = "dst_mode")          val dstMode: String,
    @ColumnInfo(name = "higher_lat")        val higherLat: String,
    val corrections:                        String,
    @ColumnInfo(name = "auto_settings")     val autoSettings: Boolean,

    val source:                             String,
    @ColumnInfo(name = "app_version")       val appVersion: String,
    @ColumnInfo(name = "os_api")            val osApi: Int,
)

@Dao
interface PrayerTimeChangeDao {

    @Insert
    suspend fun insert(changes: List<PrayerTimeChange>)

    /** The next changes to send, oldest first, so the server receives them as they happened. */
    @Query("SELECT * FROM prayer_time_change ORDER BY client_seq LIMIT :limit")
    suspend fun oldest(limit: Int): List<PrayerTimeChange>

    /** Drops the changes the server has taken. What is left is what it still owes. */
    @Query("DELETE FROM prayer_time_change WHERE client_seq IN (:clientSeqs)")
    suspend fun delete(clientSeqs: List<Long>)

    /** Drops every change still waiting — for a user who has stopped sharing them. */
    @Query("DELETE FROM prayer_time_change")
    suspend fun clear()
}
