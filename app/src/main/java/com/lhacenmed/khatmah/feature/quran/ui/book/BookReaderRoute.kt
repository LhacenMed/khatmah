package com.lhacenmed.khatmah.feature.quran.ui.book

import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint

/** True when the selected print renders via QCF4 fonts — i.e. the native book reader. */
val MushafPrint.isQcf4: Boolean
    get() = this == MushafPrint.HafsQcf4 || this == MushafPrint.WarshQcf4

/**
 * The reader destination for the currently selected print: the native [Dest.BookReader]
 * for QCF4 prints, otherwise the existing Compose [Dest.QuranReader]. Single source of
 * truth so every reading entry point routes consistently.
 *
 * [suraNum] (1-based) targets a surah; 0 resumes the last-read page. Surah targeting is
 * honoured by the Compose reader today; the book reader resumes the last page until its
 * surah-jump lands, so QCF4 + a surah target still opens cleanly at the saved page.
 */
fun currentReaderDest(suraNum: Int = 0): Dest =
    if (MushafPrefs.selected.value.isQcf4) Dest.BookReader()
    else Dest.QuranReader(suraNum = suraNum)

/**
 * The reader destination for a single Khatmah session ([startPage]..[endPage], 1-based
 * inclusive; [sessionId] keys its remembered progress): the native [Dest.BookReader] windowed to
 * those pages for QCF4 prints, otherwise the Compose [Dest.QuranSessionReader]. Mirrors
 * [currentReaderDest] so the session card and the continue-reading entry point route by the same
 * QCF4 rule.
 */
fun sessionReaderDest(sessionId: Long, startPage: Int, endPage: Int): Dest =
    if (MushafPrefs.selected.value.isQcf4)
        Dest.BookReader(startPage = startPage, endPage = endPage, sessionId = sessionId)
    else Dest.QuranSessionReader(startPage, endPage)
