package com.lhacenmed.khatmah.feature.more.reminders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.data.normalizeArabic

/**
 * The editor sheet's list of things a reminder can be set for, and the search over it.
 *
 * Filtering runs against the whole list in memory on every keystroke: the list is the mushaf plus
 * the adhkar categories, and matching a hundred-odd prepared strings is faster than the frame it
 * would be drawn in, so there is nothing here to debounce or move off the main thread.
 *
 * The selection survives filtering. Typing to find a surah and then clearing the field must not
 * quietly undo the choice that typing was for — what is on screen is a view of the list, not the
 * list itself.
 */
class ReminderTargetAdapter(
    private val targets: List<ReminderTarget>,
) : RecyclerView.Adapter<ReminderTargetAdapter.TargetViewHolder>() {

    /** What the user has picked, or null until they pick something. */
    var selected: ReminderTarget? = null
        private set

    private var visible: List<ReminderTarget> = targets

    class TargetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radio: RadioButton = view.findViewById(R.id.selected)
        val name: TextView     = view.findViewById(R.id.name)
        val badge: TextView    = view.findViewById(R.id.added_badge)
    }

    /**
     * Narrows the list to what [query] names, ignoring the diacritics and the spellings of alef and
     * ya that would otherwise make a surah searchable only by typing it exactly as the mushaf does.
     * Returns how many are left, which is what the sheet needs to say when nothing is.
     */
    fun filter(query: String): Int {
        val needle = query.trim().normalizeArabic()
        visible = if (needle.isEmpty()) targets else targets.filter { it.searchKey.contains(needle) }
        notifyDataSetChanged()
        return visible.size
    }

    override fun getItemCount() = visible.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TargetViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.reminder_target_row, parent, false)
    )

    override fun onBindViewHolder(holder: TargetViewHolder, position: Int) {
        val target = visible[position]

        holder.name.text       = target.name
        holder.radio.isChecked = target == selected
        holder.badge.isVisible = target.alreadyAdded
        holder.itemView.isEnabled = !target.alreadyAdded
        holder.name.isEnabled     = !target.alreadyAdded
        holder.radio.isEnabled    = !target.alreadyAdded

        holder.itemView.setOnClickListener {
            if (target.alreadyAdded) return@setOnClickListener
            val previous = visible.indexOf(selected)
            selected = target
            if (previous >= 0) notifyItemChanged(previous)
            notifyItemChanged(position)
        }
    }
}
