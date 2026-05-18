package com.lhacenmed.khatmah.feature.mushaf.data.db

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