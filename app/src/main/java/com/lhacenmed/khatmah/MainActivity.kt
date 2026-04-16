package com.lhacenmed.khatmah

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.khatmah.ui.page.AppEntry
import com.lhacenmed.khatmah.ui.theme.Theme
import com.lhacenmed.khatmah.widget.PrayerWidget
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
     * Refresh the widget whenever the user leaves the app — covers:
     * - Returning to the home screen after changing prayer settings or language.
     * - First launch after onboarding completes.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { PrayerWidget().updateAll(this@MainActivity) }
    }
}