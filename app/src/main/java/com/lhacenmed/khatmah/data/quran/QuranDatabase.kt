package com.lhacenmed.khatmah.data.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Opens quran.db from app-private storage, copying it from assets on first launch.
 *
 * This replicates exactly what Room's createFromAsset("databases/quran.db") does
 * internally, but skips Room's schema-validation layer — which would reject the
 * pre-packaged DB because it lacks Room's room_master_table and explicit PRIMARY KEY.
 *
 * Singleton — the read-only handle is opened once and reused for the app lifetime.
 */
internal object QuranDatabase {

    private const val DB_NAME = "quran.db"
    private const val ASSET   = "databases/quran.db"

    @Volatile private var handle: SQLiteDatabase? = null

    fun open(context: Context): SQLiteDatabase = handle ?: synchronized(this) {
        handle ?: build(context.applicationContext).also { handle = it }
    }

    private fun build(ctx: Context): SQLiteDatabase {
        val dest = ctx.getDatabasePath(DB_NAME)
        if (!dest.exists()) {
            dest.parentFile?.mkdirs()
            ctx.assets.open(ASSET).use { src ->
                dest.outputStream().use(src::copyTo)
            }
        }
        return SQLiteDatabase.openDatabase(dest.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }
}
