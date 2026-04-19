package com.lhacenmed.khatmah.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Per-tab scroll-to-top signal.
 * Each tab's content collects this flow and animates its scroll state to the top on emission.
 * Provided by MainScreen; defaults to a no-op empty flow so tabs are safe without a provider.
 */
val LocalScrollToTop = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }