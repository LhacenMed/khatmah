package com.lhacenmed.khatmah.core

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.theme.Theme

/**
 * Base for every secondary (full-screen) destination. Each detail screen is its own
 * Activity, so the platform owns back navigation, predictive-back and transitions —
 * no custom nav stack or motion seek.
 *
 * Enables edge-to-edge once and hosts Compose under the shared [Theme] + a [Surface]
 * that paints the window background (so transition frames never bleed the XML
 * windowBackground through reduced-alpha composables).
 */
open class BaseComposeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /** Full-screen Compose host, with the intent-based [LocalNavigator] in scope. */
    protected fun setAppContent(content: @Composable () -> Unit) {
        val navigator = IntentNavigator(this)
        setContent {
            Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        content()
                    }
                }
            }
        }
    }
}
