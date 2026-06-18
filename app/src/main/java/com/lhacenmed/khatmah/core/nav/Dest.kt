package com.lhacenmed.khatmah.core.nav

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Type-safe catalogue of every full-screen destination.
 *
 * Each [Dest] is a pure description of *where to go* (plus its arguments) — it carries
 * no Activity reference. The active [AppNavigator] (provided by the host Activity) maps
 * a [Dest] to a concrete `startActivity(Intent…)`, so screens depend only on this
 * contract, never on each other's Activity classes.
 *
 * Replaces the old stringly-typed routes (`nav.navigate("prayer_settings")`) and the
 * `AppRegistry`/NavHost registration. Adding a destination = add a [Dest] entry, a
 * `BaseComposeActivity` subclass, and one manifest line.
 */
sealed interface Dest {

    // ── Today / Khatmah ─────────────────────────────────────────────────────────
    data object NewKhatmah : Dest
    data object DailyAlarm : Dest
    data object FullIndex : Dest
    data class QuranReader(val suraNum: Int = 0, val ayaNum: Int = 0) : Dest
    data class QuranSessionReader(val startPage: Int, val endPage: Int) : Dest
    data object QuranSearch : Dest

    // ── Mushaf ──────────────────────────────────────────────────────────────────
    data object MushafPrints : Dest

    // ── Settings ────────────────────────────────────────────────────────────────
    data object ThemeSettings : Dest
    data object DarkTheme : Dest
    data object Language : Dest
    data object About : Dest

    // ── Prayer settings ─────────────────────────────────────────────────────────
    data object PrayerSettings : Dest
    data object CalcMethod : Dest
    data object Juristic : Dest
    data object Dst : Dest
    data object ManualCorrections : Dest
    data object HigherLat : Dest
    data object AdhanReminders : Dest
    data class AdhanSoundSelection(val prayerId: Int) : Dest
    data object Qibla : Dest

    // ── Adhkar ────────────────────────────────────────────────────────────────────
    data class AdhkarDetail(val categoryId: String) : Dest
    data class AdhkarEditor(val categoryId: String? = null) : Dest

    // ── Qadaa ─────────────────────────────────────────────────────────────────────
    data object QadaaHistory : Dest

    // ── Onboarding (Location/Country/City are also reachable from Prayer settings) ─
    data object OnboardingLocation : Dest
    data class CountrySelect(val fromSettings: Boolean = false) : Dest
    data class CitySelect(val country: String, val iso2: String, val fromSettings: Boolean = false) : Dest

    // ── Debug ───────────────────────────────────────────────────────────────────
    data object DbBrowser : Dest
    data object FileBrowser : Dest
    data object TripRequests : Dest
    data object DebugWarsh : Dest
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
