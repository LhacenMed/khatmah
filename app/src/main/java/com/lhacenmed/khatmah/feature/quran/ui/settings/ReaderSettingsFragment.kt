package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderPrefs
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderTheme

/**
 * Native reader settings, built on the androidx Preference framework (Quran Android pattern):
 * animated [CheckBoxPreference] toggles and custom brightness sliders.
 *
 * Display: reader-only night mode (via [ReaderTheme]) plus night text/background brightness —
 * disabled while night mode is off (preference `dependency`). Reading: the page-info overlay.
 *
 * Preferences read/write the reader's own "book_reader" store; the toggles are non-persistent and
 * routed through [ReaderTheme] / [ReaderPrefs] so every live page reacts immediately.
 */
class ReaderSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = ReaderPrefs.PREFS_FILE
        setPreferencesFromResource(R.xml.reader_preferences, rootKey)

        val ctx = requireContext()
        ReaderPrefs.init(ctx)
        ReaderTheme.init(ctx)

        // ── Night mode — reader-only override (drives the brightness dependency). ──
        findPreference<CheckBoxPreference>("reader_night")?.apply {
            isChecked = ReaderTheme.effectiveNight(ctx)
            setOnPreferenceChangeListener { _, _ -> ReaderTheme.toggle(ctx); true }
        }

        // ── Brightness sliders — push the committed value into the live reader state. ──
        findPreference<Preference>("text_brightness")?.setOnPreferenceChangeListener { _, value ->
            ReaderPrefs.setTextBrightness(ctx, value as Int); true
        }
        findPreference<Preference>("bg_brightness")?.setOnPreferenceChangeListener { _, value ->
            ReaderPrefs.setBackgroundBrightness(ctx, value as Int); true
        }

        // ── Page-info overlay. ──
        findPreference<CheckBoxPreference>("show_page_info")?.apply {
            isChecked = ReaderPrefs.showPageInfo.value
            setOnPreferenceChangeListener { _, value ->
                ReaderPrefs.setShowPageInfo(ctx, value as Boolean); true
            }
        }
    }
}
