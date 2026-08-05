package com.lhacenmed.khatmah.feature.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.RadioButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R

/**
 * A row that is one choice among several, marked by a radio button.
 *
 * The framework's own answer to a single choice is a dialog, which suits a choice made in passing.
 * The theme mode is the whole point of the screen it lives on, so the options stay laid out on it
 * and this carries the mark. Which row is [checked] is the screen's business — the rows are a set,
 * and only the screen sees all of them.
 */
class RadioPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        widgetLayoutResource = R.layout.pref_radio
    }

    var checked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Not clickable in its own right: the row owns the tap, so the button never becomes a
        // second, smaller target that does the same thing.
        (holder.findViewById(R.id.radio) as? RadioButton)?.apply {
            isChecked = checked
            isClickable = false
        }
    }
}
