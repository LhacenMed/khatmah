package com.lhacenmed.khatmah.feature.khatmah.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities     = [KhatmahEntity::class, KhatmahSessionEntity::class],
    version      = 1,
    exportSchema = false,
)
abstract class KhatmahDatabase : RoomDatabase() {

    abstract fun dao(): KhatmahDao

    companion object {
        @Volatile private var INSTANCE: KhatmahDatabase? = null

        fun get(context: Context): KhatmahDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhatmahDatabase::class.java,
                    "khatmah.db",
                ).build().also { INSTANCE = it }
            }
    }
}