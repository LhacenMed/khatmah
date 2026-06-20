package com.lhacenmed.khatmah.core.nav

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import com.lhacenmed.khatmah.core.ScreenHostActivity

/**
 * Builds the launch [Intent] for a [Dest]. Host-model screens (those with a
 * [screen][Dest.screen]) launch the shared [ScreenHostActivity] carrying the [Dest];
 * legacy screens launch their own [target][Dest.target] with typed [extras][Dest.extras].
 * Generic by design — adding a destination never touches this file.
 */
fun Dest.toIntent(context: Context): Intent =
    if (screen() != null) {
        // Carry the Dest for the host, plus the same flat [extras] — so a screen whose
        // ViewModel reads them from its SavedStateHandle keeps working unchanged.
        Intent(context, ScreenHostActivity::class.java)
            .putExtra(ScreenHostActivity.EXTRA_DEST, this)
            .also { extras(it) }
    } else {
        Intent(context, target!!).also { extras(it) }
    }

/**
 * [AppNavigator] backed by Activity intents. Provided by every host Activity so any
 * composable can navigate ([go]) or pop the current screen ([back]) with the platform
 * owning the back stack, transitions and predictive back.
 */
class IntentNavigator(private val activity: ComponentActivity) : AppNavigator {
    override fun go(dest: Dest) = activity.startActivity(dest.toIntent(activity))

    override fun back() {
        activity.finish()
    }
}
