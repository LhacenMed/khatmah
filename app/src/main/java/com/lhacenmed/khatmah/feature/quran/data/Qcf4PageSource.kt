package com.lhacenmed.khatmah.feature.quran.data

import android.graphics.Typeface
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya

/** Rendering contract shared by all QCF4 mushaf repositories. */
interface Qcf4PageSource {
    val riwaya: Riwaya
    suspend fun pageData(pageNum: Int): Qcf4Page
    fun typefaceFor(fontName: String): Typeface

    /**
     * Maps every aya to its 0-based page index in *this* mushaf's pagination, keyed by
     * `(sura.toLong() shl 32) or aya.toLong()`. The QCF4 layout can differ from the generic
     * per-riwaya page starts, so this is the source of truth for "which page is this aya on".
     */
    suspend fun ayaPageIndex(): Map<Long, Int>
}