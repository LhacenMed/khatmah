package com.lhacenmed.khatmah.core

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.commit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.fitTitleText
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.databinding.ActivityScreenHostBinding

/**
 * The single host Activity for [Dest.screen] destinations — so adding such a screen never
 * touches the manifest (this one entry serves them all). Each navigation launches a fresh
 * instance, so the platform keeps owning the back stack, predictive-back and transitions.
 *
 * Destinations that set a [Dest.titleRes] get the app's native [MaterialToolbar] — the same
 * chrome the tabs use — with a platform back arrow that mirrors automatically in RTL; their
 * body provides only the content. Destinations without a title render full-screen (legacy
 * pages that still draw their own top bar).
 *
 * A body is either Compose ([Dest.screen]) or a native [Dest.fragment]. Fragments contribute
 * their own toolbar actions through the standard [androidx.core.view.MenuProvider] API, so the
 * host needs no per-destination action plumbing.
 */
class ScreenHostActivity : BaseComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val dest = (intent.getSerializableExtra(EXTRA_DEST) as? Dest) ?: return finish()
        val content = dest.screen()
        val body = dest.fragment()
        if (content == null && body == null) return finish()

        val binding = ActivityScreenHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The root wears the bar's colour so the status-bar strip continues the toolbar, which
        // leaves the body to say where the page itself begins. A Compose body paints that with its
        // own Surface; a fragment body paints nothing, and without this would show the bar's colour
        // for its whole height.
        binding.body.setBackgroundColor(
            MaterialColors.getColor(binding.body, com.google.android.material.R.attr.colorSurface)
        )

        val title = dest.title(this)
        val hasChrome = title != null
        if (hasChrome) {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true) // platform back arrow, mirrored in RTL
            supportActionBar?.title = title
            supportActionBar?.subtitle = dest.subtitle(this)
            binding.toolbar.fitTitleText() // drop the tall font's padding so the subtitle never clips
            // Route the up arrow through the back dispatcher so a page's BackHandler (e.g. a
            // multi-step wizard) intercepts it exactly like system back; otherwise it finishes.
            binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
            applyChrome(binding.root, binding.toolbar, resolveColorScheme(this))
            applyTopInset(binding.root)
        } else {
            binding.toolbar.visibility = View.GONE
        }

        if (body != null) {
            // The FragmentManager restores the body itself after a configuration change.
            if (savedInstanceState == null) {
                supportFragmentManager.commit { replace(binding.body.id, body) }
            }
            return
        }

        val screen = content ?: return
        val navigator = IntentNavigator(this)
        val composeView = ComposeView(this).apply {
            setContent {
                Theme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalNavigator provides navigator) {
                            // With the native toolbar above, the body only needs the bottom
                            // insets; legacy full-screen pages handle their own insets via
                            // their Scaffold.
                            if (hasChrome) {
                                Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { screen() }
                            } else {
                                screen()
                            }
                        }
                    }
                }
            }
        }
        binding.body.addView(
            composeView,
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
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
     * the root rather than the toolbar keeps the toolbar a clean fixed height, so its title and
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
