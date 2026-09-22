package com.lhacenmed.khatmah.feature.more.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.deleteReminder
import com.lhacenmed.khatmah.shared.reminders.saveReminder
import kotlinx.coroutines.launch

/**
 * The sheet that creates a reminder, and the one that edits it — one screen for both, because both
 * answer the same two questions: when, and how often.
 *
 * Creating opens with the picker: everything the app can remind about, with the ones already set
 * marked as such. Editing opens on a reminder that already exists, so the picker has nothing to
 * ask and folds away, leaving the reminder's own name in the header.
 *
 * The sheet writes to [ReminderPrefs] itself rather than handing an answer back. The store is
 * observed by every screen that shows reminders, so a save here reaches them the same way a save
 * made anywhere else does — there is no result to route, and nothing to keep in step by hand.
 */
class ReminderEditorSheet : BottomSheetDialogFragment() {

    /** The reminder being edited, or null while one is being created. */
    private val editing: ReminderConfig?
        get() = arguments?.getString(ARG_ID)?.let(ReminderPrefs::getById)

    private var targetAdapter: ReminderTargetAdapter? = null

    private var timeHour = DEFAULT_HOUR
    private var timeMinute = DEFAULT_MINUTE
    private var repeatDayOfWeek = ReminderConfig.REPEAT_DAILY

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.reminder_editor_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val reminder = editing
        // An edit starts from what the reminder already says; a new one from a sensible evening.
        // Either way a rotation mid-edit restores what was being set, not what it started as.
        savedInstanceState?.let {
            timeHour        = it.getInt(STATE_HOUR, timeHour)
            timeMinute      = it.getInt(STATE_MINUTE, timeMinute)
            repeatDayOfWeek = it.getInt(STATE_REPEAT, repeatDayOfWeek)
        } ?: reminder?.let {
            timeHour        = it.timeHour
            timeMinute      = it.timeMinute
            repeatDayOfWeek = it.repeatDayOfWeek
        }

        showHeader(view, reminder)
        showPicker(view, isCreating = reminder == null)
        bindFields(view)
        bindButtons(view, reminder)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOUR, timeHour)
        outState.putInt(STATE_MINUTE, timeMinute)
        outState.putInt(STATE_REPEAT, repeatDayOfWeek)
    }

    /**
     * The picker makes the sheet tall enough that half of it would otherwise open below the fold,
     * and the keyboard the search field raises would take the rest — so the sheet opens expanded
     * and resizes around the keyboard rather than being pushed under it.
     */
    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    /** A new reminder is announced as one; an existing one is named by what it reminds about. */
    private fun showHeader(view: View, reminder: ReminderConfig?) {
        view.findViewById<TextView>(R.id.title).text =
            reminder?.let { reminderName(requireContext(), it) } ?: getString(R.string.reminders_new_title)
        view.findViewById<View>(R.id.close).setOnClickListener { dismiss() }
    }

    /**
     * The list of things a reminder can be set for, loaded once, with the field that narrows it.
     *
     * It reads the mushaf and the adhkar database, so it arrives a moment after the sheet does.
     * The list's place in the layout is held from the start rather than appearing when it loads,
     * so the sheet does not jump under a finger already reaching for it — and the search field is
     * inert rather than absent until there is something to search.
     */
    private fun showPicker(view: View, isCreating: Boolean) {
        val list      = view.findViewById<RecyclerView>(R.id.targets)
        val label     = view.findViewById<View>(R.id.picker_label)
        val search    = view.findViewById<EditText>(R.id.search)
        val noMatches = view.findViewById<View>(R.id.no_matches)

        list.isVisible   = isCreating
        label.isVisible  = isCreating
        search.isVisible = isCreating
        if (!isCreating) return

        // The two share a slot: whichever has something to show is the one on screen, so a search
        // that finds nothing says so instead of leaving a blank where the list was.
        fun showMatches(count: Int) {
            list.isVisible      = count > 0
            noMatches.isVisible = count == 0
        }

        list.layoutManager = LinearLayoutManager(requireContext())
        search.doAfterTextChanged { query ->
            targetAdapter?.let { showMatches(it.filter(query?.toString().orEmpty())) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val adapter = ReminderTargetAdapter(reminderTargets(requireContext()))
            targetAdapter = adapter
            list.adapter = adapter
            // The field may already have been typed into while the mushaf was still loading.
            showMatches(adapter.filter(search.text.toString()))
        }
    }

    /** The two values the sheet sets, each opening its own picker. */
    private fun bindFields(view: View) {
        val time   = view.findViewById<TextView>(R.id.time)
        val repeat = view.findViewById<TextView>(R.id.repeat)

        fun showValues() {
            time.text   = "%02d:%02d".format(timeHour, timeMinute)
            repeat.text = repeatText(requireContext(), repeatDayOfWeek)
        }
        showValues()

        time.setOnClickListener {
            showTimePicker(requireContext(), timeHour, timeMinute) { hour, minute ->
                timeHour   = hour
                timeMinute = minute
                showValues()
            }
        }
        repeat.setOnClickListener {
            val options = repeatOptions(requireContext())
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reminders_repeat)
                .setSingleChoiceItems(
                    options.map { it.second }.toTypedArray(),
                    options.indexOfFirst { it.first == repeatDayOfWeek },
                ) { dialog, index ->
                    repeatDayOfWeek = options[index].first
                    showValues()
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Saving, and the way out beside it — which is "cancel" for a reminder that does not exist yet
     * and "delete" for one the user added. A reminder the app suggests by itself is never deleted,
     * only switched off, because its row is waiting for it either way.
     */
    private fun bindButtons(view: View, reminder: ReminderConfig?) {
        val save    = view.findViewById<MaterialButton>(R.id.save)
        val dismiss = view.findViewById<MaterialButton>(R.id.dismiss)

        save.setOnClickListener { if (reminder == null) create() else update(reminder) }

        val deletable = reminder?.label != null
        dismiss.setText(if (deletable) R.string.reminders_delete else R.string.reminders_cancel)
        dismiss.setOnClickListener {
            if (deletable) deleteReminder(requireContext(), reminder!!)
            dismiss()
        }
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    /**
     * A reminder for whatever the user picked. It carries the target's name so it can say what it
     * is about without asking the mushaf again, and starts on — nobody adds an alarm to leave it
     * off.
     */
    private fun create() {
        val target = targetAdapter?.selected ?: return
        saveReminder(requireContext(), ReminderConfig(
            id              = target.id,
            type            = target.type,
            enabled         = true,
            timeHour        = timeHour,
            timeMinute      = timeMinute,
            alarmCode       = ReminderPrefs.nextAlarmCode(),
            repeatDayOfWeek = repeatDayOfWeek,
            label           = target.name,
        ))
        dismiss()
    }

    private fun update(reminder: ReminderConfig) {
        saveReminder(requireContext(), reminder.copy(
            timeHour        = timeHour,
            timeMinute      = timeMinute,
            repeatDayOfWeek = repeatDayOfWeek,
        ))
        dismiss()
    }

    companion object {
        const val TAG = "reminder_editor"

        private const val ARG_ID = "reminder_id"
        private const val STATE_HOUR   = "hour"
        private const val STATE_MINUTE = "minute"
        private const val STATE_REPEAT = "repeat"

        /** Where a new reminder starts: this evening, every day. */
        private const val DEFAULT_HOUR   = 20
        private const val DEFAULT_MINUTE = 0

        /** The sheet that creates a reminder. */
        fun forNewReminder() = ReminderEditorSheet()

        /** The sheet that edits the reminder keyed [id]. */
        fun forReminder(id: String) = ReminderEditorSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
