package com.lhacenmed.khatmah.feature.quran.ui.reader

/**
 * Eastern-Arabic-Indic numerals (٠١٢٣…) for the reader feature. Owned here so the reader is
 * self-contained — nothing borrows numeral formatting from elsewhere.
 */
internal fun toArNums(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
