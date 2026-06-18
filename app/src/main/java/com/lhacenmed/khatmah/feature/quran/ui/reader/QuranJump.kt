package com.lhacenmed.khatmah.feature.quran.ui.reader

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot bus carrying a (suraNum, ayaNum) jump request from the search screen
 * back to the reader that launched it.
 *
 * Mirrors the existing WidgetNavRequest idiom: the reader keeps collecting while it
 * sits (stopped, not destroyed) under the search activity, so an emit made just
 * before search finishes is delivered when the reader resumes. [extraBufferCapacity]
 * keeps [request] non-suspending.
 */
object QuranJump {
    private val _request = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 1)
    val request = _request.asSharedFlow()

    fun request(suraNum: Int, ayaNum: Int) {
        _request.tryEmit(suraNum to ayaNum)
    }
}
