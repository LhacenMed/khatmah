package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.ui.reader.NightBrightness
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderPrefs

/**
 * The night-brightness preview, as a row of its own beneath the two sliders it answers to.
 *
 * Colours come from [NightBrightness] — the same source the book and text readers paint from — so
 * the swatch is not an approximation of the reader but the reader's own arithmetic on a sample aya.
 *
 * Each slider writes its value here as it moves and the swatch repaints; the row is not selectable,
 * because there is nothing to press. Values are kept on the preference rather than in the view, so
 * scrolling the row off screen and back redraws it exactly as it was.
 */
class AyaPreviewPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    private var view: AyaPreviewView? = null
    private var sample: List<PreviewRun> = emptyList()

    /** Text-brightness slider value. */
    var textBrightness: Int = ReaderPrefs.DEFAULT_TEXT_BRIGHTNESS
        set(value) { if (field != value) { field = value; render() } }

    /** Background-brightness slider value. */
    var backgroundBrightness: Int = ReaderPrefs.DEFAULT_BG_BRIGHTNESS
        set(value) { if (field != value) { field = value; render() } }

    init {
        layoutResource = R.layout.pref_aya_preview
        isSelectable = false
    }

    /** The resolved preview aya; shown on the next bind if the row isn't laid out yet. */
    fun setSample(runs: List<PreviewRun>) {
        sample = runs
        view?.setRuns(runs)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // A disabled slider greys itself; a swatch has no disabled state of its own, so the row
        // fades with the sliders it belongs to rather than staying lit beside them.
        holder.itemView.alpha = if (isEnabled) 1f else DISABLED_ALPHA
        view = holder.findViewById(R.id.preview) as AyaPreviewView
        view?.setRuns(sample)
        render()
    }

    private fun render() {
        view?.apply {
            textArgb = NightBrightness.textArgb(textBrightness, backgroundBrightness)
            setBackgroundColor(NightBrightness.backgroundArgb(backgroundBrightness))
        }
    }

    private companion object {
        /** Material's disabled-content opacity. */
        const val DISABLED_ALPHA = 0.38f
    }
}
