package com.lhacenmed.khatmah.core

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.databinding.ActivityScreenHostBinding

/**
 * The single host Activity for [Dest.screen] destinations — so adding such a screen never
 * touches the manifest (this one entry serves them all). Each navigation launches a fresh
 * instance, so the platform keeps owning the back stack, predictive-back and transitions.
 *
 * Destinations that set a [Dest.titleRes] get the app's native [MaterialToolbar] — the same
 * chrome the tabs use — with a platform back arrow that mirrors automatically in RTL; their
 * composable provides only the body. Destinations without a title render full-screen (legacy
 * pages that still draw their own top bar).
 */
class ScreenHostActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val dest = (intent.getSerializableExtra(EXTRA_DEST) as? Dest) ?: return finish()
        val content = dest.screen() ?: return finish()

        val binding = ActivityScreenHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = dest.title(this)
        val hasChrome = title != null
        if (hasChrome) {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true) // platform back arrow, mirrored in RTL
            supportActionBar?.title = title
            supportActionBar?.subtitle = dest.subtitle(this)
            // Route the up arrow through the back dispatcher so a page's BackHandler (e.g. a
            // multi-step wizard) intercepts it exactly like system back; otherwise it finishes.
            binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            applyChrome(binding.root, binding.toolbar, resolveColorScheme(this, isAppInDarkTheme(this)))
            applyTopInset(binding.root)
        } else {
            binding.toolbar.visibility = View.GONE
        }

        val navigator = IntentNavigator(this)
        binding.composeView.setContent {
            Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalNavigator provides navigator) {
                        // With the native toolbar above, the body only needs the bottom insets;
                        // legacy full-screen pages handle their own insets via their Scaffold.
                        if (hasChrome) {
                            Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { content() }
                        } else {
                            content()
                        }
                    }
                }
            }
        }
    }

    /**
     * Paint the native toolbar from the Compose colour scheme so it matches the tabs' chrome.
     * The bar uses [surfaceContainer][ColorScheme.surfaceContainer] (like the bottom nav) to
     * stand out from the [surface][ColorScheme.surface] body; the root shares that colour so the
     * status-bar inset region blends into the bar.
     */
    private fun applyChrome(root: View, toolbar: MaterialToolbar, scheme: ColorScheme) {
        val barColor = scheme.surfaceContainer.toArgb()
        window.setBackgroundDrawable(ColorDrawable(scheme.surface.toArgb()))
        root.setBackgroundColor(barColor)
        toolbar.setBackgroundColor(barColor)
        toolbar.setTitleTextColor(scheme.onSurface.toArgb())
        toolbar.setSubtitleTextColor(scheme.onSurfaceVariant.toArgb())
        toolbar.navigationIcon?.setTint(scheme.onSurfaceVariant.toArgb())
    }

    /**
     * Push the whole bar below the status bar via the root's top padding (edge-to-edge). Padding
     * the root rather than the toolbar keeps the toolbar a clean actionBarSize, so its title and
     * back arrow stay vertically aligned.
     */
    private fun applyTopInset(root: View) {
        root.updatePadding(top = statusBarHeight())
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            if (top > 0) v.updatePadding(top = top)
            insets
        }
    }

    /** Platform status-bar height, resolved synchronously (no inset dispatch required). */
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    companion object {
        const val EXTRA_DEST = "dest"
    }
}
