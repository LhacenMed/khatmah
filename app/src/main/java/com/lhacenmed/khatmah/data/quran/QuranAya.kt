package com.lhacenmed.khatmah.data.quran

/** Minimal projection of a single row from the quran table. */
data class QuranAya(
    val suraNum: Int,
    val sura:    String,
    val ayaNum:  Int,
    val aya:     String,
    val juz:     String,
)
