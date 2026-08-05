package com.lhacenmed.khatmah.core.ui

import android.view.View
import android.view.ViewGroup

/**
 * Strips the scrolling affordances from a view and everything under it — the fading scrollbar and
 * the thin lines a scrollable dialog draws above and below its content.
 *
 * Dialogs assemble their own hierarchy when they are shown, so there is no layout to set this on:
 * the platform's time picker, for one, wraps its wheels in a scroll view of its own making. Walking
 * the finished hierarchy is the only place the setting can be applied, and it leaves the widget
 * itself untouched — this changes what is drawn over the content, not the content.
 */
fun View.hideScrollIndicators() {
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    scrollIndicators = 0
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).hideScrollIndicators()
    }
}
