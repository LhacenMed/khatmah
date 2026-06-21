package com.lhacenmed.khatmah.feature.quran.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Page-level metadata; one row per mushaf page. */
@Entity(tableName = "mushaf_page", primaryKeys = ["riwaya", "page_num"])
data class PageEntity(
    val riwaya:                                 String,
    @ColumnInfo(name = "page_num") val pageNum: Int,
    val font:                                   String,
)

/**
 * One rendered word on a mushaf page.
 *
 * [lineIdx] — 0-based ordering index within the page (sort key).
 * [lineNum] — raw line number as provided by the source data.
 */
@Entity(tableName = "mushaf_word", primaryKeys = ["riwaya", "page_num", "line_idx", "word_idx"])
data class WordEntity(
    val riwaya:                                    String,
    @ColumnInfo(name = "page_num")  val pageNum:   Int,
    @ColumnInfo(name = "line_idx")  val lineIdx:   Int,
    @ColumnInfo(name = "line_num")  val lineNum:   Int,
    @ColumnInfo(name = "word_idx")  val wordIdx:   Int,
    val char:                                      String,
    val font:                                      String,
    val text:                                      String,
    val type:                                      String,
    @ColumnInfo(name = "verse_key") val verseKey:  String?,
    val sura:                                      Int?,
    val position:                                  Int?,
)

/** Maps one aya (sura + aya number) to its first mushaf page. */
@Entity(tableName = "mushaf_verse_page", primaryKeys = ["riwaya", "sura", "aya"])
data class VersePage(
    val riwaya:                                  String,
    val sura:                                    Int,
    val aya:                                     Int,
    @ColumnInfo(name = "page_num") val pageNum:  Int,
)

/** Surah metadata. [ayat] count is riwaya-specific (e.g. Al-Baqarah: 285 Warsh / 286 Hafs). */
@Entity(tableName = "mushaf_surah", primaryKeys = ["riwaya", "num"])
data class SurahEntity(
    val riwaya:                                          String,
    val num:                                             Int,
    val name:                                            String,
    @ColumnInfo(name = "name_en")          val nameEn:  String,
    @ColumnInfo(name = "name_complex")     val nameComplex: String,
    @ColumnInfo(name = "translated_name")  val translatedName: String,
    val type:                                            String,   // "makki" | "madani"
    @ColumnInfo(name = "revelation_order") val revelationOrder: Int,
    val ayat:                                            Int,
    @ColumnInfo(name = "bismillah_pre")    val bismillahPre: Boolean,
)

/**
 * Quranic division marker — one row per juz' / rub' al-hizb / thumn al-hizb start.
 * Use [DivType] constants for [type].
 * Hizb is not stored: hizb_num = ceil(rub_num / 4).
 */
@Entity(tableName = "mushaf_division", primaryKeys = ["riwaya", "type", "num"])
data class DivisionEntity(
    val riwaya: String,
    val type:   String,   // "juz" | "rub" | "thumn"
    val num:    Int,
    val sura:   Int,
    val aya:    Int,
)

/** Sajda (prostration) aya marker. */
@Entity(tableName = "mushaf_sajda", primaryKeys = ["riwaya", "num"])
data class SajdaEntity(
    val riwaya:     String,
    val num:        Int,
    val sura:       Int,
    val aya:        Int,
    val obligatory: Boolean,
)

/** Maps a 1-based page number to the sura:aya that begins that page. */
@Entity(tableName = "mushaf_page_start", primaryKeys = ["riwaya", "page_num"])
data class PageStartEntity(
    val riwaya:                                  String,
    @ColumnInfo(name = "page_num") val pageNum:  Int,
    val sura:                                    Int,
    val aya:                                     Int,
)

/**
 * One verse of the Quran for a given riwaya.
 * [normalized] is pre-computed for fast in-memory search (diacritics stripped, alef variants unified).
 */
@Entity(tableName = "mushaf_verse", primaryKeys = ["riwaya", "sura", "aya"])
data class VerseEntity(
    val riwaya:     String,
    val sura:       Int,
    val aya:        Int,
    val text:       String,
    val normalized: String,
)