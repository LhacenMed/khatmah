package com.lhacenmed.khatmah.core

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.khatmah.core.nav.AppEntry
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.today.TodayViewModel
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.PrayerWidget
import com.lhacenmed.khatmah.widget.WidgetAction
import com.lhacenmed.khatmah.widget.WidgetNavRequest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() so the system sets up the
        // splash window before any content is drawn.
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Hoist TodayViewModel to Activity scope here — the earliest possible
        // moment after super.onCreate(). Its Room flow chain starts immediately,
        // so data is in flight before setContent ever runs.
        val todayVm = ViewModelProvider(
            this,
            TodayViewModel.Factory(applicationContext),
        )[TodayViewModel::class.java]

        // Only hold for data when onboarding is complete; during onboarding the
        // flow itself provides visual continuity and TodayTab is never composed.
        val holdForData = OnboardingPrefs.isComplete(this)

        // Keep the splash visible until TodayTab's SideEffect confirms Compose has
        // committed a real-data frame. This is the critical difference from checking
        // the StateFlow directly: splashReady is only set AFTER Compose has finished
        // composing with non-Loading state, so the splash never exits to a shimmer.
        splashScreen.setKeepOnScreenCondition {
            holdForData && !todayVm.splashReady
        }

        // Handle a widget tap or reminder tap that cold-started the app.
        handleLaunchIntent(intent)

        // Observe prayer settings changes while the app is active so the widget
        // refreshes immediately — covers split-screen and freeform window modes
        // where onStop never fires even when the widget is visible.
        observeSettingsForWidget()

        setContent {
            Theme {
                // Surface fills the window with MaterialTheme.colorScheme.background,
                // covering the XML windowBackground so it never bleeds through during
                // NavHost transition frames where composables render at reduced alpha.
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppEntry()
                }
            }
        }
    }

    /**
     * Called when the widget is tapped while the app is already running.
     * [FLAG_ACTIVITY_SINGLE_TOP] on the widget intent ensures this path is taken
     * instead of creating a new [MainActivity] instance.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Keep getIntent() in sync for any downstream readers.
        handleLaunchIntent(intent)
    }

    /**
     * Refresh the widget whenever the user leaves the app — covers:
     * - Returning to the home screen after changing prayer settings or language.
     * - First launch after onboarding completes.
     * - Normal foreground → background transitions.
     */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { PrayerWidget().updateAll(this@MainActivity) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Routes widget-tap and reminder-tap intents to [WidgetNavRequest] so [AppEntry]
     * can switch to the correct tab or navigate to a detail screen.
     *
     * Simple route strings ("prayers", "adhkar", "today") map to their Route constants.
     * All other non-null strings (e.g. "adhkar_detail/morning") are passed through
     * directly so [AppEntry] can call navController.navigate() for deep links.
     *
     * Called from both [onCreate] (cold start) and [onNewIntent] (warm start).
     */
    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            WidgetAction.OPEN_PRAYERS        -> WidgetNavRequest.request("prayers")
            "com.lhacenmed.khatmah.REMINDER" -> {
                // Route strings from ReminderNotifier.defaultDeepLink match Route constants directly.
                val route = when (val raw = intent.getStringExtra("route")) {
                    "prayers" -> "prayers"
                    "adhkar"  -> "adhkar"
                    "today"   -> "today"
                    else      -> raw   // pass through deep-link routes (e.g. "adhkar_detail/morning")
                }
                route?.let { WidgetNavRequest.request(it) }
            }
        }
    }

    /**
     * While the activity is at least STARTED, push a widget update on every
     * settings save (after the initial emission is skipped via [drop]).
     *
     * [repeatOnLifecycle] cancels the collection when the activity goes to the
     * background, so updates are never double-counted with [onStop].
     */
    private fun observeSettingsForWidget() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PrayerSettings.flow
                    .drop(1) // Skip the initial/current value — widget is already up to date.
                    .collect { PrayerWidget().updateAll(this@MainActivity) }
            }
        }
    }
}