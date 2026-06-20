package com.lhacenmed.khatmah.feature.adhkar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide invalidation signal for the adhkar store.
 *
 * The adhkar tab, detail, and editor each run in their own activity, so each gets its
 * own [AdhkarViewModel][com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel] instance.
 * A write in one activity must therefore reach every other live VM. [AdhkarRepository]
 * bumps [changes] after every mutation; VMs observe it and re-fetch — the same reactive
 * contract Room's `Flow` queries provide, for this raw-SQLite store.
 *
 * [changes] is a [StateFlow] so new collectors get the current value immediately, which
 * doubles as the VM's initial load trigger.
 */
internal object AdhkarChangeNotifier {

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Signals that the adhkar store changed; every observing VM re-fetches. */
    fun notifyChange() = _changes.update { it + 1 }
}
