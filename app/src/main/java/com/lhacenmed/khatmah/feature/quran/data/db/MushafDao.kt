package com.lhacenmed.khatmah.feature.quran.data.db

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSurahs(surahs: List<SurahEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDivisions(divisions: List<DivisionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSajdaat(sajdaat: List<SajdaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPageStarts(starts: List<PageStartEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertVerses(verses: List<VerseEntity>)

    /** Inserts [page] and all its [words] in a single atomic transaction. */
    @Transaction
    open suspend fun insertPageWithWords(page: PageEntity, words: List<WordEntity>) {
        insertPage(page)
        insertWords(words)
    }

    /**
     * Inserts all riwaya data from the bundled JSON atomically.
     * Called once by [MushafInitializer] on first launch per riwaya.
     */
    @Transaction
    open suspend fun insertRiwayaData(
        surahs:     List<SurahEntity>,
        divisions:  List<DivisionEntity>,
        sajdaat:    List<SajdaEntity>,
        pageStarts: List<PageStartEntity>,
        verses:     List<VerseEntity>,
    ) {
        insertSurahs(surahs)
        insertDivisions(divisions)
        insertSajdaat(sajdaat)
        insertPageStarts(pageStarts)
        insertVerses(verses)
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Query("SELECT font FROM mushaf_page WHERE riwaya = :riwaya AND page_num = :pageNum")
    abstract suspend fun pageFont(riwaya: String, pageNum: Int): String?

    @Query("SELECT * FROM mushaf_word WHERE riwaya = :riwaya AND page_num = :pageNum ORDER BY line_idx, word_idx")
    abstract suspend fun words(riwaya: String, pageNum: Int): List<WordEntity>

    @Query("SELECT * FROM mushaf_verse_page WHERE riwaya = :riwaya")
    abstract suspend fun versePages(riwaya: String): List<VersePage>

    @Query("SELECT * FROM mushaf_surah WHERE riwaya = :riwaya ORDER BY num ASC")
    abstract suspend fun surahs(riwaya: String): List<SurahEntity>

    @Query("SELECT * FROM mushaf_division WHERE riwaya = :riwaya AND type = :type ORDER BY num ASC")
    abstract suspend fun divisions(riwaya: String, type: String): List<DivisionEntity>

    @Query("SELECT * FROM mushaf_sajda WHERE riwaya = :riwaya ORDER BY num")
    abstract suspend fun sajdaat(riwaya: String): List<SajdaEntity>

    @Query("SELECT * FROM mushaf_page_start WHERE riwaya = :riwaya ORDER BY page_num")
    abstract suspend fun allPageStarts(riwaya: String): List<PageStartEntity>

    @Query("SELECT * FROM mushaf_verse WHERE riwaya = :riwaya ORDER BY sura, aya")
    abstract suspend fun verses(riwaya: String): List<VerseEntity>

    @Query("SELECT COUNT(*) FROM mushaf_verse WHERE riwaya = :riwaya")
    abstract suspend fun verseCount(riwaya: String): Int

    @Query("SELECT * FROM mushaf_division WHERE riwaya = :riwaya AND type = :type AND num = :num LIMIT 1")
    abstract suspend fun division(riwaya: String, type: String, num: Int): DivisionEntity?

    @Query("""
        SELECT * FROM mushaf_division 
        WHERE riwaya = :riwaya AND type = :type 
        AND (sura < :sura OR (sura = :sura AND aya <= :aya)) 
        ORDER BY sura DESC, aya DESC LIMIT 1
    """)
    abstract suspend fun divisionForVerse(riwaya: String, type: String, sura: Int, aya: Int): DivisionEntity?

    @Query("""
        SELECT page_num FROM mushaf_page_start 
        WHERE riwaya = :riwaya 
        AND (sura < :sura OR (sura = :sura AND aya <= :aya)) 
        ORDER BY sura DESC, aya DESC LIMIT 1
    """)
    abstract suspend fun pageForVerse(riwaya: String, sura: Int, aya: Int): Int?

    @Query("SELECT * FROM mushaf_verse WHERE riwaya = :riwaya AND sura = :sura AND aya = :aya LIMIT 1")
    abstract suspend fun verse(riwaya: String, sura: Int, aya: Int): VerseEntity?

    @Query("SELECT name FROM mushaf_surah WHERE riwaya = :riwaya AND num = :num LIMIT 1")
    abstract suspend fun surahName(riwaya: String, num: Int): String?

    @Query("""
        SELECT * FROM mushaf_verse 
        WHERE riwaya = :riwaya 
        AND (sura < :sura OR (sura = :sura AND aya < :aya)) 
        ORDER BY sura DESC, aya DESC LIMIT 1
    """)
    abstract suspend fun verseBefore(riwaya: String, sura: Int, aya: Int): VerseEntity?

    @Query("SELECT * FROM mushaf_verse WHERE riwaya = :riwaya ORDER BY sura DESC, aya DESC LIMIT 1")
    abstract suspend fun lastVerse(riwaya: String): VerseEntity?

    @Query("SELECT sura, aya FROM mushaf_verse WHERE riwaya = :riwaya ORDER BY sura, aya")
    abstract suspend fun verseIdentities(riwaya: String): List<VerseIdentity>

    data class VerseIdentity(val sura: Int, val aya: Int)

    /** Lightweight projection used to rebuild the verse→page index after QCF4 download. */
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

    @Query("DELETE FROM mushaf_page WHERE riwaya = :riwaya")
    abstract suspend fun clearPages(riwaya: String)

    @Query("DELETE FROM mushaf_word WHERE riwaya = :riwaya")
    abstract suspend fun clearWords(riwaya: String)

    @Query("DELETE FROM mushaf_verse_page WHERE riwaya = :riwaya")
    abstract suspend fun clearVersePages(riwaya: String)

    @Query("DELETE FROM mushaf_surah WHERE riwaya = :riwaya")
    abstract suspend fun clearSurahs(riwaya: String)

    @Query("DELETE FROM mushaf_division WHERE riwaya = :riwaya")
    abstract suspend fun clearDivisions(riwaya: String)

    @Query("DELETE FROM mushaf_sajda WHERE riwaya = :riwaya")
    abstract suspend fun clearSajdaat(riwaya: String)

    @Query("DELETE FROM mushaf_page_start WHERE riwaya = :riwaya")
    abstract suspend fun clearPageStarts(riwaya: String)

    @Query("DELETE FROM mushaf_verse WHERE riwaya = :riwaya")
    abstract suspend fun clearVerses(riwaya: String)

    /** Clears all tables for [riwaya] in one transaction. */
    @Transaction
    open suspend fun clearRiwaya(riwaya: String) {
        clearPages(riwaya)
        clearWords(riwaya)
        clearVersePages(riwaya)
        clearSurahs(riwaya)
        clearDivisions(riwaya)
        clearSajdaat(riwaya)
        clearPageStarts(riwaya)
        clearVerses(riwaya)
    }
}