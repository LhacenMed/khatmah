package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import androidx.preference.PreferenceViewHolder

/**
 * Background-brightness slider, which also hosts the *combined* night-mode preview: the sample text
 * is drawn at the text-brightness alpha over the chosen background grey, so both sliders are judged
 * against each other in a single swatch. The text slider feeds its live value in via
 * [setPreviewTextAlpha].
 */
class SeekBarBackgroundBrightnessPreference(
    context: Context,
    attrs: AttributeSet,
) : SeekBarPreference(context, attrs) {

    private var textAlpha = DEFAULT_TEXT_BRIGHTNESS
    private var bgGrey = 0

    override fun previewVisibility(): Int = View.VISIBLE

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        // Pick up the stored text brightness so the preview is correct however the rows bind.
        textAlpha = sharedPreferences?.getInt(TEXT_BRIGHTNESS_KEY, DEFAULT_TEXT_BRIGHTNESS)
            ?: DEFAULT_TEXT_BRIGHTNESS
        super.onBindViewHolder(holder)
    }

    /** Live text-brightness value from the text slider. */
    fun setPreviewTextAlpha(alpha: Int) {
        textAlpha = alpha
        renderPreview()
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        super.onProgressChanged(seekBar, progress, fromUser)
        bgGrey = progress
        renderPreview()
    }

    private fun renderPreview() {
        previewText?.apply {
            setTextColor(Color.argb(textAlpha, 255, 255, 255))
            setBackgroundColor(Color.rgb(bgGrey, bgGrey, bgGrey))
        }
    }

    private companion object {
        const val TEXT_BRIGHTNESS_KEY = "text_brightness"
        const val DEFAULT_TEXT_BRIGHTNESS = 180
    }
}
