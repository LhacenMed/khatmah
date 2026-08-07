package com.lhacenmed.khatmah.feature.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.core.ui.components.ValuePreference
import com.lhacenmed.khatmah.core.ui.tintIcons
import com.lhacenmed.khatmah.shared.util.ThemeManager
import kotlinx.coroutines.flow.combine

/**
 * Appearance: light or dark, and which palette the app is painted in.
 *
 * Every choice goes through [ThemeManager], and every row here reads back from it. That matters
 * most for the mode row, whose value is changed on another screen entirely: this one is still
 * alive behind it, and a value read once when the screen was built would be the value from before
 * the visit.
 */
class ThemeSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.theme_preferences, rootKey)

        val context = requireContext()

        findPreference<Preference>("dark_theme")?.setOnPreferenceClickListener {
            startActivity(Dest.DarkTheme.toIntent(context))
            true
        }

        findPreference<SwitchPreferenceCompat>("dynamic_color")?.apply {
            // Material You is the device's palette, so there is nothing to offer where it does
            // not exist — below API 31 the picker below is the only choice there is.
            isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            setOnPreferenceChangeListener { _, value ->
                ThemeManager.setDynamicColorEnabled(context, value as Boolean)
                true
            }
        }

        findPreference<PalettePreference>("palette")?.onSelect = { index ->
            ThemeManager.setPalette(context, index)
        }

        preferenceScreen.tintIcons()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectWhileStarted(ThemeManager.mode) { mode ->
            findPreference<ValuePreference>("dark_theme")?.value = getString(modeLabel(mode))
        }
        // The two move together: a palette is only marked when the device's own colours are not in
        // force, so they are read as one value rather than as two rows racing each other.
        collectWhileStarted(
            ThemeManager.dynamicColor.combine(ThemeManager.colorIndex) { dynamic, index ->
                dynamic to index
            }
        ) { (dynamic, index) ->
            findPreference<SwitchPreferenceCompat>("dynamic_color")?.isChecked = dynamic
            findPreference<PalettePreference>("palette")?.selected = if (dynamic) NO_PALETTE else index
        }
    }

    /** The current mode, as the row's value — so the choice reads without opening the screen. */
    private fun modeLabel(mode: Int): Int = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO  -> R.string.theme_light
        AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_dark
        else -> R.string.theme_system
    }

    private companion object {
        /** No swatch is marked while the device's dynamic colours are in force. */
        const val NO_PALETTE = -1
    }
}
