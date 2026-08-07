package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R

/**
 * A [Preference] that renders a slider with a live value, ported from Quran Android's
 * SeekBarPreference. The committed value is both persisted (via the preference's key) and reported
 * through [callChangeListener] so the fragment can push it into the reader's live state; [onLiveValue]
 * additionally reports every drag, which is what lets the brightness preview follow the slider
 * rather than wait for it to be let go.
 */
class SeekBarPreference(
    context: Context,
    attrs: AttributeSet,
) : Preference(context, attrs), SeekBar.OnSeekBarChangeListener {

    /** Called on every slider movement (not just on commit) — used to drive the shared preview. */
    var onLiveValue: ((Int) -> Unit)? = null

    private var valueText: TextView? = null

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

        current = getPersistedInt(default)
        seekBar.max = maxValue
        seekBar.setOnSeekBarChangeListener(this)
        seekBar.progress = current
        onProgressChanged(seekBar, current, false)   // sync label + preview to the stored value
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        valueText?.text = progress.toString()
        current = progress
        onLiveValue?.invoke(progress)
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        persistInt(current)
        callChangeListener(current)
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
