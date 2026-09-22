package com.lhacenmed.khatmah.feature.more.reminders

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValueListPreference
import com.lhacenmed.khatmah.core.ui.components.ValuePreference
import com.lhacenmed.khatmah.core.ui.components.onClick
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.saveReminder

/** Preset choices for [AdhkarAnchor.id]".offset", in minutes. */
private val AdhkarOffsetOptions = intArrayOf(15, 20, 25, 30, 45, 60)

/**
 * Morning/evening adhkar anchor to a prayer by default — [prayerNameRes] and [isAfter] describe
 * that anchor for display, and must mirror ReminderScheduler's own Fajr/Maghrib choice.
 */
private data class AdhkarAnchor(val id: String, val prayerNameRes: Int, val isAfter: Boolean)

private val AdhkarAnchors = listOf(
    AdhkarAnchor("adhkar:morning", R.string.prayer_fajr,    isAfter = true),
    AdhkarAnchor("adhkar:evening", R.string.prayer_maghrib, isAfter = false),
)

/** The surahs the app suggests by itself — seeded reminders with a row waiting in the layout. */
private val SunnahReminderIds = listOf(
    "sunnah:al_kahf",
    "sunnah:al_mulk",
    "sunnah:al_baqarah",
)

/**
 * Every alarm the app can raise, in one screen: the daily adhkar, the surahs it suggests, and
 * whatever else the user has asked to be reminded about.
 *
 * The first two sections are declared in XML because they always exist. The third is built here,
 * because it is whatever the user has made it — but its category is declared with the rest, so the
 * screen has the same shape before and after anything is added to it.
 *
 * Everything is painted from [ReminderPrefs], which is also what the editor sheet writes to, so a
 * reminder added, changed or deleted in the sheet is already on the screen behind it.
 */
class RemindersFragment : PreferenceFragmentCompat() {

