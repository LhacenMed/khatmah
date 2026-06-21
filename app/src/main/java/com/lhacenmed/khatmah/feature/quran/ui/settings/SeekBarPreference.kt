package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R

/**
 * A [Preference] that renders a slider with a live value, ported from Quran Android's
 * SeekBarPreference. The reading-brightness subclasses add an inline preview. The committed value
 * is both persisted (via the preference's key) and reported through [callChangeListener] so the
 * fragment can push it into the reader's live state.
 */
open class SeekBarPreference(
    context: Context,
    attrs: AttributeSet,
) : Preference(context, attrs), SeekBar.OnSeekBarChangeListener {

    protected lateinit var previewText: TextView
    protected lateinit var previewBox: View
    private lateinit var valueText: TextView

    private val maxValue = attrs.getAttributeIntValue(ANDROID_NS, "max", 100)
    private val default  = attrs.getAttributeIntValue(ANDROID_NS, "defaultValue", 0)
    private var current  = 0

    init {
        layoutResource = R.layout.seekbar_pref
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.seekbar) as SeekBar
        valueText   = holder.findViewById(R.id.value) as TextView
        previewText = holder.findViewById(R.id.pref_preview) as TextView
        previewBox  = holder.findViewById(R.id.preview_square)

        previewText.visibility = previewVisibility()
        current = getPersistedInt(default)
        seekBar.max = maxValue
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.progress = current
        onProgressChanged(seekBar, current, false)   // sync label + preview to the stored value
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        valueText.text = progress.toString()
        current = progress
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        persistInt(current)
        callChangeListener(current)
    }

    /** Visibility of the "preview" text row under the slider. */
    protected open fun previewVisibility(): Int = View.GONE

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
