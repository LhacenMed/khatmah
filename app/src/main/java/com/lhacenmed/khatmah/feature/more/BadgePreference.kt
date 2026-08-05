package com.lhacenmed.khatmah.feature.more

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R

/**
 * A preference row that carries a count in its widget slot — the wird counters in the More tab.
 *
 * Zero is shown like any other count: the pill is how the row reports where the khatmah stands,
 * and "none left" is an answer worth reading, not an absence worth hiding.
 */
class BadgePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        widgetLayoutResource = R.layout.pref_badge
    }

    var count: Int = 0
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.badge) as? TextView)?.text = count.toString()
    }
}
