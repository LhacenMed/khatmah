package com.lhacenmed.khatmah.core.nav

import android.os.Build
import androidx.annotation.RequiresApi
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.more.MoreTab
import com.lhacenmed.khatmah.feature.mushaf.ui.PrintSelectPage
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
import com.lhacenmed.khatmah.feature.quran.ui.IndexTab
import com.lhacenmed.khatmah.feature.settings.AboutPage
import com.lhacenmed.khatmah.feature.settings.ThemeSettingsPage
import com.lhacenmed.khatmah.feature.settings.DarkThemePage
import com.lhacenmed.khatmah.feature.settings.LanguagePage
import com.lhacenmed.khatmah.feature.today.TodayTab

import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.CalcMethodPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.DstPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.HigherLatPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.JuristicPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.ManualCorrectionsPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.AdhanRemindersPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound.AdhanSoundSelectionPage

import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranReaderPage
import com.lhacenmed.khatmah.feature.quran.ui.search.QuranSearchPage
import com.lhacenmed.khatmah.feature.quran.ui.debug.DebugWarshPage
import com.lhacenmed.khatmah.feature.khatmah.ui.NewKhatmahPage
import com.lhacenmed.khatmah.feature.khatmah.ui.DailyAlarmPage
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranSessionReaderPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorPage
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaPage
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryPage
import com.lhacenmed.khatmah.feature.trips.ui.TripRequestsPage
import com.lhacenmed.khatmah.feature.debug.DbBrowserPage
import com.lhacenmed.khatmah.feature.debug.FileBrowserPage
import com.lhacenmed.khatmah.feature.today.FullIndexPage

/**
 * Single source of truth for all navigation destinations.
 *
 * ── Adding a new tab ──────────────────────────────────────────────────────────
 *  1. `object YourTab : AppTab(iconRes, labelRes, order)` in its feature package.
 *  2. Add to [tabs] below — done.
 *
 * ── Adding a new page ─────────────────────────────────────────────────────────
 *  1. `object YourPage : AppPage()` in its feature package.
 *  2. Add to [pages] below — done.
 *
 * Onboarding pages are excluded intentionally — they form a linear flow with
 * special argument forwarding handled directly in [AppEntry].
 */
@RequiresApi(Build.VERSION_CODES.O)
object AppRegistry {

    /** Tab-bar destinations sorted by [AppTab.order]. */
    val tabs: List<AppTab> = listOf(
        TodayTab,
        AdhkarTab,
        PrayersTab,
        IndexTab,
        MoreTab,
    ).sortedBy { it.order }

    /**
     * Full-screen page destinations. [AppEntry] registers all entries here
     * automatically via a single NavHost loop — no manual [animatedComposable]
     * blocks needed for any destination listed here.
     *
     * Migrate remaining pages progressively: create the AppPage object in the
     * feature file, add it here, and remove the corresponding manual block
     * from [AppEntry].
     */
    val pages: List<AppPage> = listOf(
        // ── Settings ──────────────────────────────────────────────────────────
        AboutPage,
        ThemeSettingsPage,
        DarkThemePage,
        LanguagePage,

        // ── Adhkar ────────────────────────────────────────────────────────────
        AdhkarDetailPage,
        AdhkarEditorPage,

        // ── Mushaf ────────────────────────────────────────────────────────────
        PrintSelectPage,

        // ── Prayer Settings ───────────────────────────────────────────────────
        PrayerSettingsPage,
        CalcMethodPage,
        JuristicPage,
        DstPage,
        ManualCorrectionsPage,
        HigherLatPage,
        AdhanRemindersPage,
        AdhanSoundSelectionPage,
        QiblaPage,

        // ── Quran Tools ───────────────────────────────────────────────────────
        QuranReaderPage,
        QuranSearchPage,
        DebugWarshPage,

        // ── Khatmah & Adhkar ──────────────────────────────────────────────────
        NewKhatmahPage,
        DailyAlarmPage,
        QuranSessionReaderPage,
        FullIndexPage,

        // ── Utilities & Debug ─────────────────────────────────────────────────
        QadaaPage,
        QadaaHistoryPage,
        TripRequestsPage,
        DbBrowserPage,
        FileBrowserPage,
    )
}