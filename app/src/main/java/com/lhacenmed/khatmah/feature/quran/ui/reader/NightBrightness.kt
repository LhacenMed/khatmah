package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.graphics.Color
import kotlin.math.ln1p

/**
 * Turns the reader's two night-brightness sliders into the colours a page is painted with.
 *
 * The text alpha is not the text slider on its own: a lighter background swallows dim text, so the
 * background brightness lifts the alpha along with it — which is why text at zero is still faintly
 * legible on a grey page rather than gone. The lift is logarithmic, so the first few steps off pure
 * black do most of the work and the top of the range stays gentle.
 *
 * The book reader, the text reader and the settings preview all derive their colours here, so what
 * the preview shows cannot drift from what the reader actually draws.
 */
object NightBrightness {

    /** How far the background brightness lifts the text alpha. */
    private const val BG_LIFT = 50f

    /** Night text alpha for a slider pair, 0..255. */
    fun textAlpha(text: Int, background: Int): Int =
        (BG_LIFT * ln1p(background.toDouble()).toFloat() + text).toInt().coerceAtMost(255)

    /** Night text colour: white at [textAlpha]. */
    fun textArgb(text: Int, background: Int): Int =
        Color.argb(textAlpha(text, background), 255, 255, 255)

    /** Night page background: a solid grey at the background brightness. */
    fun backgroundArgb(background: Int): Int = Color.rgb(background, background, background)
}
