package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R

/**
 * Hosts the reader's settings fragment. Native toolbar with a platform back arrow; the body is
 * [ReaderSettingsFragment].
 *
 * Edge-to-edge: the toolbar covers the status-bar strip (surfaceContainer) and the body sits under
 * the nav bar (surface), so each system-bar strip matches the region it overlays.
 */
class ReaderSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.book_reader_settings_activity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val container = findViewById<View>(R.id.settings_container)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "إعدادات القارئ"
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Match the reader's surface-coloured chrome.
        val bar = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorSurfaceContainer)
        val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        toolbar.setBackgroundColor(bar)
        toolbar.setTitleTextColor(onSurface)
        toolbar.navigationIcon?.setTint(onSurface)

        // Push the toolbar below the status bar and the body content above the nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
    }
}
