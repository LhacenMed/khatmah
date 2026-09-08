package com.lhacenmed.khatmah.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.glance.color.ColorProvider
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.shared.util.ThemeManager

/** One colour role in both variants (ARGB). The two are equal when the app pins a night mode. */
internal data class DayNightColor(val day: Int, val night: Int)

/**
 * The colours [PrayerWidget] draws with — the app's own, in both variants.
 *
 * Every role is carried twice because a widget lives in the launcher: the device can switch to
 * dark long after the app's process is gone, and only the platform is there to react. Handing it
 * both variants (API 31+) makes the swap immediate and, just as importantly, whole — the Glance
 * composables and the embedded RemoteViews panels change together instead of one waiting for the
 * next refresh with the other already repainted.
 */
internal class WidgetPalette(
    /** The widget's own background. */
    val surface:   DayNightColor,
    /** Prayer-list text. */
    val onSurface: DayNightColor,
    /** The highlighted prayer. */
    val accent:    DayNightColor,
    /** Countdown-panel fill. */
    val panel:     DayNightColor,
    /** Countdown-panel text and icon. */
    val onPanel:   DayNightColor,
    /** The variant in force right now — the only one API < 31 can be told about. */
    val night:     Boolean,
)

/**
 * Resolves the app's palette for the widget: the chosen colours (Material You, a picked palette,
 * high contrast) exactly as [resolveColorScheme] gives them to every screen.
 *
 * A night mode the user pinned in the app wins over the device's, and pins both variants to it —
 * the widget belongs to the app, so it stays in the mode the app is in. Left on "follow system"
 * the two variants differ, and the launcher picks between them.
 */
internal fun widgetPalette(context: Context): WidgetPalette {
    val pinned = when (ThemeManager.mode.value) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO  -> false
        else                             -> null
    }
    val day   = resolveColorScheme(context, night = pinned ?: false)
    val night = resolveColorScheme(context, night = pinned ?: true)

    fun role(pick: ColorScheme.() -> Color) = DayNightColor(day.pick().toArgb(), night.pick().toArgb())

    return WidgetPalette(
        surface   = role { secondaryContainer },
        onSurface = role { onSecondaryContainer },
        accent    = role { primary },
        panel     = role { primaryContainer },
        onPanel   = role { onPrimaryContainer },
        night     = pinned ?: context.resources.configuration.isNight,
    )
}

/**
 * This colour as Glance sees it, so the composables carry both variants too — the return type is
 * left inferred because Glance's factory and the interface it builds share a name.
 */
internal fun DayNightColor.asColorProvider() = ColorProvider(day = Color(day), night = Color(night))

/**
 * Calls an int-taking colour method (`setTextColor`, `setColorFilter`, `setBackgroundColor`) with
 * both variants, leaving the choice to the platform at draw time. Below API 31 RemoteViews carry
 * one colour only, so the variant in force when the widget was built is used and the next refresh
 * corrects it.
 */
internal fun RemoteViews.setDayNightColor(viewId: Int, method: String, color: DayNightColor, night: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setColorInt(viewId, method, color.day, color.night)
    } else {
        setInt(viewId, method, if (night) color.night else color.day)
    }
}

/** Night mode as the resource system sees it — the same signal that picks `values-night`. */
private val Configuration.isNight: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
