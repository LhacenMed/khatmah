package com.lhacenmed.khatmah.feature.prayer.data.changelog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Holds the prayer-time changes that have not reached the server yet.
 *
 * Its own database rather than a table in the mushaf's: that one is seeded and re-downloaded, and
 * a log of what the user did should not be able to go with it.
 */
@Database(entities = [PrayerTimeChange::class], version = 1, exportSchema = false)
abstract class PrayerChangeDb : RoomDatabase() {

    abstract fun dao(): PrayerTimeChangeDao

    companion object {
        private const val DB_NAME = "prayer_changes.db"

        @Volatile private var instance: PrayerChangeDb? = null

        fun get(context: Context): PrayerChangeDb = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, PrayerChangeDb::class.java, DB_NAME)
                .build()
                .also { instance = it }
        }
    }
}
