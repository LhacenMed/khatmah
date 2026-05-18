package com.lhacenmed.khatmah.feature.mushaf.data.db

import androidx.annotation.WorkerThread
import androidx.room.*

@Dao
abstract class MushafDao {

    // ── Insert ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPage(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWords(words: List<WordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVersePages(verses: List<VersePage>)

    /** Inserts [page] and all its [words] in a single atomic transaction. */
    @Transaction
    open suspend fun insertPageWithWords(page: PageEntity, words: List<WordEntity>) {
        insertPage(page)
        insertWords(words)
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Query("SELECT font FROM mushaf_page WHERE riwaya = :riwaya AND page_num = :pageNum")
    abstract suspend fun pageFont(riwaya: String, pageNum: Int): String?

    @Query("SELECT * FROM mushaf_word WHERE riwaya = :riwaya AND page_num = :pageNum ORDER BY line_idx, word_idx")
    abstract suspend fun words(riwaya: String, pageNum: Int): List<WordEntity>

    @Query("SELECT * FROM mushaf_verse_page WHERE riwaya = :riwaya")
    abstract suspend fun versePages(riwaya: String): List<VersePage>

    /** Lightweight projection used to rebuild the verse→page index after download. */
    data class VerseKeyRow(
        @ColumnInfo(name = "verse_key") val verseKey: String,
        val sura:                                      Int,
        @ColumnInfo(name = "page_num")  val pageNum:  Int,
    )

    @Query("""
        SELECT verse_key, sura, page_num FROM mushaf_word
        WHERE riwaya = :riwaya AND verse_key IS NOT NULL AND sura IS NOT NULL
    """)
    abstract suspend fun verseKeyRows(riwaya: String): List<VerseKeyRow>

    /**
     * 1-based page numbers already stored for [riwaya].
     * Non-suspend — must be called on a background thread.
     */
    @WorkerThread
    @Query("SELECT page_num FROM mushaf_page WHERE riwaya = :riwaya")
    abstract fun existingPageNums(riwaya: String): List<Int>

    // ── Delete ────────────────────────────────────────────────────────────────

    @Query("DELETE FROM mushaf_page WHERE riwaya = :riwaya")
    abstract suspend fun clearPages(riwaya: String)

    @Query("DELETE FROM mushaf_word WHERE riwaya = :riwaya")
    abstract suspend fun clearWords(riwaya: String)

    @Query("DELETE FROM mushaf_verse_page WHERE riwaya = :riwaya")
    abstract suspend fun clearVersePages(riwaya: String)

    /** Clears all three tables for [riwaya] in one transaction. */
    @Transaction
    open suspend fun clearRiwaya(riwaya: String) {
        clearPages(riwaya)
        clearWords(riwaya)
        clearVersePages(riwaya)
    }
}