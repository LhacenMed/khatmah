package com.lhacenmed.khatmah.core.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects [flow] while this fragment's view is at least STARTED, and stops when it is not.
 *
 * Scoped to the view rather than the fragment, so a body that is torn down and rebuilt — a tab
 * hidden and shown, a screen returned to — collects once per view rather than accumulating a
 * collector each time.
 */
fun <T> Fragment.collectWhileStarted(flow: Flow<T>, action: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}
