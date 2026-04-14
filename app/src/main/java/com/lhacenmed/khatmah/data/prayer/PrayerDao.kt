package com.lhacenmed.khatmah.data.prayer

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data-access object backed directly by [PrayerDatabase].
 * All operations are dispatched to [Dispatchers.IO] — safe to call from any coroutine.
 *
 * The composite UNIQUE(date, name) index means INSERT OR REPLACE acts as an upsert,
 * keeping a full-day refresh atomic and idempotent (same contract as the former Room DAO).
 */
class PrayerDao(private val db: PrayerDatabase) {

    /** Returns all prayers for [date] in time order, or an empty list if not yet cached. */
    suspend fun getForDate(date: String): List<PrayerEntity> = withContext(Dispatchers.IO) {
        db.readableDatabase.query(
            PrayerDatabase.TABLE,
            null,
            "${PrayerDatabase.COL_DATE} = ?",
            arrayOf(date),
            null, null,
            "${PrayerDatabase.COL_HOUR}, ${PrayerDatabase.COL_MINUTE}",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PrayerEntity(
                            id     = cursor.getLong(cursor.getColumnIndexOrThrow(PrayerDatabase.COL_ID)),
                            date   = cursor.getString(cursor.getColumnIndexOrThrow(PrayerDatabase.COL_DATE)),
                            name   = cursor.getString(cursor.getColumnIndexOrThrow(PrayerDatabase.COL_NAME)),
                            hour   = cursor.getInt(cursor.getColumnIndexOrThrow(PrayerDatabase.COL_HOUR)),
                            minute = cursor.getInt(cursor.getColumnIndexOrThrow(PrayerDatabase.COL_MINUTE)),
                        )
                    )
                }
            }
        }
    }

    /**
     * Inserts or replaces a full day's prayers.
     * REPLACE handles the case where refresh re-inserts an already-cached day.
     */
    suspend fun insertAll(prayers: List<PrayerEntity>): Unit = withContext(Dispatchers.IO) {
        db.writableDatabase.run {
            beginTransaction()
            try {
                prayers.forEach { entity ->
                    insertWithOnConflict(
                        PrayerDatabase.TABLE,
                        null,
                        ContentValues().apply {
                            put(PrayerDatabase.COL_DATE,   entity.date)
                            put(PrayerDatabase.COL_NAME,   entity.name)
                            put(PrayerDatabase.COL_HOUR,   entity.hour)
                            put(PrayerDatabase.COL_MINUTE, entity.minute)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    /** Wipes the entire cache — called before a location-triggered refresh. */
    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        db.writableDatabase.delete(PrayerDatabase.TABLE, null, null)
    }
}