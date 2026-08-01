package com.lhacenmed.khatmah.core.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics

/**
 * Pins the app's UI scale so the design renders consistently across devices, independent of the
 * user's system **Display size** and **Font size** settings — the two knobs that otherwise make
 * the same screen look large on one phone and compact on another.
 *
 * - **Density** is reset to [DisplayMetrics.DENSITY_DEVICE_STABLE] — the device's boot density,
 *   which ignores the "Display size" zoom — so 1 dp maps to the same physical size everywhere and
 *   layouts keep their intended proportions.
 * - **Font scale** is clamped to [MAX_FONT_SCALE], so moderate large-text accessibility still
 *   helps but can never overflow fixed-height chrome (e.g. a top bar's stacked title + subtitle).
 *
 * Applied from every Activity's `attachBaseContext` via [wrap]; it is the single place UI scaling
 * is decided, so behaviour stays uniform app-wide.
 */
object UiScale {

    /** Upper bound for the system font scale — keeps text readable without breaking layouts. */
    const val MAX_FONT_SCALE = 1.15f

    /** Wraps [base] with a configuration that neutralises Display-size zoom and caps font scale. */
    fun wrap(base: Context): Context {
        val current = base.resources.configuration
        val stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE
        val clampedFont = current.fontScale.coerceAtMost(MAX_FONT_SCALE)

        // Already at the target scale → skip the wrap so nothing is allocated needlessly.
        if (current.densityDpi == stableDensity && current.fontScale == clampedFont) return base

        val config = Configuration(current).apply {
            densityDpi = stableDensity
            fontScale = clampedFont
        }
        return base.createConfigurationContext(config)
    }
}
