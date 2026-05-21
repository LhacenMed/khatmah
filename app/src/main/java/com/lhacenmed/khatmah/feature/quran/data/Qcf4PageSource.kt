package com.lhacenmed.khatmah.feature.quran.data

import android.graphics.Typeface

/** Rendering contract shared by all QCF4 mushaf repositories. */
interface Qcf4PageSource {
    suspend fun pageData(pageNum: Int): Qcf4Page
    fun typefaceFor(fontName: String): Typeface
}