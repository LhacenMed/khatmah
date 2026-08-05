package com.lhacenmed.khatmah.shared.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.edit
import com.google.android.material.color.DynamicColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.paletteOverlays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * The app's appearance: night mode, palette, and whether to follow the device's dynamic colours.
 *
 * It owns theming for the whole app rather than for Compose alone. [applyTo] resolves the choices
 * into a native theme on each Activity, so every XML layout painted with `?attr/…` and every
 * Compose screen — which reads the same attributes back, see
 * [com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme] — are drawn from one set of colours.
 *
 * A native theme is resolved once, at inflation, so a colour change cannot be applied to a
 * running Activity. Each change therefore bumps [version], and [attach] recreates Activities
 * still on an older one: the visible screen at once, the ones behind it as they come back.
 */
object ThemeManager {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_MODE = "night_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_COLOR_INDEX = "color_index"
    private const val KEY_HIGH_CONTRAST = "high_contrast"

    /**
     * How long a colour change waits, so the control that caused it is seen to move first.
     *
     * Short enough to read as the same action rather than a second one: the thumb is well on its
     * way before the screen repaints, and the repaint still feels like the result of the press.
     */
    private const val REPAINT_DELAY_MS = 100L

    private val _mode = MutableStateFlow(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    val mode: StateFlow<Int> = _mode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _colorIndex = MutableStateFlow(0)
    val colorIndex: StateFlow<Int> = _colorIndex.asStateFlow()

    private val _highContrast = MutableStateFlow(false)
    val highContrast: StateFlow<Boolean> = _highContrast.asStateFlow()

    /** Bumped on every colour change; an Activity themed at an older value is out of date. */
    private val _version = MutableStateFlow(0)

    /** The version each live Activity was themed at. Weak, so it never holds one alive. */
    private val themedAt = WeakHashMap<Activity, Int>()

    /** The foreground Activity, so a colour change can repaint it at once. */
    private var resumed = WeakReference<Activity>(null)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _mode.value = prefs.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        _dynamicColor.value = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        _colorIndex.value = prefs.getInt(KEY_COLOR_INDEX, 0)
        _highContrast.value = prefs.getBoolean(KEY_HIGH_CONTRAST, false)

        AppCompatDelegate.setDefaultNightMode(_mode.value)
    }

    // ── Applying the theme ────────────────────────────────────────────────────

    /**
     * Applies the resolved theme to [activity]. Call it first thing in `onCreate` — before
     * `setContentView`, and after `installSplashScreen` where there is one, since that installs a
     * theme of its own. Layered with `applyStyle` rather than `setTheme`, so it adds to whatever
     * theme the Activity already carries instead of replacing it.
     */
    fun applyTo(activity: Activity) {
        val theme = activity.theme
        if (useDynamicColor) {
            DynamicColors.applyToActivityIfAvailable(activity)
        } else {
            theme.applyStyle(overlayFor(_colorIndex.value), true)
        }
        if (_highContrast.value) {
            theme.applyStyle(R.style.ThemeOverlay_Khatmah_HighContrast, true)
        }
        themedAt[activity] = _version.value
    }

    /**
     * Watches Activities so a colour change reaches all of them: the one in front is recreated
     * the moment the change lands, and any left behind on an older version are recreated as they
     * resume. Call once, from `Application.onCreate`.
     */
    fun attach(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
                if (themedAt[activity] != _version.value) activity.recreate()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * A context carrying the app's theme resolved for a given [night] mode and palette — for
     * reading colours outside an Activity's own theme. [paletteIndex] defaults to the active
     * palette; the settings picker passes each one in turn to draw its swatches.
     */
    fun themedContext(
        context: Context,
        night: Boolean,
        paletteIndex: Int = _colorIndex.value,
    ): Context {
        val config = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val base = ContextThemeWrapper(
            context.createConfigurationContext(config),
            R.style.Theme_Khatmah,
        )
        // Asking for a palette other than the active one means asking for that palette itself —
        // which is what lets the picker keep drawing true swatches while Material You is on.
        val themed: Context = if (useDynamicColor && paletteIndex == _colorIndex.value) {
            DynamicColors.wrapContextIfAvailable(base)
        } else {
            base.also { it.theme.applyStyle(overlayFor(paletteIndex), true) }
        }
        if (_highContrast.value) {
            themed.theme.applyStyle(R.style.ThemeOverlay_Khatmah_HighContrast, true)
        }
        return themed
    }

    /**
     * Whether the device's own palette is in play. When Material You is unavailable the chosen
     * palette applies instead, so the picker keeps working on every device the app supports.
     */
    private val useDynamicColor: Boolean
        get() = _dynamicColor.value && DynamicColors.isDynamicColorAvailable()

    private fun overlayFor(index: Int): Int =
        paletteOverlays.getOrElse(index) { paletteOverlays[0] }

    /**
     * Records a colour change and repaints the screen the user is looking at.
     *
     * The repaint waits a beat. A colour change is nearly always made by pressing a control that
     * animates — a switch thumb sliding over, a swatch taking its ring — and recreating the
     * Activity in that same frame swaps the window out before any of it is drawn, so the control
     * appears to jump. The delay is long enough for the gesture to read and short enough that the
     * new colours still feel like its result.
     */
    private fun invalidateTheme() {
        _version.value++
        val activity = resumed.get() ?: return
        activity.window.decorView.postDelayed({
            // The screen may be on its way out by the time this runs.
            if (!activity.isFinishing && !activity.isDestroyed) activity.recreate()
        }, REPAINT_DELAY_MS)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Settings ──────────────────────────────────────────────────────────────

    fun getMode(context: Context): Int = _mode.value

    fun setMode(context: Context, mode: Int) {
        prefs(context).edit { putInt(KEY_MODE, mode) }
        _mode.value = mode
        // AppCompat recreates the Activities itself for a night-mode change, so this one does
        // not go through invalidateTheme — doing both would recreate twice.
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Follow the device's dynamic colours, or fall back to the chosen [colorIndex] palette. */
    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_DYNAMIC_COLOR, enabled) }
        _dynamicColor.value = enabled
        invalidateTheme()
    }

    /**
     * Picking a palette says "use this one, not the device's", so both preferences move together
     * — and the theme is invalidated once, not twice.
     */
    fun setPalette(context: Context, index: Int) {
        prefs(context).edit {
            putInt(KEY_COLOR_INDEX, index)
            putBoolean(KEY_DYNAMIC_COLOR, false)
        }
        _colorIndex.value = index
        _dynamicColor.value = false
        invalidateTheme()
    }

    fun setHighContrastEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_HIGH_CONTRAST, enabled) }
        _highContrast.value = enabled
        invalidateTheme()
    }
}
