package com.lhacenmed.khatmah.core.nav

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.lhacenmed.khatmah.core.ui.theme.Theme
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Hosts a [ComposeTab]'s body inside the fragment-based tab host, and supplies the two things
 * its composables expect from the host: an [AppNavigator] and the re-selection signal.
 *
 * The tab is looked up by [route] rather than passed in, because a fragment is rebuilt from its
 * arguments after process death — a string survives that, an object reference does not.
 *
 * Temporary by design: it exists only while tabs are still Compose, and is deleted with the last
 * of them.
 */
class ComposeTabFragment : Fragment(), Reselectable {

    /** Re-selection taps, replayed to the tab's composables through [LocalTabReselected]. */
    private val reselected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onReselect() {
        reselected.tryEmit(Unit)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val route = requireArguments().getString(ARG_ROUTE)
        val tab = AppTabs.first { it.route == route } as ComposeTab
        val navigator = IntentNavigator(requireActivity() as ComponentActivity)

        return ComposeView(requireContext()).apply {
            // The host hides tabs rather than removing them, so the composition must outlive a
            // hide and be released with the fragment's view — not with the window.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                Theme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalNavigator provides navigator,
                            LocalTabReselected provides reselected,
                        ) {
                            // The host applies the window insets, so the body gets none of its own.
                            tab.Content(PaddingValues())
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_ROUTE = "route"

        fun newInstance(route: String) = ComposeTabFragment().apply {
            arguments = bundleOf(ARG_ROUTE to route)
        }
    }
}
