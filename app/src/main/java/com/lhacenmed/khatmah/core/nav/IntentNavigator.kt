package com.lhacenmed.khatmah.core.nav

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailActivity
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorActivity
import com.lhacenmed.khatmah.feature.debug.DbBrowserActivity
import com.lhacenmed.khatmah.feature.debug.FileBrowserActivity
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryActivity
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
 * The single place that maps a type-safe [Dest] to the Activity that renders it.
 * Arguments travel as Intent extras; each target Activity reads them back via the
 * keys exposed on its companion (or, for ViewModel-backed readers, via the default
 * SavedStateHandle seeded from these extras).
 */
fun Dest.toIntent(context: Context): Intent = when (this) {

    // ── Today / Khatmah ─────────────────────────────────────────────────────────
    Dest.NewKhatmah -> intent<NewKhatmahActivity>(context)
    Dest.DailyAlarm -> intent<DailyAlarmActivity>(context)
    Dest.FullIndex  -> intent<FullIndexActivity>(context)
    is Dest.QuranReader -> intent<QuranReaderActivity>(context)
        .putExtra(QuranReaderActivity.EXTRA_SURA, suraNum)
        .putExtra(QuranReaderActivity.EXTRA_AYA, ayaNum)
    is Dest.QuranSessionReader -> intent<QuranSessionReaderActivity>(context)
        .putExtra(QuranSessionReaderActivity.EXTRA_START_PAGE, startPage)
        .putExtra(QuranSessionReaderActivity.EXTRA_END_PAGE, endPage)
    Dest.QuranSearch -> intent<QuranSearchActivity>(context)

    // ── Mushaf ──────────────────────────────────────────────────────────────────
    Dest.MushafPrints -> intent<PrintSelectActivity>(context)

    // ── Settings ────────────────────────────────────────────────────────────────
    Dest.ThemeSettings -> intent<ThemeSettingsActivity>(context)
    Dest.DarkTheme     -> intent<DarkThemeActivity>(context)
    Dest.Language      -> intent<LanguageActivity>(context)
    Dest.About         -> intent<AboutActivity>(context)

    // ── Prayer settings ─────────────────────────────────────────────────────────
    Dest.PrayerSettings    -> intent<PrayerSettingsActivity>(context)
    Dest.CalcMethod        -> intent<CalcMethodActivity>(context)
    Dest.Juristic          -> intent<JuristicActivity>(context)
    Dest.Dst               -> intent<DstActivity>(context)
    Dest.ManualCorrections -> intent<ManualCorrectionsActivity>(context)
    Dest.HigherLat         -> intent<HigherLatActivity>(context)
    Dest.AdhanReminders    -> intent<AdhanRemindersActivity>(context)
    is Dest.AdhanSoundSelection -> intent<AdhanSoundSelectionActivity>(context)
        .putExtra(AdhanSoundSelectionActivity.EXTRA_PRAYER_ID, prayerId)
    Dest.Qibla             -> intent<QiblaActivity>(context)

    // ── Onboarding (self-contained wizard; deep-linked to a start step) ───────────
    Dest.OnboardingLocation -> intent<OnboardingActivity>(context)
        .putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.ONBOARDING_LOCATION)
    is Dest.CountrySelect -> intent<OnboardingActivity>(context)
        .putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.countrySelect(fromSettings))
    is Dest.CitySelect -> intent<OnboardingActivity>(context)
        .putExtra(OnboardingActivity.EXTRA_START_ROUTE, ShellRoutes.citySelect(country, iso2, fromSettings))

    // ── Adhkar / Qadaa ──────────────────────────────────────────────────────────
    is Dest.AdhkarDetail -> intent<AdhkarDetailActivity>(context)
        .putExtra(AdhkarDetailActivity.EXTRA_CATEGORY_ID, categoryId)
    is Dest.AdhkarEditor -> intent<AdhkarEditorActivity>(context)
        .apply { categoryId?.let { putExtra(AdhkarEditorActivity.EXTRA_CATEGORY_ID, it) } }
    Dest.QadaaHistory -> intent<QadaaHistoryActivity>(context)

    // ── Debug ───────────────────────────────────────────────────────────────────
    Dest.DbBrowser    -> intent<DbBrowserActivity>(context)
    Dest.FileBrowser  -> intent<FileBrowserActivity>(context)
    Dest.TripRequests -> intent<TripRequestsActivity>(context)
    Dest.DebugWarsh   -> intent<DebugWarshActivity>(context)
}

private inline fun <reified T> intent(context: Context) = Intent(context, T::class.java)

/**
 * [AppNavigator] backed by Activity intents. Provided by every host Activity so any
 * composable can navigate ([go]) or pop the current screen ([back]) with the platform
 * owning the back stack, transitions and predictive back.
 */
class IntentNavigator(private val activity: ComponentActivity) : AppNavigator {
    override fun go(dest: Dest) {
        activity.startActivity(dest.toIntent(activity))
    }

    override fun back() {
        activity.finish()
    }
}
