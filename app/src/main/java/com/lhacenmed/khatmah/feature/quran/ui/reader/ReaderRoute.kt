package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.feature.quran.data.MushafFormat
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.RiwayaConfig
import com.lhacenmed.khatmah.shared.reminders.SunnahSurah

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
 *
 * The verse rides along in both modes even though QCF4 navigates by page, because [highlight] needs
 * something to mark. The reader reads the page first, so carrying it changes nothing about where
 * either mode opens.
 */
fun readerDestAt(page: Int, suraNum: Int, ayaNum: Int = 1, highlight: Boolean = false): Dest =
    if (MushafPrefs.selected.value.isQcf4)
        Dest.Reader(page = page, suraNum = suraNum, ayaNum = ayaNum, highlight = highlight)
    else Dest.Reader(suraNum = suraNum, ayaNum = ayaNum, highlight = highlight)

/**
 * The reader destination for a single Khatmah session ([startPage]..[endPage], 1-based inclusive;
 * [sessionId] keys its remembered progress). Sessions are page-windowed, so only the QCF4 book
 * reader honours them — callers gate text prints to a download prompt before reaching here.
 */
fun sessionReaderDest(sessionId: Long, startPage: Int, endPage: Int): Dest =
    Dest.Reader(startPage = startPage, endPage = endPage, sessionId = sessionId)

/**
 * The reader destination for a sunnah surah, windowed to the pages it occupies in the selected
 * print — or null when that print cannot show a window, which is the caller's cue to offer the
 * QCF4 download.
 *
 * The range is resolved here rather than remembered, because a surah falls on different pages in
 * different riwayas: asking again is what lets the row on the More tab, its reminder, and carrying
 * on reading all land on the right pages whichever print is selected at the time.
 *
 * The negative session id keeps a per-surah reading position that can never collide with a
 * khatmah's.
 */
suspend fun sunnahReaderDest(context: Context, surah: SunnahSurah): Dest? {
    val print = MushafPrefs.selected.value
    if (!print.isQcf4) return null
    val ayaCount = RiwayaConfig.of(print.riwaya).ayaCount(surah.number)
    val range = QuranTextRepository(context)
        .pageRangeForSurah(print.riwaya.dbKey, surah.number, ayaCount) ?: return null
    return sessionReaderDest(-surah.number.toLong(), range.first, range.last)
}
