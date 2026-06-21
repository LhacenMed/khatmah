package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

/** Text-brightness slider — the preview text fades with the chosen alpha (Quran Android parity). */
class SeekBarTextBrightnessPreference(
    context: Context,
    attrs: AttributeSet,
) : SeekBarPreference(context, attrs) {

    override fun previewVisibility(): Int = View.VISIBLE

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        super.onProgressChanged(seekBar, progress, fromUser)
        previewText.setTextColor(Color.argb(progress, 255, 255, 255))
    }
}
