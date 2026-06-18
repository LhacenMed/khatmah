package com.lhacenmed.khatmah.core.nav

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailActivity
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorActivity
import com.lhacenmed.khatmah.feature.debug.DbBrowserActivity
import com.lhacenmed.khatmah.feature.debug.FileBrowserActivity
import com.lhacenmed.khatmah.feature.demo.DemoDetailScreen
import com.lhacenmed.khatmah.feature.khatmah.ui.DailyAlarmActivity
import com.lhacenmed.khatmah.feature.khatmah.ui.NewKhatmahActivity
import com.lhacenmed.khatmah.feature.mushaf.ui.PrintSelectActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.CalcMethodActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.DstActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.HigherLatActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.JuristicActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.ManualCorrectionsActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.AdhanRemindersActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound.AdhanSoundSelectionActivity
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryActivity
import com.lhacenmed.khatmah.feature.quran.ui.debug.DebugWarshActivity
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranReaderActivity
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranSessionReaderActivity
import com.lhacenmed.khatmah.feature.quran.ui.search.QuranSearchActivity
import com.lhacenmed.khatmah.feature.settings.AboutActivity
import com.lhacenmed.khatmah.feature.settings.DarkThemeActivity
import com.lhacenmed.khatmah.feature.settings.LanguageActivity
import com.lhacenmed.khatmah.feature.settings.ThemeSettingsActivity
import com.lhacenmed.khatmah.feature.today.FullIndexActivity
import com.lhacenmed.khatmah.feature.trips.ui.TripRequestsActivity
import com.lhacenmed.khatmah.onboarding.OnboardingActivity

/**
 * Type-safe catalogue of every full-screen destination.
 *
 * Each [Dest] is self-describing: it names the Activity that renders it ([target]) and
 * attaches its own arguments as intent extras ([extras]). The active [AppNavigator]
 * turns any [Dest] into a `startActivity(Intent…)` generically — so adding a destination
 * is just a new entry here plus one manifest line; no central `when` to keep in sync.
 *
 * Call sites stay type-safe and unchanged: `nav.go(Dest.QuranReader(suraNum = 5))`.
 */
sealed class Dest(val target: Class<out Activity>? = null) : java.io.Serializable {

    /**
     * Compose content for screens rendered by the single, already-declared
     * [com.lhacenmed.khatmah.core.ScreenHostActivity] — they need NO Activity class and
     * NO manifest entry. Screens still on their own Activity leave this null and rely on
     * [target] instead. (The host owns back/predictive-back exactly as a real Activity.)
     */
    open fun screen(): (@Composable () -> Unit)? = null

    /** Attach this destination's typed arguments as intent extras (legacy [target] path). */
    open fun extras(intent: Intent) {}

    // ── Today / Khatmah ─────────────────────────────────────────────────────────
    data object NewKhatmah : Dest(NewKhatmahActivity::class.java)
    data object DailyAlarm : Dest(DailyAlarmActivity::class.java)
    data object FullIndex : Dest(FullIndexActivity::class.java)
    data class QuranReader(val suraNum: Int = 0, val ayaNum: Int = 0) :
        Dest(QuranReaderActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(QuranReaderActivity.EXTRA_SURA, suraNum)
            intent.putExtra(QuranReaderActivity.EXTRA_AYA, ayaNum)
        }
    }
    data class QuranSessionReader(val startPage: Int, val endPage: Int) :
        Dest(QuranSessionReaderActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(QuranSessionReaderActivity.EXTRA_START_PAGE, startPage)
            intent.putExtra(QuranSessionReaderActivity.EXTRA_END_PAGE, endPage)
        }
    }
    data object QuranSearch : Dest(QuranSearchActivity::class.java)

    // ── Mushaf ──────────────────────────────────────────────────────────────────
    data object MushafPrints : Dest(PrintSelectActivity::class.java)

    // ── Settings ────────────────────────────────────────────────────────────────
    data object ThemeSettings : Dest(ThemeSettingsActivity::class.java)
    data object DarkTheme : Dest(DarkThemeActivity::class.java)
    data object Language : Dest(LanguageActivity::class.java)
    data object About : Dest(AboutActivity::class.java)

    // ── Prayer settings ─────────────────────────────────────────────────────────
    data object PrayerSettings : Dest(PrayerSettingsActivity::class.java)
    data object CalcMethod : Dest(CalcMethodActivity::class.java)
    data object Juristic : Dest(JuristicActivity::class.java)
    data object Dst : Dest(DstActivity::class.java)
    data object ManualCorrections : Dest(ManualCorrectionsActivity::class.java)
    data object HigherLat : Dest(HigherLatActivity::class.java)
    data object AdhanReminders : Dest(AdhanRemindersActivity::class.java)
    data class AdhanSoundSelection(val prayerId: Int) :
        Dest(AdhanSoundSelectionActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(AdhanSoundSelectionActivity.EXTRA_PRAYER_ID, prayerId)
        }
    }
    data object Qibla : Dest(QiblaActivity::class.java)

    // ── Adhkar / Qadaa ──────────────────────────────────────────────────────────
    data class AdhkarDetail(val categoryId: String) :
        Dest(AdhkarDetailActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(AdhkarDetailActivity.EXTRA_CATEGORY_ID, categoryId)
        }
    }
    data class AdhkarEditor(val categoryId: String? = null) :
        Dest(AdhkarEditorActivity::class.java) {
        override fun extras(intent: Intent) {
            categoryId?.let { intent.putExtra(AdhkarEditorActivity.EXTRA_CATEGORY_ID, it) }
        }
    }
    data object QadaaHistory : Dest(QadaaHistoryActivity::class.java)

    // ── Onboarding (self-contained wizard; deep-linked to a start step) ───────────
    data object OnboardingLocation : Dest(OnboardingActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.ONBOARDING_LOCATION)
        }
    }
    data class CountrySelect(val fromSettings: Boolean = false) :
        Dest(OnboardingActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.countrySelect(fromSettings))
        }
    }
    data class CitySelect(val country: String, val iso2: String, val fromSettings: Boolean = false) :
        Dest(OnboardingActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.citySelect(country, iso2, fromSettings))
        }
    }

    // ── Demo (temporary example) ──────────────────────────────────────────────────
    // Host-model screen: no Activity class, no manifest entry — just this entry plus the
    // DemoDetailScreen composable. That is the whole 2-step flow for a new screen.
    data object DemoDetail : Dest() {
        override fun screen(): @Composable () -> Unit = { DemoDetailScreen() }
    }

    // ── Debug ───────────────────────────────────────────────────────────────────
    data object DbBrowser : Dest(DbBrowserActivity::class.java)
    data object FileBrowser : Dest(FileBrowserActivity::class.java)
    data object TripRequests : Dest(TripRequestsActivity::class.java)
    data object DebugWarsh : Dest(DebugWarshActivity::class.java)
}

/**
 * Navigation entry point used by screen content. The host Activity supplies the
 * implementation (intent dispatch); leaf screens just call [go] / [back].
 */
interface AppNavigator {
    /** Launch the Activity for [dest]. */
    fun go(dest: Dest)

    /** Finish the current screen (native back). */
    fun back()
}

/** Provided by every Activity host so any composable in the tree can navigate. */
val LocalNavigator = staticCompositionLocalOf<AppNavigator> {
    error("No AppNavigator provided. Did the host Activity wrap content in CompositionLocalProvider(LocalNavigator provides …)?")
}
