package com.lhacenmed.khatmah.feature.quran.data

import android.graphics.Typeface
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya

/** Rendering contract shared by all QCF4 mushaf repositories. */
interface Qcf4PageSource {
    val riwaya: Riwaya
    suspend fun pageData(pageNum: Int): Qcf4Page
    fun typefaceFor(fontName: String): Typeface
}