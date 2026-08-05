package com.lhacenmed.khatmah.feature.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
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
 * The rows follow [ThemeManager] rather than being set once and left. A mode change does not always
 * rebuild the screen — AppCompat recreates an Activity only when the mode it resolves to actually
 * changes, so choosing Dark on a device already in dark changes what the app follows without
 * changing how it looks — and a screen that had marked its own rows on tap would still be showing
 * the old answer to anything that changed the mode elsewhere.
 */
class DarkThemeFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.dark_theme_preferences, rootKey)

        val context = requireContext()

        Modes.forEach { (key, mode) ->
            findPreference<RadioPreference>(key)?.setOnPreferenceClickListener {
                ThemeManager.setMode(context, mode)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("high_contrast")?.setOnPreferenceChangeListener { _, value ->
            ThemeManager.setHighContrastEnabled(context, value as Boolean)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The rows show what ThemeManager holds — including the change this screen just asked for,
        // which arrives back through the same flow as any other.
        collectWhileStarted(ThemeManager.mode) { current ->
            Modes.forEach { (key, mode) ->
                findPreference<RadioPreference>(key)?.checked = mode == current
            }
        }
        collectWhileStarted(ThemeManager.highContrast) { enabled ->
            findPreference<SwitchPreferenceCompat>("high_contrast")?.isChecked = enabled
        }
    }
}
