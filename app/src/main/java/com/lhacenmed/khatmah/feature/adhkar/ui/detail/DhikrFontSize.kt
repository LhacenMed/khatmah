package com.lhacenmed.khatmah.feature.adhkar.ui.detail

/**
 * Three-step reading font size that cycles on each tap of the toolbar's resize action.
 * [bodyScale] scales general text; [quranScale] scales Quranic verse text, which starts
 * slightly larger to preserve the traditional Uthmanic appearance.
 */
enum class DhikrFontSize(val bodyScale: Float, val quranScale: Float) {
    SMALL(0.82f, 0.88f),
    MEDIUM(1f,   1f),
    LARGE(1.22f, 1.16f);

    fun next() = when (this) {
        SMALL  -> MEDIUM
        MEDIUM -> LARGE
        LARGE  -> SMALL
    }
}
