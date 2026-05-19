package com.lhacenmed.khatmah.feature.qadaa.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities     = [PrayerDebtEntity::class, FastDebtEntity::class, QadaaLogEntity::class],
    version      = 1,
    exportSchema = false,
)
abstract class QadaaDatabase : RoomDatabase() {

    abstract fun dao(): QadaaDao

    companion object {
        @Volatile private var INSTANCE: QadaaDatabase? = null

        fun get(context: Context): QadaaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QadaaDatabase::class.java,
                    "qadaa.db",
                )
                    .addCallback(object : Callback() {
                        /** Pre-seed one row per prayer so UPDATE queries always find a row. */
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val now = System.currentTimeMillis()
                            Prayer.values().forEach { p ->
                                db.execSQL(
                                    "INSERT INTO qadaa_prayer (prayer, totalOwed, totalCompleted, updatedAt) " +
                                            "VALUES ('${p.name}', 0, 0, $now)"
                                )
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}