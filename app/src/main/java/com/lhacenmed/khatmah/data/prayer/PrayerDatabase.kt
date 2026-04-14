package com.lhacenmed.khatmah.data.prayer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Thin [SQLiteOpenHelper] wrapper — zero annotation processing required.
 * Schema mirrors the former Room entity exactly; migration path is intact
 * via [onUpgrade].
 */
class PrayerDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) = db.execSQL(SQL_CREATE)

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    companion object {
        private const val DB_NAME    = "khatmah_prayers.db"
        private const val DB_VERSION = 1

        const val TABLE      = "prayers"
        const val COL_ID     = "id"
        const val COL_DATE   = "date"
        const val COL_NAME   = "name"
        const val COL_HOUR   = "hour"
        const val COL_MINUTE = "minute"

        private val SQL_CREATE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                $COL_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DATE   TEXT    NOT NULL,
                $COL_NAME   TEXT    NOT NULL,
                $COL_HOUR   INTEGER NOT NULL,
                $COL_MINUTE INTEGER NOT NULL,
                UNIQUE($COL_DATE, $COL_NAME)
            )
        """.trimIndent()

        @Volatile private var INSTANCE: PrayerDatabase? = null

        fun get(context: Context): PrayerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrayerDatabase(context).also { INSTANCE = it }
            }
    }
}