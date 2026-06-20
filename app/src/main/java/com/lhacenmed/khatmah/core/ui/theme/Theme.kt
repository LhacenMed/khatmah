package com.lhacenmed.khatmah.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.lhacenmed.khatmah.shared.util.ThemeManager

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
    else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

/**
 * Builds the active [ColorScheme] from the theme inputs — the single source of truth shared by
 * [Theme] (Compose content) and the host Activity (which colours its XML chrome before the first
 * frame so a theme switch never flashes baseline colours).
 */
fun buildColorScheme(
    context: Context,
    isDark: Boolean,
    dynamicColor: Boolean,
    colorIndex: Int,
    highContrast: Boolean,
): ColorScheme {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> colorPreferences.getOrElse(colorIndex) { colorPreferences[0] }
            .run { if (isDark) darkScheme else lightScheme }
    }
    return if (highContrast && isDark) base.copy(surface = Color.Black, background = Color.Black) else base
}

/** Snapshot resolve for non-Compose callers (reads current [ThemeManager] values). */
fun resolveColorScheme(context: Context, isDark: Boolean): ColorScheme = buildColorScheme(
    context, isDark,
    ThemeManager.dynamicColor.value,
    ThemeManager.colorIndex.value,
    ThemeManager.highContrast.value,
)

@Composable
fun Theme(
    content: @Composable () -> Unit
) {
    val context      = LocalContext.current
    val mode         by ThemeManager.mode.collectAsState()
    val dynamicColor by ThemeManager.dynamicColor.collectAsState()
    val colorIndex   by ThemeManager.colorIndex.collectAsState()
    val highContrast by ThemeManager.highContrast.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO  -> false
        else -> isSystemDark
    }

    val colorScheme = buildColorScheme(context, isDark, dynamicColor, colorIndex, highContrast)

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
        typography  = Typography,
        content     = content,
    )
}
