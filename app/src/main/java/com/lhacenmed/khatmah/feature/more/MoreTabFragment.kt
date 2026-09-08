package com.lhacenmed.khatmah.feature.more

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lhacenmed.khatmah.BuildConfig
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.Reselectable
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValuePreference
import com.lhacenmed.khatmah.core.ui.components.go
import com.lhacenmed.khatmah.core.ui.components.onClick
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.ui.reader.sunnahReaderDest
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
import com.lhacenmed.khatmah.shared.reminders.SunnahSurah
import com.lhacenmed.khatmah.feature.update.UpdateChecker
import com.lhacenmed.khatmah.feature.update.UpdatePrefs
import com.lhacenmed.khatmah.feature.update.UpdateRegistry
import com.lhacenmed.khatmah.feature.update.UpdateStore
import com.lhacenmed.khatmah.shared.util.LocaleManager
import kotlinx.coroutines.launch

// Items within this distance from the top animate directly; farther ones jump-then-animate.
private const val SMOOTH_SCROLL_THRESHOLD = 4

/** The reminders shown here, paired with the row that sets each one's time. */
private val ReminderIds = listOf(
    "adhkar:morning",
    "adhkar:evening",
    "sunnah:al_mulk",
    "sunnah:al_baqarah",
)

/**
 * Everything the app keeps outside the four reading tabs: the current khatmah, the sunnah surahs,
 * the alarms, and the app's own settings.
 *
 * Built on the androidx Preference framework, like the reader's settings — the categories, rows,
 * switches and the language dialog are all the platform's. The rows front stores that already
 * exist rather than a preference file of their own, so they are declared non-persistent and are
 * read and written here.
 */
class MoreTabFragment : PreferenceFragmentCompat(), Reselectable {

    /** True while a manual update check is in flight — the row is inert until it answers. */
    private var checkingUpdate = false

