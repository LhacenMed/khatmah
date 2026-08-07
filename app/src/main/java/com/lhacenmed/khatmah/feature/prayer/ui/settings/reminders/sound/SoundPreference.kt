package com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.RadioPreference

/**
 * One sound to choose from, with the option of hearing it first.
 *
 * The row chooses; the leading button plays. They are deliberately two targets — choosing a sound
 * commits it to the prayer, and being able to audition one without committing is the point of the
 * screen. A row with nothing to audition ([onPreview] left null) leaves the button inert, and it
 * reads as the plain state icon it then is.
 *
 * [onPreview] is read at bind time; the screen sets it when it builds the row.
 */
class SoundPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RadioPreference(context, attrs) {

    var onPreview: (() -> Unit)? = null

    init {
        layoutResource = R.layout.pref_sound
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val preview = onPreview
        holder.findViewById(R.id.preview)?.apply {
            // Clickable is also what gates the ripple, so an inert button shows no press at all.
            isClickable = preview != null
            setOnClickListener { preview?.invoke() }
        }
    }
}
