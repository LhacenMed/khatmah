package com.lhacenmed.khatmah.shared.util

import android.view.HapticFeedbackConstants
import android.view.View

object HapticFeedback {
    fun View.slightHapticFeedback() =
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun View.longPressHapticFeedback() =
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}