    /** What the check row last reported, kept so a redraw does not wipe it. */
    private var updateStatus: CharSequence? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey)

        bindNavigation()
        bindSunnahSurahs()
        bindLanguage()
        bindReminders()
        bindUpdates()

        findPreference<PreferenceCategory>("debug")?.isVisible = BuildConfig.DEBUG
        findPreference<Preference>("version")?.title =
            getString(R.string.more_version, BuildConfig.VERSION_NAME)

        // Last, so it covers every row the calls above may have touched.
        preferenceScreen.tintIcons()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The categories already say where you are in the list; a bar fading in and out over the
        // rows on every touch is one moving part more than the screen needs.
        listView.isVerticalScrollBarEnabled = false
        observeSessionCounts()
        observeReminders()
        observeMushafPrint()
        collectWhileStarted(UpdatePrefs.autoPrompt) { showUpdateRows() }
    }

    /**
     * Two-phase scroll-to-top: jump to near the top, then animate the last stretch. Animating the
     * whole way from far down the list is just churn the user has to sit through.
     */
    override fun onReselect() {
        if (view == null) return
        val list = listView
        val first = (list.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: 0
        if (first > SMOOTH_SCROLL_THRESHOLD) list.scrollToPosition(SMOOTH_SCROLL_THRESHOLD)
        list.smoothScrollToPosition(0)
    }

    // ── Wiring ────────────────────────────────────────────────────────────────

    /** Rows that only lead somewhere. Rows with no destination yet are left inert, as they were. */
    private fun bindNavigation() {
        onClick("previous_sessions") { go(Dest.Sessions(showRead = true)) }
        onClick("upcoming_sessions") { go(Dest.Sessions(showRead = false)) }
        onClick("bookmarks")         { go(Dest.Bookmarks) }
        onClick("daily_alarm")       { go(Dest.DailyAlarm) }
        onClick("new_khatmah")       { go(Dest.NewKhatmah) }
        onClick("prayer_settings")   { go(Dest.PrayerSettings) }
        onClick("qibla")             { go(Dest.Qibla) }
        onClick("theme_settings")    { go(Dest.ThemeSettings) }
        onClick("mushaf_print")      { go(Dest.MushafPrints) }
        onClick("debug_db")          { go(Dest.DbBrowser) }
        onClick("trip_requests")     { go(Dest.TripRequests) }
        onClick("files_browser")     { go(Dest.FileBrowser) }
    }

    private fun bindSunnahSurahs() {
        onClick("surat_kahf")    { openSunnah(SunnahSurah.AlKahf) }
        onClick("surat_mulk")    { openSunnah(SunnahSurah.AlMulk) }
        onClick("surat_baqarah") { openSunnah(SunnahSurah.AlBaqarah) }
    }

    /**
     * The app language. Entry values carry the locale tag, with an empty one meaning "follow the
     * device" — [LocaleManager] takes null for that, so the two are mapped at the boundary.
     */
    private fun bindLanguage() {
        val preference = findPreference<androidx.preference.ListPreference>("language") ?: return
        preference.entries = arrayOf(
            getString(R.string.language_system_default),
            getString(R.string.language_english),
            getString(R.string.language_arabic),
        )
        preference.entryValues = arrayOf("", "en", "ar")
        preference.value = LocaleManager.getCurrentTag() ?: ""
        preference.setOnPreferenceChangeListener { _, value ->
            LocaleManager.setLocale((value as String).ifEmpty { null })
            true
        }
    }

    /**
     * Each alarm is a switch plus the row that sets its time. The time row's `dependency` in XML
     * already greys it out when the alarm is off, so nothing here has to think about that.
     */
    private fun bindReminders() {
        ReminderIds.forEach { id ->
            findPreference<SwitchPreferenceCompat>(id)?.setOnPreferenceChangeListener { _, value ->
                config(id)?.let { save(it.copy(enabled = value as Boolean)) }
                true
            }
            onClick("$id.time") {
                val current = config(id)?.takeIf { it.enabled } ?: return@onClick
                showTimePicker(requireContext(), current.timeHour, current.timeMinute) { hour, minute ->
                    save(current.copy(timeHour = hour, timeMinute = minute))
                }
            }
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Session counters, shown as pills on the two session rows. */
    private fun observeSessionCounts() {
        val repo = KhatmahRepository(requireContext())
        collectWhileStarted(repo.activeSessionCounts()) { counts ->
            findPreference<BadgePreference>("previous_sessions")?.count = counts.read
            findPreference<BadgePreference>("upcoming_sessions")?.count = counts.upcoming
        }
    }

    /** Switch states and alarm times, so a change made anywhere shows up here. */
    private fun observeReminders() {
        collectWhileStarted(ReminderPrefs.flow) { reminders ->
            ReminderIds.forEach { id ->
                val config = reminders.find { it.id == id }
                findPreference<SwitchPreferenceCompat>(id)?.isChecked = config?.enabled == true
                findPreference<ValuePreference>("$id.time")?.value =
                    config?.let { "%02d:%02d".format(it.timeHour, it.timeMinute) } ?: "--:--"
            }
        }
    }

    /** The selected mushaf, as the print row's value. */
    private fun observeMushafPrint() {
        collectWhileStarted(MushafPrefs.selected) { print ->
            findPreference<ValuePreference>("mushaf_print")?.value = getString(print.nameRes)
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Opens a sunnah surah as a session windowed to that surah's pages.
     *
     * Public because a sunnah reminder leads here too: the row and the notification are two ways
     * of asking for the same read. A print that cannot be windowed — not QCF4, or QCF4 with no
     * pages on disk for this riwaya yet — lands on the download dialog rather than doing nothing,
     * which would leave the row looking broken.
     */
    fun openSunnah(surah: SunnahSurah) {
        val context = requireContext()
        lifecycleScope.launch {
            val dest = sunnahReaderDest(context, surah) ?: return@launch showDownloadDialog()
            go(dest)
        }
    }

    /** Shown when a session is opened on a print that ships no page images to window. */
    private fun showDownloadDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.today_dl_title)
            .setMessage(R.string.today_dl_msg)
            .setPositiveButton(R.string.today_settings) { _, _ -> go(Dest.MushafPrints) }
            .setNegativeButton(R.string.today_cancel, null)
            .show()
    }

    private fun save(config: ReminderConfig) {
        val context = requireContext()
        ReminderPrefs.save(context, config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ReminderScheduler.schedule(context, config)
        }
    }

    private fun config(id: String): ReminderConfig? = ReminderPrefs.flow.value.find { it.id == id }

    // ── Updates ───────────────────────────────────────────────────────────────

    /**
     * Two rows for one decision: whether a found update speaks up by itself, and — when it doesn't —
     * the way to ask it. Only one of them is ever live, because with the alerts on there is nothing
     * left to ask for.
     */
    private fun bindUpdates() {
        findPreference<SwitchPreferenceCompat>(KEY_UPDATE_AUTO)?.setOnPreferenceChangeListener { _, value ->
            UpdatePrefs.setAutoPrompt(requireContext(), value as Boolean)
            true
        }
        onClick(KEY_UPDATE_CHECK) { checkForUpdate() }
    }

    /**
     * Asks about a newer build — unless one is already known, in which case the answer is in hand
     * and the dialog opens straight away. That covers the APK downloaded last session and still
     * waiting to install: it is "available" until the install goes through, so this reopens the
     * install prompt rather than re-fetching a manifest it already has.
     */
    private fun checkForUpdate() {
        if (UpdateRegistry.available.value != null) return UpdateRegistry.requestPrompt()

        val context = requireContext()
        checkingUpdate = true
        showUpdateRows(getString(R.string.update_checking))
        viewLifecycleOwner.lifecycleScope.launch {
            val found = UpdateChecker.check()
            found?.let {
                UpdateStore.save(context, it)
                UpdateRegistry.setAvailable(it)
                UpdateRegistry.requestPrompt()
            }
            checkingUpdate = false
            // A found update speaks for itself through the dialog; only its absence needs saying.
            showUpdateRows(if (found == null) getString(R.string.update_up_to_date) else null)
        }
    }

    /** Both rows from one reading, so neither can be left saying something the other contradicts. */
    private fun showUpdateRows(status: CharSequence? = updateStatus) {
        updateStatus = status
        val auto = UpdatePrefs.autoPrompt.value
        findPreference<SwitchPreferenceCompat>(KEY_UPDATE_AUTO)?.isChecked = auto
        findPreference<ValuePreference>(KEY_UPDATE_CHECK)?.apply {
            isEnabled = !auto && !checkingUpdate
            value = status
        }
    }

    private companion object {
        const val KEY_UPDATE_AUTO = "update_auto_prompt"
        const val KEY_UPDATE_CHECK = "update_check"
    }
}
