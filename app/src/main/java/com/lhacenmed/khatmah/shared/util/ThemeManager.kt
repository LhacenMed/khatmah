package com.lhacenmed.khatmah.shared.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "night_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_COLOR_INDEX = "color_index"
    private const val KEY_HIGH_CONTRAST = "high_contrast"

    private val _mode = MutableStateFlow(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    val mode: StateFlow<Int> = _mode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _colorIndex = MutableStateFlow(0)
    val colorIndex: StateFlow<Int> = _colorIndex.asStateFlow()

    private val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _mode.value = prefs.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        _colorIndex.value = prefs.getInt(KEY_COLOR_INDEX, 0)
        _highContrast.value = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        
        AppCompatDelegate.setDefaultNightMode(_mode.value)
    }

    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context))
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_MODE, mode) }
        _mode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getMode(context: Context): Int = _mode.value

    fun isDynamicColorEnabled(context: Context): Boolean = _dynamicColor.value

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
        _dynamicColor.value = enabled
    }

    fun getColorIndex(context: Context): Int = _colorIndex.value

    fun setColorIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_COLOR_INDEX, index) }
        _colorIndex.value = index
    }

    fun isHighContrastEnabled(context: Context): Boolean = _highContrast.value

    fun setHighContrastEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_HIGH_CONTRAST, enabled) }
        _highContrast.value = enabled
    }
}
