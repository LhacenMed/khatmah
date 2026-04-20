package com.lhacenmed.khatmah.data.adhkar

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Opens (or creates) adhkar.db in app-private storage.
 * Schema is created on first open; subsequent opens reuse the existing file.
 * Singleton — the writable handle is opened once and reused for the app lifetime.
 */
internal object AdhkarDatabase {

    private const val DB_NAME = "adhkar.db"

    @Volatile private var handle: SQLiteDatabase? = null

    fun open(context: Context): SQLiteDatabase =
        handle ?: synchronized(this) {
            handle ?: build(context.applicationContext).also { handle = it }
        }

    private fun build(ctx: Context): SQLiteDatabase {
        val dest = ctx.getDatabasePath(DB_NAME)
        dest.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(dest.absolutePath, null).also { db ->
            // setForeignKeyConstraintsEnabled is safe — PRAGMA foreign_keys=ON returns no rows.
            // enableWriteAheadLogging() is intentionally omitted: on API 24–28 it routes
            // PRAGMA journal_mode=WAL through execSQL internally, which Android rejects
            // because journal_mode returns a result set. WAL is a perf-only optimization
            // and provides no correctness benefit for a single-writer database.
            db.setForeignKeyConstraintsEnabled(true)
            createSchema(db)
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                id          TEXT    PRIMARY KEY,
                title_res   TEXT,
                title_text  TEXT,
                icon_res    TEXT,
                icon_uri    TEXT,
                color_argb  INTEGER NOT NULL,
                span        INTEGER NOT NULL DEFAULT 1,
                sort_order  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dhikr (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                category_id TEXT    NOT NULL,
                sort_order  INTEGER NOT NULL DEFAULT 0,
                repetitions INTEGER NOT NULL DEFAULT 1,
                paragraphs  TEXT    NOT NULL,
                FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_dhikr_cat ON dhikr(category_id, sort_order)"
        )
    }
}