package com.lhacenmed.khatmah.core.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailScreen
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorScreen
import com.lhacenmed.khatmah.feature.debug.DbBrowserScreen
import com.lhacenmed.khatmah.feature.debug.FileBrowserScreen
import com.lhacenmed.khatmah.feature.demo.DemoDetailScreen
import com.lhacenmed.khatmah.feature.khatmah.ui.DailyAlarmScreen
import com.lhacenmed.khatmah.feature.khatmah.ui.NewKhatmahScreen
import com.lhacenmed.khatmah.feature.quran.ui.bookmarks.BookmarksScreen
import com.lhacenmed.khatmah.feature.quran.ui.prints.PrintSelectScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.CalcMethodScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.DstScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.HigherLatScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.JuristicScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.ManualCorrectionsScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.AdhanRemindersScreen
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound.AdhanSoundSelectionScreen
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryScreen
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderActivity
import com.lhacenmed.khatmah.feature.settings.AboutScreen
import com.lhacenmed.khatmah.feature.settings.DarkThemeScreen
import com.lhacenmed.khatmah.feature.settings.LanguageScreen
import com.lhacenmed.khatmah.feature.settings.ThemeSettingsScreen
import com.lhacenmed.khatmah.feature.today.FullIndexScreen
import com.lhacenmed.khatmah.feature.trips.ui.TripRequestsScreen
import com.lhacenmed.khatmah.onboarding.OnboardingActivity

/**
 * Type-safe catalogue of every full-screen destination.
 *
 * Most destinations are **host-model**: they override [screen] with their composable and are
 * rendered by the single, already-declared [com.lhacenmed.khatmah.core.ScreenHostActivity] —
 * so adding one is just an entry here plus its composable, with NO Activity class and NO
 * manifest line. The exception is onboarding, which targets a real [OnboardingActivity]
 * (a self-contained NavHost wizard) via [target] + [extras].
 *
 * Call sites stay type-safe and unchanged: `nav.go(Dest.Reader(suraNum = 5))`.
 */
sealed class Dest(val target: Class<out Activity>? = null) : java.io.Serializable {

    /**
     * Compose content for host-model screens. Rendered by [com.lhacenmed.khatmah.core.ScreenHostActivity];
     * such screens need no Activity class and no manifest entry. Legacy screens (onboarding)
     * leave this null and use [target] instead.
     */
    open fun screen(): (@Composable () -> Unit)? = null

    /**
     * Title for the shared native top bar in [com.lhacenmed.khatmah.core.ScreenHostActivity].
     * Non-null → the host renders the toolbar + a platform back arrow (auto-mirrored in RTL) and
     * the screen provides only its body. Null → the screen draws its own top bar (legacy).
     */
    @get:StringRes
    open val titleRes: Int? get() = null

    /**
     * Toolbar title resolved at show time; defaults to [titleRes]. Override for a dynamic title
     * (e.g. one that embeds an argument). Non-null → the host renders its native top bar.
     */
    open fun title(context: Context): String? = titleRes?.let(context::getString)

    /** Optional toolbar subtitle under the title (e.g. the selected city). Default: none. */
    open fun subtitle(context: Context): String? = null

    /** Attach typed arguments as intent extras (legacy [target] path, or a ViewModel's SavedStateHandle). */
    open fun extras(intent: Intent) {}

    // ── Today / Khatmah ─────────────────────────────────────────────────────────
    data object NewKhatmah : Dest() {
        override val titleRes get() = R.string.new_khatmah_title
        override fun screen() = @Composable { NewKhatmahScreen() }
    }
    data object DailyAlarm : Dest() {
        override val titleRes get() = R.string.more_daily_alarm
        override fun screen() = @Composable { DailyAlarmScreen() }
    }
    data object FullIndex : Dest() {
        override val titleRes get() = R.string.full_index_title
        override fun screen() = @Composable { FullIndexScreen() }
    }
    /**
     * The native (View-based) reader — targets its own [ReaderActivity] rather than the Compose
     * host. One destination for both modes; the selected print decides text vs QCF4 rendering.
     *
     * [page] (1-based) opens the QCF4 book reader on an exact mushaf page. [suraNum]/[ayaNum]
     * (1-based) target a verse — used by the text reader (different pagination) and as a fallback;
     * 0/0 resumes the last-read page. [startPage]..[endPage] (1-based, inclusive) restrict the QCF4
     * reader to a single Khatmah session's pages; [sessionId] keys that session's remembered page.
     */
    data class Reader(
        val page: Int = 0,
        val suraNum: Int = 0,
        val ayaNum: Int = 0,
        val startPage: Int = 0,
        val endPage: Int = 0,
        val sessionId: Long = 0,
    ) : Dest(ReaderActivity::class.java) {
        override fun extras(intent: Intent) {
            intent.putExtra(ReaderActivity.EXTRA_PAGE, page)
            intent.putExtra(ReaderActivity.EXTRA_SURA, suraNum)
            intent.putExtra(ReaderActivity.EXTRA_AYA, ayaNum)
            if (startPage > 0 && endPage > 0) {
                intent.putExtra(ReaderActivity.EXTRA_START_PAGE, startPage)
                intent.putExtra(ReaderActivity.EXTRA_END_PAGE, endPage)
                intent.putExtra(ReaderActivity.EXTRA_SESSION_ID, sessionId)
            }
        }
    }

