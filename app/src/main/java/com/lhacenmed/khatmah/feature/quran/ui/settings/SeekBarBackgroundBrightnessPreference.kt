package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

/** Background-brightness slider — the preview square takes the chosen grey (Quran Android parity). */
class SeekBarBackgroundBrightnessPreference(
    context: Context,
    attrs: AttributeSet,
) : SeekBarPreference(context, attrs) {

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        super.onProgressChanged(seekBar, progress, fromUser)
        previewBox.visibility = View.VISIBLE
        previewBox.setBackgroundColor(Color.argb(255, progress, progress, progress))
    }
}
