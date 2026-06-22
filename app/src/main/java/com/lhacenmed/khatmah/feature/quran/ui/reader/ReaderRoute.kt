package com.lhacenmed.khatmah.feature.quran.ui.reader

import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.feature.quran.data.MushafFormat
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs

/** True when the selected print renders via QCF4 fonts (the downloadable book reader). */
val MushafPrint.isQcf4: Boolean get() = format == MushafFormat.QCF4

/**
 * The reader destination for "continue reading": the one native [Dest.Reader]. [suraNum] (1-based)
 * targets a surah; 0 resumes the last-read page. Both modes honour the surah target — the QCF4 book
 * reader maps it through its own pagination, the text reader through its built pages.
 */
fun currentReaderDest(suraNum: Int = 0): Dest = Dest.Reader(suraNum = suraNum)

/**
 * Reader destination that opens at a specific location. QCF4 opens at the exact [page]; the text
 * reader (different pagination) opens at [suraNum]/[ayaNum] instead. Index entry points (a surah, or
 * a juz' that starts mid-surah) pass both so either mode lands in the same place.
 */
fun readerDestAt(page: Int, suraNum: Int, ayaNum: Int = 1): Dest =
    if (MushafPrefs.selected.value.isQcf4) Dest.Reader(page = page)
    else Dest.Reader(suraNum = suraNum, ayaNum = ayaNum)

/**
 * The reader destination for a single Khatmah session ([startPage]..[endPage], 1-based inclusive;
 * [sessionId] keys its remembered progress). Sessions are page-windowed, so only the QCF4 book
 * reader honours them — callers gate text prints to a download prompt before reaching here.
 */
fun sessionReaderDest(sessionId: Long, startPage: Int, endPage: Int): Dest =
    Dest.Reader(startPage = startPage, endPage = endPage, sessionId = sessionId)
