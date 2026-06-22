package com.lhacenmed.khatmah.feature.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for the app-update flow, mirroring [com.lhacenmed.khatmah.feature
 * .quran.data.download.DownloadRegistry]. Holds the [available] update (set by [UpdateChecker] on
 * launch) and the live [state] of its download (written by [UpdateService], observed by the UI).
 * Living outside any Activity means a config change — or dismissing the dialog — never loses an
 * in-flight APK download: re-opening simply re-reads the same flow.
 */
object UpdateRegistry {

    private val _available = MutableStateFlow<AppUpdate?>(null)
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Records that a newer version exists; the UI shows the prompt off this. */
    fun setAvailable(update: AppUpdate) { _available.value = update }

    fun update(state: UpdateState) { _state.value = state }

    fun stateOf(): UpdateState = _state.value

    /** True while the APK is connecting or transferring. */
    val isActive: Boolean
        get() = when (_state.value) {
            UpdateState.Connecting, is UpdateState.Downloading -> true
            else -> false
        }
}
