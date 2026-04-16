package com.lhacenmed.khatmah.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level channel that carries a widget-tap navigation request to [MainScreen].
 *
 * Why [StateFlow] (not [SharedFlow]):
 * - [MainActivity] may call [request] in [onCreate], before [MainScreen]'s
 *   [LaunchedEffect] has started collecting. A [StateFlow] with replay-1 semantics
 *   guarantees the value is still present when the collector registers.
 * - Consuming via [consume] resets the value so the same route isn't re-applied on
 *   recomposition (e.g. after a configuration change).
 *
 * Lifecycle:
 *   [MainActivity] → [request]  (on widget-tap intent)
 *   [MainScreen]   → reads [route], switches tab, calls [consume]
 */
internal object WidgetNavRequest {

    private val _route = MutableStateFlow<String?>(null)

    /** Non-null while a widget-initiated navigation is pending. */
    val route: StateFlow<String?> = _route.asStateFlow()

    /** Signal that the app should navigate to [route]. */
    fun request(route: String) { _route.value = route }

    /** Mark the pending request as handled so it isn't re-applied on recomposition. */
    fun consume() { _route.value = null }
}