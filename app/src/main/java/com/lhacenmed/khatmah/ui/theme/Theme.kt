package com.lhacenmed.khatmah.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lhacenmed.khatmah.util.ThemeManager
import androidx.appcompat.app.AppCompatDelegate

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

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> colorPreferences
            .getOrElse(colorIndex) { colorPreferences[0] }
            .run { if (isDark) darkScheme else lightScheme }
    }.let { scheme ->
        if (highContrast && isDark)
            scheme.copy(surface = Color.Black, background = Color.Black)
        else scheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
