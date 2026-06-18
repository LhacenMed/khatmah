package com.lhacenmed.khatmah.core

import android.os.Bundle
import com.lhacenmed.khatmah.core.nav.Dest

/**
 * The single host Activity for screens that live as a [Dest.screen] composable rather than
 * their own Activity class. It renders the [Dest] it was launched with — so adding such a
 * screen never touches the manifest (this one entry serves them all).
 *
 * Each navigation launches a fresh instance, so the platform keeps owning the back stack,
 * predictive-back and transitions exactly as it does for separate Activities.
 */
class ScreenHostActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val content = (intent.getSerializableExtra(EXTRA_DEST) as? Dest)?.screen()
        if (content == null) { finish(); return }
        setAppContent { content() }
    }

    companion object {
        const val EXTRA_DEST = "dest"
    }
}
