package com.lhacenmed.khatmah.feature.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.shared.util.ThemeManager

/**
 * Appearance: light or dark, and which palette the app is painted in.
 *
 * Every choice here goes through [ThemeManager], which resolves it into the native theme and
 * recreates the Activities on it — so this screen sets the value and nothing else. It is repainted
 * by that recreation, which is why it reads its state in `onCreatePreferences` rather than
 * observing: there is no moment where the screen is alive and the value has moved underneath it.
 */
class ThemeSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.theme_preferences, rootKey)

        val context = requireContext()

        findPreference<Preference>("dark_theme")?.apply {
            setSummary(modeLabel())
            setOnPreferenceClickListener {
                startActivity(Dest.DarkTheme.toIntent(context)); true
            }
        }

        findPreference<SwitchPreferenceCompat>("dynamic_color")?.apply {
            // Material You is the device's palette, so there is nothing to offer where it does
            // not exist — below API 31 the picker below is the only choice there is.
            isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            isChecked = ThemeManager.dynamicColor.value
            setOnPreferenceChangeListener { _, value ->
                ThemeManager.setDynamicColorEnabled(context, value as Boolean); true
            }
        }

        findPreference<PalettePreference>("palette")?.apply {
            // No swatch is marked while the device's own colours are in force.
            selected = if (ThemeManager.dynamicColor.value) -1 else ThemeManager.colorIndex.value
            onSelect = { index -> ThemeManager.setPalette(context, index) }
        }

        preferenceScreen.tintIcons()
    }

    /** The current mode, as the row's value — so the choice reads without opening the screen. */
    private fun modeLabel(): Int = when (ThemeManager.mode.value) {
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO  -> R.string.theme_light
        androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_dark
        else -> R.string.theme_system
    }
}
