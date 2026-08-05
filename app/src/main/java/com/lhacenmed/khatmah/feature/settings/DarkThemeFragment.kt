package com.lhacenmed.khatmah.feature.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.util.ThemeManager

/** The three ways the app can decide between light and dark, paired with their rows. */
private val Modes = listOf(
    "mode_system" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    "mode_light" to AppCompatDelegate.MODE_NIGHT_NO,
    "mode_dark" to AppCompatDelegate.MODE_NIGHT_YES,
)

/**
 * Light or dark, and the pure-black option for reading in it.
 *
 * Choosing a mode recreates the Activities through AppCompat, so the marks are set once from the
 * current value and the screen is rebuilt rather than updated in place.
 */
class DarkThemeFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.dark_theme_preferences, rootKey)

        val context = requireContext()
        val current = ThemeManager.mode.value

        Modes.forEach { (key, mode) ->
            findPreference<RadioPreference>(key)?.apply {
                checked = mode == current
                setOnPreferenceClickListener { ThemeManager.setMode(context, mode); true }
            }
        }

        findPreference<SwitchPreferenceCompat>("high_contrast")?.apply {
            isChecked = ThemeManager.highContrast.value
            setOnPreferenceChangeListener { _, value ->
                ThemeManager.setHighContrastEnabled(context, value as Boolean); true
            }
        }
    }
}
