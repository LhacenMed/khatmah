package com.lhacenmed.khatmah.core.ui.components

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.toIntent

/**
 * The two things every preference screen does with its rows: send one somewhere, or give one
 * something to do. Kept together so a screen reads as a list of what its rows mean rather than a
 * list of listener plumbing.
 */

/** Opens [dest] — the same journey `nav.go` makes on the Compose side. */
fun PreferenceFragmentCompat.go(dest: Dest) = startActivity(dest.toIntent(requireContext()))

/** Runs [action] when the row keyed [key] is pressed. Absent keys are ignored, as elsewhere. */
fun PreferenceFragmentCompat.onClick(key: String, action: () -> Unit) {
    findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
}
