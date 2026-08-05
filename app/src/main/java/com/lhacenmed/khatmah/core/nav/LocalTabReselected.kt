package com.lhacenmed.khatmah.core.nav

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Per-tab re-selection signal: emitted when the bar item of the tab already showing is tapped again.
 *
 * What that means is each tab's own business — the list tabs scroll back to the top, the Quran tab
 * reopens the mushaf where reading stopped. Provided by MainScreen; defaults to a no-op empty flow
 * so tabs are safe without a provider.
 */
val LocalTabReselected = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }