package com.lhacenmed.khatmah.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.lhacenmed.khatmah.shared.util.ThemeManager
import com.google.android.material.R as MaterialR

/** Walks the context chain to the hosting Activity's Window (for system-bar control). */
private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity       -> window
    is ContextWrapper -> baseContext.findWindow()
    else              -> null
}

/** Whether the app should render dark, from the user's mode + the (forced) configuration. */
fun isAppInDarkTheme(context: Context): Boolean = when (ThemeManager.mode.value) {
    AppCompatDelegate.MODE_NIGHT_YES -> true
    AppCompatDelegate.MODE_NIGHT_NO  -> false
    else -> context.resources.configuration.isNight
}

/** Night mode as the resource system sees it — the same signal that picks `values-night`. */
private val Configuration.isNight: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/**
 * The Material 3 colour roles read back out of the active theme, sorted so a single
 * [Resources.Theme.obtainStyledAttributes] call can resolve all of them — building a
 * [ColorScheme] then costs one pass over the theme rather than one lookup per role.
 */
private val ColorAttrs = intArrayOf(
    MaterialR.attr.colorPrimary,
    MaterialR.attr.colorOnPrimary,
    MaterialR.attr.colorPrimaryContainer,
    MaterialR.attr.colorOnPrimaryContainer,
    MaterialR.attr.colorPrimaryInverse,
    MaterialR.attr.colorSecondary,
    MaterialR.attr.colorOnSecondary,
    MaterialR.attr.colorSecondaryContainer,
    MaterialR.attr.colorOnSecondaryContainer,
    MaterialR.attr.colorTertiary,
    MaterialR.attr.colorOnTertiary,
    MaterialR.attr.colorTertiaryContainer,
    MaterialR.attr.colorOnTertiaryContainer,
    MaterialR.attr.colorError,
    MaterialR.attr.colorOnError,
    MaterialR.attr.colorErrorContainer,
    MaterialR.attr.colorOnErrorContainer,
    android.R.attr.colorBackground,
    MaterialR.attr.colorOnBackground,
    MaterialR.attr.colorSurface,
    MaterialR.attr.colorOnSurface,
    MaterialR.attr.colorSurfaceVariant,
    MaterialR.attr.colorOnSurfaceVariant,
    MaterialR.attr.colorSurfaceInverse,
    MaterialR.attr.colorOnSurfaceInverse,
    MaterialR.attr.colorOutline,
    MaterialR.attr.colorOutlineVariant,
    MaterialR.attr.colorSurfaceBright,
    MaterialR.attr.colorSurfaceDim,
    MaterialR.attr.colorSurfaceContainer,
    MaterialR.attr.colorSurfaceContainerLowest,
    MaterialR.attr.colorSurfaceContainerLow,
    MaterialR.attr.colorSurfaceContainerHigh,
    MaterialR.attr.colorSurfaceContainerHighest,
).sortedArray()

/** Resolves every [ColorAttrs] role in one pass and hands [block] a lookup keyed by attribute. */
private inline fun <T> Resources.Theme.withColors(block: (color: (Int) -> Color) -> T): T {
    val typed = obtainStyledAttributes(ColorAttrs)
    try {
        return block { attr -> Color(typed.getColor(ColorAttrs.binarySearch(attr), 0)) }
    } finally {
        typed.recycle()
    }
}

/**
 * The active [ColorScheme], read out of [context]'s own theme.
 *
 * The native theme is the app's single source of colour truth: [ThemeManager] applies the
 * palette (or the device's dynamic one) to each Activity, the XML layouts read it through
 * `?attr/…`, and this reads the very same resolved attributes — so Compose content and native
 * views are painted from one set of values and cannot disagree.
 *
 * Roles the app never overrides (scrim, surface tint) keep their Material baseline.
 */
