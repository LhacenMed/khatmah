package com.lhacenmed.khatmah.core

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.lhacenmed.khatmah.core.ui.UiScale
import com.lhacenmed.khatmah.shared.util.ThemeManager

/**
 * Base for the Compose host Activity ([ScreenHostActivity]). Enables edge-to-edge once and
 * provides an AppCompat context (so a [com.google.android.material.appbar.MaterialToolbar]
 * can serve as the support action bar). The platform owns back, predictive-back and
 * transitions — no custom nav stack or motion seek.
 */
open class BaseComposeActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTo(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}
