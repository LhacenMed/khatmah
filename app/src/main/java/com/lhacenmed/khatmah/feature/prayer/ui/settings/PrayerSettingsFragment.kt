package com.lhacenmed.khatmah.feature.prayer.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValuePreference
import com.lhacenmed.khatmah.core.ui.components.go
import com.lhacenmed.khatmah.core.ui.components.onClick
import com.lhacenmed.khatmah.feature.prayer.data.DstMode
import com.lhacenmed.khatmah.feature.prayer.data.HigherLatMode
import com.lhacenmed.khatmah.feature.prayer.data.JuristicMethod
import com.lhacenmed.khatmah.feature.prayer.data.PrayerCalcSettings
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs

/**
 * Prayer settings: where the times are computed for, what announces them, and how they are worked
 * out.
 *
 * Every calculation row carries its current choice as the row's value, so the whole configuration
 * reads without opening a single one. They are also the rows the automatic switch speaks for: with
 * it on, the method and school follow the country and the rows go quiet — shown, so the reading is
 * still there, but not open to being contradicted.
 *
 * [PrayerSettings] owns the values and this reads back from it, which matters because several of
 * these choices are made on other screens: this one is still alive behind them, and a value read
 * once when the screen was built would be the value from before the visit.
 */
class PrayerSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prayer_preferences, rootKey)

        onClick("qibla")           { go(Dest.Qibla) }
        onClick("adhan_reminders") { go(Dest.AdhanReminders) }
        onClick("auto_location")   { go(Dest.OnboardingLocation) }
        onClick("manual_location") { go(Dest.CountrySelect(fromSettings = true)) }

        onClick("calc_method") { go(Dest.CalcMethod) }
        onClick("juristic")    { go(Dest.Juristic) }
        onClick("dst")         { go(Dest.Dst) }
        onClick("corrections") { go(Dest.ManualCorrections) }
        onClick("higher_lat")  { go(Dest.HigherLat) }

        findPreference<SwitchPreferenceCompat>("auto_settings")?.setOnPreferenceChangeListener { _, value ->
            PrayerSettings.save(requireContext(), toggleAuto(value as Boolean))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectWhileStarted(PrayerSettings.flow) { showCalculation(it) }
    }

    /**
     * The place is chosen on another screen, and unlike the calculation settings it is read rather
     * than observed — so it is refreshed on the way back in, when a change can have happened.
     */
    override fun onResume() {
        super.onResume()
        showLocation()
    }

    // ── Rows ──────────────────────────────────────────────────────────────────

    private fun showLocation() {
        val location = OnboardingPrefs.location(requireContext())
        findPreference<ValuePreference>("location")?.apply {
            title = location?.cityName?.takeIf { it.isNotBlank() }
                ?: getString(R.string.prayers_city_unknown)
            value = location?.let { "%.4f°, %.4f°".format(it.lat, it.lng) }
        }
    }

    private fun showCalculation(settings: PrayerCalcSettings) {
        val auto = settings.autoSettings
        // What the calculation will actually use: with the switch on, the country decides.
        val effective = settings.resolve(countryCode())

        findPreference<SwitchPreferenceCompat>("auto_settings")?.isChecked = auto

        setValue("calc_method", auto, effective.method.displayName)
        setValue("juristic", auto, getString(juristicLabel(effective.juristic)))
        setValue("dst", auto, getString(dstLabel(settings.dstMode)))
        setValue("corrections", auto, getString(correctionsLabel(settings)))
        setValue("higher_lat", auto, getString(higherLatLabel(settings.higherLatMode)))
    }

    /** A calculation row: its current reading, and live only while the automatic switch is off. */
    private fun setValue(key: String, auto: Boolean, value: String) {
        findPreference<ValuePreference>(key)?.apply {
            isEnabled = !auto
            this.value = value
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Turning the switch off keeps what the country had resolved to, so the manual settings start
     * from where the automatic ones left rather than from a default nobody chose.
     */
    private fun toggleAuto(on: Boolean): PrayerCalcSettings {
        val settings = PrayerSettings.get()
        if (on) return settings.copy(autoSettings = true)
        val effective = settings.resolve(countryCode())
        return settings.copy(
            autoSettings = false,
            method = effective.method,
            juristic = effective.juristic,
        )
    }

    private fun countryCode(): String =
        OnboardingPrefs.location(requireContext())?.countryCode.orEmpty()

    // ── Labels ────────────────────────────────────────────────────────────────

    private fun juristicLabel(juristic: JuristicMethod): Int =
        if (juristic == JuristicMethod.HANAFI) R.string.juristic_hanafi else R.string.juristic_shafi

    private fun dstLabel(mode: DstMode): Int = when (mode) {
        DstMode.AUTOMATIC -> R.string.dst_automatic
        DstMode.PLUS_ONE  -> R.string.dst_plus_one
        DstMode.MINUS_ONE -> R.string.dst_minus_one
    }

    private fun correctionsLabel(settings: PrayerCalcSettings): Int =
        if (settings.corrections.isAllZero) R.string.corrections_all_default
        else R.string.corrections_customized

    private fun higherLatLabel(mode: HigherLatMode): Int = when (mode) {
        HigherLatMode.NONE             -> R.string.higher_lat_none
        HigherLatMode.MIDDLE_OF_NIGHT  -> R.string.higher_lat_middle
        HigherLatMode.SEVENTH_OF_NIGHT -> R.string.higher_lat_seventh
        HigherLatMode.ANGLE_BASED      -> R.string.higher_lat_angle
    }
}