fun resolveColorScheme(context: Context): ColorScheme {
    val base = if (context.resources.configuration.isNight) darkColorScheme() else lightColorScheme()
    return context.theme.withColors { color ->
        base.copy(
            primary               = color(MaterialR.attr.colorPrimary),
            onPrimary             = color(MaterialR.attr.colorOnPrimary),
            primaryContainer      = color(MaterialR.attr.colorPrimaryContainer),
            onPrimaryContainer    = color(MaterialR.attr.colorOnPrimaryContainer),
            inversePrimary        = color(MaterialR.attr.colorPrimaryInverse),
            secondary             = color(MaterialR.attr.colorSecondary),
            onSecondary           = color(MaterialR.attr.colorOnSecondary),
            secondaryContainer    = color(MaterialR.attr.colorSecondaryContainer),
            onSecondaryContainer  = color(MaterialR.attr.colorOnSecondaryContainer),
            tertiary              = color(MaterialR.attr.colorTertiary),
            onTertiary            = color(MaterialR.attr.colorOnTertiary),
            tertiaryContainer     = color(MaterialR.attr.colorTertiaryContainer),
            onTertiaryContainer   = color(MaterialR.attr.colorOnTertiaryContainer),
            error                 = color(MaterialR.attr.colorError),
            onError               = color(MaterialR.attr.colorOnError),
            errorContainer        = color(MaterialR.attr.colorErrorContainer),
            onErrorContainer      = color(MaterialR.attr.colorOnErrorContainer),
            background            = color(android.R.attr.colorBackground),
            onBackground          = color(MaterialR.attr.colorOnBackground),
            surface               = color(MaterialR.attr.colorSurface),
            onSurface             = color(MaterialR.attr.colorOnSurface),
            surfaceVariant        = color(MaterialR.attr.colorSurfaceVariant),
            onSurfaceVariant      = color(MaterialR.attr.colorOnSurfaceVariant),
            inverseSurface        = color(MaterialR.attr.colorSurfaceInverse),
            inverseOnSurface      = color(MaterialR.attr.colorOnSurfaceInverse),
            outline               = color(MaterialR.attr.colorOutline),
            outlineVariant        = color(MaterialR.attr.colorOutlineVariant),
            surfaceBright         = color(MaterialR.attr.colorSurfaceBright),
            surfaceDim            = color(MaterialR.attr.colorSurfaceDim),
            surfaceContainer      = color(MaterialR.attr.colorSurfaceContainer),
            surfaceContainerLowest  = color(MaterialR.attr.colorSurfaceContainerLowest),
            surfaceContainerLow     = color(MaterialR.attr.colorSurfaceContainerLow),
            surfaceContainerHigh    = color(MaterialR.attr.colorSurfaceContainerHigh),
            surfaceContainerHighest = color(MaterialR.attr.colorSurfaceContainerHighest),
        )
    }
}

/**
 * The active [ColorScheme] as it would be in a given [night] mode, regardless of the app's own.
 * The reader uses this: its day/night switch is independent of the app theme, but the accent it
 * draws with must still be the palette the user chose.
 */
fun resolveColorScheme(context: Context, night: Boolean): ColorScheme =
    resolveColorScheme(ThemeManager.themedContext(context, night))

@Composable
fun Theme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Read from the host Activity's theme, which ThemeManager has already resolved. A colour
    // change recreates the Activity (see ThemeManager.attach), so a new context — and with it a
    // fresh scheme — is exactly what arrives when the palette or night mode changes.
    val colorScheme = remember(context) { resolveColorScheme(context) }
    val typography  = remember(context) { resolveTypography(context) }
    val isDark = context.resources.configuration.isNight

    // Keep system-bar icon appearance in step with the resolved theme, so every Activity
    // (host, details, onboarding) shows correct status-bar icons even when the app's theme
    // differs from the system night mode (e.g. app forced Dark while the system is Light).
    val view   = LocalView.current
    val window = view.context.findWindow()
    if (!view.isInEditMode && window != null) {
        SideEffect {
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = typography,
        content     = content,
    )
}
