package com.lhacenmed.khatmah.feature.khatmah.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities     = [KhatmahEntity::class, KhatmahSessionEntity::class],
    version      = 2,
    exportSchema = false,
)
abstract class KhatmahDatabase : RoomDatabase() {

    abstract fun dao(): KhatmahDao

    companion object {
        @Volatile private var INSTANCE: KhatmahDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing rows default to "warsh" — the original only-supported riwaya.
                database.execSQL(
                    "ALTER TABLE khatmah ADD COLUMN riwaya TEXT NOT NULL DEFAULT 'warsh'"
                )
            }
        }

        fun get(context: Context): KhatmahDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KhatmahDatabase::class.java,
                    "khatmah.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}