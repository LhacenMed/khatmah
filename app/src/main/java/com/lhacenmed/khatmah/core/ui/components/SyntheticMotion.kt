package com.lhacenmed.khatmah.core.ui.components

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * Sends a synthetic MotionEvent to this view and immediately recycles it.
 * y is fixed at 0f so system tooltips always position above the anchor view,
 * regardless of where the finger sits within the originating touch target.
 */
internal fun View.dispatchSyntheticMotion(
    action: Int, downTime: Long, eventTime: Long, x: Float,
) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, 0f, 0)
    dispatchTouchEvent(event)
    event.recycle()
}