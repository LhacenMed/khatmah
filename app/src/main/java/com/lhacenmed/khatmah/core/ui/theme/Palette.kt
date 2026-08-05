package com.lhacenmed.khatmah.core.ui.theme

import android.content.Context
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.shared.util.ThemeManager
import com.google.android.material.R as MaterialR

/**
 * The palettes offered by the appearance settings, in the order their swatches appear.
 * [com.lhacenmed.khatmah.shared.util.ThemeManager.colorIndex] indexes this list.
 *
 * Each entry is a Material 3 theme overlay — the colours themselves live in
 * `res/values/themes_palette.xml` and its `values-night` counterpart, which is the app's one
 * definition of what a palette is. Holding style ids rather than colours here is what keeps it
 * that way: there is nothing in Kotlin that could fall out of step with the XML.
 */
val paletteOverlays: List<Int> = listOf(
    R.style.ThemeOverlay_Khatmah_Palette_Violet,
    R.style.ThemeOverlay_Khatmah_Palette_Blue,
    R.style.ThemeOverlay_Khatmah_Palette_Green,
    R.style.ThemeOverlay_Khatmah_Palette_Orange,
    R.style.ThemeOverlay_Khatmah_Palette_Pink,
)

/**
 * The primary colour a palette resolves to, for the settings picker's swatches. Read from the
 * palette's own overlay, so a swatch cannot show a colour the app would not actually use.
 */
fun paletteColor(context: Context, index: Int, night: Boolean): Int =
    MaterialColors.getColor(
        ThemeManager.themedContext(context, night, paletteIndex = index),
        MaterialR.attr.colorPrimary,
        0,
    )
