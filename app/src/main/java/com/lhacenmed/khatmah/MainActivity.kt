package com.lhacenmed.khatmah

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
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.khatmah.data.prayer.PrayerSettings
import com.lhacenmed.khatmah.ui.page.AppEntry
import com.lhacenmed.khatmah.ui.theme.Theme
import com.lhacenmed.khatmah.widget.PrayerWidget
import com.lhacenmed.khatmah.widget.WidgetAction
import com.lhacenmed.khatmah.widget.WidgetNavRequest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Handle a widget tap that cold-started the app.
        handleWidgetIntent(intent)

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
        handleWidgetIntent(intent)
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
     * Routes a widget-tap intent to [WidgetNavRequest] so [AppEntry]'s [MainScreen]
     * can switch to the correct tab.
     *
     * Called from both [onCreate] (cold start) and [onNewIntent] (warm start).
     */
    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            WidgetAction.OPEN_PRAYERS -> WidgetNavRequest.request(
                com.lhacenmed.khatmah.ui.common.Route.PRAYERS
            )
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