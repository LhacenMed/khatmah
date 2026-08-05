package com.lhacenmed.khatmah.core.ui

import android.content.res.ColorStateList
import androidx.core.graphics.ColorUtils
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR

/**
 * Draws every icon in this screen in one colour.
 *
 * The preference framework applies no tint of its own, so a screen assembled from icons of mixed
 * provenance shows exactly that — a vector authored with a black fill turns invisible in the dark
 * theme while the next one along is legible. Tinting here rather than editing twenty vectors keeps
 * the rule in one place, and it holds for rows added later.
 *
 * It also settles the icons drawn with strokes over a transparent fill: the tint is a filter over
 * the finished render, so it recolours stroke and fill alike. Size is the row layout's business —
 * `pref_row.xml` gives the icon a fixed box and scales the drawable into it.
 */
fun PreferenceGroup.tintIcons() {
    val enabled = MaterialColors.getColor(
        context,
        MaterialR.attr.colorOnSurfaceVariant,
        context.getColor(android.R.color.darker_gray),
    )
    // A tint list rather than a flat tint, so an icon dims along with the row it sits on — the
    // preference framework passes the row's enabled state down to its views, and the drawable
    // follows from there without anything having to set it.
    val tint = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(ColorUtils.setAlphaComponent(enabled, DISABLED_ALPHA), enabled),
    )
    forEachPreference { preference ->
        // mutate() so the tint stays on this row's copy and never leaks to another user of the
        // same vector — the bottom bar draws some of these too.
        preference.icon = preference.icon?.mutate()?.apply { setTintList(tint) }
    }
}

/** Material's disabled opacity (38%), as an alpha channel. */
private const val DISABLED_ALPHA = 97

/** Applies [action] to every preference in this group, nested categories included. */
private fun PreferenceGroup.forEachPreference(action: (Preference) -> Unit) {
    for (index in 0 until preferenceCount) {
        val child = getPreference(index)
        action(child)
        if (child is PreferenceGroup) child.forEachPreference(action)
    }
}
