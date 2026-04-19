package com.lhacenmed.khatmah.ui.page.quran

import com.lhacenmed.khatmah.data.quran.QuranAya

// ── Item types ────────────────────────────────────────────────────────────────

sealed class QuranPageItem {
    /**
     * "بِسْمِ اِ۬للَّهِ اِ۬لرَّحْمَٰنِ اِ۬لرَّحِيمِ" injected before each sura.
     * Carries [suraNum] so keys are unique across the whole item list.
     */
    data class Basmala(val suraNum: Int)                          : QuranPageItem()
    data class SuraHeader(val num: Int, val name: String)         : QuranPageItem()
    data class Aya(val suraNum: Int, val ayaNum: Int, val text: String) : QuranPageItem()
}

// ── Page ──────────────────────────────────────────────────────────────────────

data class QuranPage(
    val number: Int,                    // 1-based display number
    val items:  List<QuranPageItem>,
)

// ── Builder ───────────────────────────────────────────────────────────────────

/** Number of Aya items per page; Basmala / SuraHeader entries don't count. */
private const val AYAS_PER_PAGE = 12

/**
 * Converts the flat aya list into display-ready pages.
 *
 * Layout rules injected at every sura boundary:
 *  - [QuranPageItem.Basmala]    — all suras except Al-Fatiha (1) and At-Tawbah (9).
 *  - [QuranPageItem.SuraHeader] — every sura, always.
 *
 * Paging: the buffer flushes once [AYAS_PER_PAGE] Aya items accumulate;
 * headers ride along for free and never trigger an early flush.
 *
 * Note: Sura 1 aya 1 IS the Basmala in the DB, so no duplicate header is added.
 * Sura 9 has no Basmala per Quranic tradition.
 */
fun buildQuranPages(ayas: List<QuranAya>): List<QuranPage> {
    if (ayas.isEmpty()) return emptyList()

    // Step 1: build a flat, ordered display list with injected headers
    val flat = ArrayList<QuranPageItem>(ayas.size + 250)
    var lastSura = -1
    for (q in ayas) {
        if (q.suraNum != lastSura) {
            if (q.suraNum != 1 && q.suraNum != 9) flat += QuranPageItem.Basmala(q.suraNum)
            flat += QuranPageItem.SuraHeader(q.suraNum, q.sura)
            lastSura = q.suraNum
        }
        flat += QuranPageItem.Aya(q.suraNum, q.ayaNum, q.aya)
    }

    // Step 2: chunk into pages (only Aya items count toward the limit)
    return buildList {
        val buf      = mutableListOf<QuranPageItem>()
        var ayaCount = 0
        for (item in flat) {
            buf += item
            if (item is QuranPageItem.Aya && ++ayaCount >= AYAS_PER_PAGE) {
                add(QuranPage(size + 1, buf.toList()))
                buf.clear()
                ayaCount = 0
            }
        }
        if (buf.isNotEmpty()) add(QuranPage(size + 1, buf.toList()))
    }
}