    /** The added reminders the rows currently stand for, so they are rebuilt only when that changes. */
    private var addedIds: List<String> = emptyList()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.reminders_preferences, rootKey)

        AdhkarAnchors.forEach(::bindAdhkarReminder)
        SunnahReminderIds.forEach(::bindSunnahReminder)
        onClick(KEY_ADD) { showEditor(ReminderEditorSheet.forNewReminder()) }

        // Last, so it covers every row the calls above may have touched.
        preferenceScreen.tintIcons()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.isVerticalScrollBarEnabled = false
        collectWhileStarted(ReminderPrefs.flow) { reminders ->
            AdhkarAnchors.forEach { anchor -> showAdhkarReminder(anchor, reminders) }
            SunnahReminderIds.forEach { id -> showSunnahReminder(id, reminders) }
            showAddedReminders(reminders.filter { it.label != null })
        }
    }

    // ── Adhkar ────────────────────────────────────────────────────────────────

    /**
     * Morning/evening adhkar carry two ways to set their time — an offset from the anchor prayer
     * (the default), or a fixed clock time — switched between by the ".custom" row. Both rows stay
     * in the layout at all times; [showAdhkarReminder] just enables the one the switch selects, so
     * the screen never restructures itself under the user.
     */
    private fun bindAdhkarReminder(anchor: AdhkarAnchor) {
        val id = anchor.id
        bindReminderSwitch(id)

        findPreference<ValueListPreference>("$id.offset")?.apply {
            val prayerName = getString(anchor.prayerNameRes)
            val labelRes = if (anchor.isAfter) R.string.more_adhkar_offset_after else R.string.more_adhkar_offset_before
            entries = AdhkarOffsetOptions.map { getString(labelRes, it, prayerName) }.toTypedArray()
            entryValues = AdhkarOffsetOptions.map { it.toString() }.toTypedArray()
            setOnPreferenceChangeListener { _, value ->
                config(id)?.let { save(it.copy(anchorOffsetMinutes = (value as String).toInt(), useCustomTime = false)) }
                true
            }
        }
        findPreference<SwitchPreferenceCompat>("$id.custom")?.setOnPreferenceChangeListener { _, value ->
            config(id)?.let { save(it.copy(useCustomTime = value as Boolean)) }
            true
        }
        onClick("$id.time") {
            val current = config(id)?.takeIf { it.enabled } ?: return@onClick
            showTimePicker(requireContext(), current.timeHour, current.timeMinute) { hour, minute ->
                save(current.copy(timeHour = hour, timeMinute = minute, useCustomTime = true))
            }
        }
    }

    private fun showAdhkarReminder(anchor: AdhkarAnchor, reminders: List<ReminderConfig>) {
        val id = anchor.id
        val config = reminders.find { it.id == id } ?: return
        findPreference<SwitchPreferenceCompat>(id)?.isChecked = config.enabled

        findPreference<ValueListPreference>("$id.offset")?.apply {
            isEnabled = config.enabled && !config.useCustomTime
            value = config.anchorOffsetMinutes.toString()
        }
        findPreference<SwitchPreferenceCompat>("$id.custom")?.isChecked = config.useCustomTime
        findPreference<ValuePreference>("$id.time")?.apply {
            isEnabled = config.enabled && config.useCustomTime
            value = clockForDisplay(config)
        }
    }

    // ── Surahs ────────────────────────────────────────────────────────────────

    /** A switch, and a row that opens the editor — the same editor a user's own reminder opens. */
    private fun bindSunnahReminder(id: String) {
        bindReminderSwitch(id)
        onClick("$id.time") {
            if (config(id)?.enabled == true) showEditor(ReminderEditorSheet.forReminder(id))
        }
    }

    private fun showSunnahReminder(id: String, reminders: List<ReminderConfig>) {
        val config = reminders.find { it.id == id }
        findPreference<SwitchPreferenceCompat>(id)?.isChecked = config?.enabled == true
        findPreference<ValuePreference>("$id.time")?.value = clockForDisplay(config)
    }

    // ── Added reminders ───────────────────────────────────────────────────────

    /**
     * The rows for whatever the user has added — a switch and the row that edits it, each the same
     * pair the suggested surahs above use, so an added reminder is not a lesser kind of reminder.
     *
     * The rows are rebuilt only when the set of reminders changes; a time or a switch moving just
     * repaints what is already there, which is what keeps the list from flickering on every toggle.
     */
    private fun showAddedReminders(added: List<ReminderConfig>) {
        val category = findPreference<PreferenceCategory>(KEY_ADDED) ?: return
        category.isVisible = added.isNotEmpty()

        val ids = added.map { it.id }
        if (ids != addedIds) {
            addedIds = ids
            category.removeAll()
            added.forEach { category.addAddedReminder(it) }
            preferenceScreen.tintIcons()
        }

        added.forEach { config ->
            findPreference<SwitchPreferenceCompat>(config.id)?.isChecked = config.enabled
            findPreference<ValuePreference>("${config.id}.time")?.value = clockForDisplay(config)
        }
    }

    /** The switch and the editor row for one added reminder, in that order. */
    private fun PreferenceCategory.addAddedReminder(config: ReminderConfig) {
        val context = requireContext()

        addPreference(SwitchPreferenceCompat(context).apply {
            key          = config.id
            title        = reminderName(context, config)
            icon         = context.getDrawable(R.drawable.ic_notifications)
            isPersistent = false
        })
        val time = ValuePreference(context).apply {
            key          = "${config.id}.time"
            title        = getString(R.string.reminders_schedule)
            icon         = context.getDrawable(R.drawable.ic_schedule)
            isPersistent = false
            setOnPreferenceClickListener {
                showEditor(ReminderEditorSheet.forReminder(config.id))
                true
            }
        }
        addPreference(time)
        // Named only once the row is in the hierarchy. A dependency is resolved the moment it is
        // named, and a row that has not been added yet has no hierarchy to find the switch in —
        // androidx throws there rather than waiting for one, which is why this cannot move up into
        // the block above with the rest of the row's properties.
        time.dependency = config.id

        bindReminderSwitch(config.id)
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private fun bindReminderSwitch(id: String) {
        findPreference<SwitchPreferenceCompat>(id)?.setOnPreferenceChangeListener { _, value ->
            config(id)?.let { save(it.copy(enabled = value as Boolean)) }
            true
        }
    }

    /**
     * What a time row reports: the clock time, and the day when it is not every day. A reminder the
     * screen cannot find says so with dashes rather than leaving its row blank, so the row keeps
     * its shape either way.
     */
    private fun clockForDisplay(config: ReminderConfig?): String = when {
        config == null -> "--:--"
        config.repeatDayOfWeek == ReminderConfig.REPEAT_DAILY -> clockText(config)
        else -> scheduleText(requireContext(), config)
    }

    private fun showEditor(sheet: ReminderEditorSheet) =
        sheet.show(childFragmentManager, ReminderEditorSheet.TAG)

    private fun save(config: ReminderConfig) = saveReminder(requireContext(), config)

    private fun config(id: String): ReminderConfig? = ReminderPrefs.getById(id)

    private companion object {
        const val KEY_ADDED = "added"
        const val KEY_ADD   = "add_reminder"
    }
}
