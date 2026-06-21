package com.lhacenmed.khatmah.feature.quran.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities     = [
        PageEntity::class, WordEntity::class, VersePage::class,
        SurahEntity::class, DivisionEntity::class, SajdaEntity::class,
        PageStartEntity::class, VerseEntity::class, BookmarkEntity::class,
    ],
    version      = 4,
    exportSchema = false,
)
abstract class MushafDb : RoomDatabase() {

    abstract fun dao(): MushafDao

    companion object {
        private const val DB_NAME = "mushaf.db"

        /** v3→v4: adds the bookmarks table, preserving the seeded text + downloaded QCF4 data. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS mushaf_bookmark (" +
                        "riwaya TEXT NOT NULL, page_num INTEGER NOT NULL, created_at INTEGER NOT NULL, " +
                        "PRIMARY KEY(riwaya, page_num))"
                )
            }
        }

        @Volatile private var instance: MushafDb? = null

        fun get(context: Context): MushafDb = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, MushafDb::class.java, DB_NAME)
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
