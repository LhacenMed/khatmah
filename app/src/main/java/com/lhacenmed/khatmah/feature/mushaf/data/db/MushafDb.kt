package com.lhacenmed.khatmah.feature.mushaf.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities     = [
        PageEntity::class, WordEntity::class, VersePage::class,
        SurahEntity::class, DivisionEntity::class, SajdaEntity::class,
        PageStartEntity::class, VerseEntity::class,
    ],
    version      = 3,
    exportSchema = false,
)
abstract class MushafDb : RoomDatabase() {

    abstract fun dao(): MushafDao

    companion object {
        private const val DB_NAME = "mushaf.db"

        @Volatile private var instance: MushafDb? = null

        fun get(context: Context): MushafDb = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, MushafDb::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}