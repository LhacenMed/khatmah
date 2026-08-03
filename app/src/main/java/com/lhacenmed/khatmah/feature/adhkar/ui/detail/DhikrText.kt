package com.lhacenmed.khatmah.feature.adhkar.ui.detail

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import com.lhacenmed.khatmah.R

/**
 * Maps [count] to a human-readable repetition string drawn from string resources, so Arabic
 * and English values are resolved by the active locale automatically.
 */
fun repetitionsLabel(context: Context, count: Int): String = context.getString(
    when (count) {
        1                     -> R.string.rep_once
        2                     -> R.string.rep_twice
        3                     -> R.string.rep_three
        7                     -> R.string.rep_seven
        10                    -> R.string.rep_ten
        33                    -> R.string.rep_thirty_three
        100                   -> R.string.rep_hundred
        in 11..99             -> R.string.rep_n_times_mid  // singular accusative (AR: مرةً)
        in 101..Int.MAX_VALUE -> R.string.rep_n_times_high // singular genitive  (AR: مرةٍ)
        else                  -> R.string.rep_n_times      // 4–10 plural        (AR: مرات)
    },
    count,
)

/**
 * Regex matching aya number tokens embedded in Quranic text:
 *  • Circled Unicode digits  ①–⑳  (U+2460–U+2473)
 *  • ASCII digit sequences   123
 *  • Arabic-Indic sequences  ٢٥٣
 */
private val ayaNumberRegex = Regex("[①-⑳]|[0-9٠-٩]+")

/**
 * Tints the aya number tokens inside a Quranic verse with [numberColor] at a fixed size, so
 * they read as separators rather than part of the verse. The verse itself keeps the
 * TextView's own colour and size.
 */
fun quranSpanned(text: String, @ColorInt numberColor: Int, numberSizePx: Int): CharSequence =
    SpannableString(text).apply {
        ayaNumberRegex.findAll(text).forEach { match ->
            val start = match.range.first
            val end   = match.range.last + 1
            setSpan(ForegroundColorSpan(numberColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(numberSizePx), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
