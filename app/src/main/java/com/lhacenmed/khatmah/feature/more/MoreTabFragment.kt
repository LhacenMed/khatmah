package com.lhacenmed.khatmah.feature.more

import android.os.Build
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.core.ui.components.showTimePicker
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.RiwayaConfig
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import com.lhacenmed.khatmah.feature.quran.ui.reader.sessionReaderDest
import com.lhacenmed.khatmah.shared.reminders.ReminderConfig
import com.lhacenmed.khatmah.shared.reminders.ReminderPrefs
import com.lhacenmed.khatmah.shared.reminders.ReminderScheduler
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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.more_preferences, rootKey)

        bindNavigation()
        bindSunnahSurahs()
        bindLanguage()
        bindReminders()

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
        onClick("surat_kahf")    { openSunnah(18) }
        onClick("surat_mulk")    { openSunnah(67) }
        onClick("surat_baqarah") { openSunnah(2) }
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
        collectWhileStarted {
            repo.activeSessionCounts().collect { counts ->
                findPreference<BadgePreference>("previous_sessions")?.count = counts.read
                findPreference<BadgePreference>("upcoming_sessions")?.count = counts.upcoming
            }
        }
    }

    /** Switch states and time summaries, so a change made anywhere shows up here. */
    private fun observeReminders() {
        collectWhileStarted {
            ReminderPrefs.flow.collect { reminders ->
                ReminderIds.forEach { id ->
                    val config = reminders.find { it.id == id }
                    findPreference<SwitchPreferenceCompat>(id)?.isChecked = config?.enabled == true
                    findPreference<Preference>("$id.time")?.summary =
                        config?.let { "%02d:%02d".format(it.timeHour, it.timeMinute) } ?: "--:--"
                }
            }
        }
    }

    /** The selected mushaf, as the print row's summary. */
    private fun observeMushafPrint() {
        collectWhileStarted {
            MushafPrefs.selected.collect { print ->
                findPreference<Preference>("mushaf_print")?.setSummary(print.nameRes)
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Opens a sunnah surah as a session windowed to that surah's pages. Only a QCF4 print ships
     * the page images a window needs, so anything else is turned back at the door.
     */
    private fun openSunnah(surahNum: Int) {
        val print = MushafPrefs.selected.value
        if (!print.isQcf4) return showDownloadDialog()
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val ayaCount = RiwayaConfig.of(print.riwaya).ayaCount(surahNum)
            val range = QuranTextRepository(context)
                .pageRangeForSurah(print.riwaya.dbKey, surahNum, ayaCount)
            // A print can be QCF4 and still have no pages on disk for this riwaya yet. Returning
            // quietly would leave the row looking broken — a tap that does nothing at all — so an
            // unresolvable range lands on the same dialog as an unsuitable print.
            if (range == null) return@launch showDownloadDialog()
            // Negative session id keeps a per-surah reading position, never colliding with khatmah ids.
            go(sessionReaderDest(-surahNum.toLong(), range.first, range.last))
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

    private fun go(dest: Dest) = startActivity(dest.toIntent(requireContext()))

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun onClick(key: String, action: () -> Unit) {
        findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
    }

    private fun collectWhileStarted(block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
        }
    }
}
