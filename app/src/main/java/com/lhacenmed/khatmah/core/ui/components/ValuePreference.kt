package com.lhacenmed.khatmah.core.ui.components

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R

/**
 * Fills the value slot `pref_row` keeps opposite the title, and folds it away when there is none —
 * an empty slot would still hold its margin open on every row that has no value to show.
 */
private fun PreferenceViewHolder.bindValue(value: CharSequence?) {
    val view = findViewById(R.id.value) as? TextView ?: return
    view.text = value
    view.isVisible = !value.isNullOrEmpty()
}

/**
 * A row that reports its current setting beside its title — a time, a mushaf name, a mode.
 *
 * The framework's summary sits under the title, which suits a sentence explaining a setting but
 * not a value: a value is the answer to the title and reads as one line with it. A row may carry
 * both, and the two never compete for the same width.
 */
class ValuePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** Shown opposite the title; null or empty leaves the slot closed. */
    var value: CharSequence? = null
        set(text) {
            if (field == text) return
            field = text
            notifyChanged()
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.bindValue(value)
    }
}

/**
 * A row that opens a list of choices and shows the chosen one as its value.
 *
 * The choice *is* the value, so nothing has to keep the two in step: setting the preference redraws
 * the row, and the entry it names is what appears.
 */
class ValueListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ListPreference(context, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.bindValue(entry)
    }
}
