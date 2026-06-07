package com.lhacenmed.khatmah.feature.mushaf.data

import androidx.annotation.StringRes
import com.lhacenmed.khatmah.R

enum class Riwaya(@StringRes val nameRes: Int) {
    HAFS(R.string.riwaya_hafs),
    WARSH(R.string.riwaya_warsh);

    /** Key used in [MushafDb] tables — matches the riwaya field in bundled JSON files. */
    val dbKey: String get() = name.lowercase()  // "hafs" | "warsh"
}