    // ── Mushaf ──────────────────────────────────────────────────────────────────
    data object MushafPrints : Dest() {
        override val titleRes get() = R.string.mushaf_print_title
        override fun screen() = @Composable { PrintSelectScreen() }
    }
    data object Bookmarks : Dest() {
        override val titleRes get() = R.string.bookmarks_title
        override fun screen() = @Composable { BookmarksScreen() }
    }

    // ── Settings ────────────────────────────────────────────────────────────────
    data object ThemeSettings : Dest() {
        override val titleRes get() = R.string.theme_settings
        override fun screen() = @Composable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ThemeSettingsScreen()
        }
    }
    data object DarkTheme : Dest() {
        override val titleRes get() = R.string.theme_dark
        override fun screen() = @Composable { DarkThemeScreen() }
    }
    data object Language : Dest() {
        override val titleRes get() = R.string.language_settings
        override fun screen() = @Composable { LanguageScreen() }
    }
    data object About : Dest() {
        override val titleRes get() = R.string.about_page
        override fun screen() = @Composable { AboutScreen() }
    }

    // ── Prayer settings ─────────────────────────────────────────────────────────
    data object PrayerSettings : Dest() {
        override val titleRes get() = R.string.prayer_settings_title
        override fun screen() = @Composable { PrayerSettingsScreen() }
    }
    data object CalcMethod : Dest() {
        override val titleRes get() = R.string.prayer_settings_calc_method
        override fun screen() = @Composable { CalcMethodScreen() }
    }
    data object Juristic : Dest() {
        override val titleRes get() = R.string.prayer_settings_juristic
        override fun screen() = @Composable { JuristicScreen() }
    }
    data object Dst : Dest() {
        override val titleRes get() = R.string.prayer_settings_dst
        override fun screen() = @Composable { DstScreen() }
    }
    data object ManualCorrections : Dest() {
        override val titleRes get() = R.string.prayer_settings_corrections
        override fun screen() = @Composable { ManualCorrectionsScreen() }
    }
    data object HigherLat : Dest() {
        override val titleRes get() = R.string.prayer_settings_higher_lat
        override fun screen() = @Composable { HigherLatScreen() }
    }
    data object AdhanReminders : Dest() {
        override val titleRes get() = R.string.adhan_reminders_title
        override fun screen() = @Composable { AdhanRemindersScreen() }
    }
    data class AdhanSoundSelection(val prayerId: Int) : Dest() {
        // Dynamic title: "<prayer> adhan" — the prayer name varies with prayerId.
        override fun title(context: Context): String {
            val names = intArrayOf(
                R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
                R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha,
            )
            val prayer = context.getString(names.getOrElse(prayerId) { R.string.prayers })
            return context.getString(R.string.adhan_alarm_title_format, prayer)
        }
        override fun screen() = @Composable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AdhanSoundSelectionScreen(prayerId)
        }
    }
    data object Qibla : Dest() {
        override val titleRes get() = R.string.prayers_qibla
        override fun subtitle(context: Context) =
            OnboardingPrefs.location(context)?.cityName?.takeIf { it.isNotBlank() }
        override fun screen() = @Composable { QiblaScreen() }
    }

    // ── Adhkar / Qadaa ──────────────────────────────────────────────────────────
    data class AdhkarDetail(val categoryId: String) : Dest() {
        override fun screen() = @Composable { AdhkarDetailScreen(categoryId) }
    }
    data class AdhkarEditor(val categoryId: String? = null) : Dest() {
        override fun screen() = @Composable { AdhkarEditorScreen(categoryId) }
    }
    data object QadaaHistory : Dest() {
        override val titleRes get() = R.string.qadaa_history
        override fun screen() = @Composable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) QadaaHistoryScreen()
        }
    }

    // ── Onboarding (legacy: targets the OnboardingActivity NavHost wizard) ─────────
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
    data object DemoDetail : Dest() {
        override fun screen() = @Composable { DemoDetailScreen() }
    }

    // ── Debug ───────────────────────────────────────────────────────────────────
    data object DbBrowser : Dest() {
        override fun screen() = @Composable { DbBrowserScreen() }
    }
    data object FileBrowser : Dest() {
        override fun screen() = @Composable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) FileBrowserScreen()
        }
    }
    data object TripRequests : Dest() {
        override fun screen() = @Composable { TripRequestsScreen() }
    }
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
