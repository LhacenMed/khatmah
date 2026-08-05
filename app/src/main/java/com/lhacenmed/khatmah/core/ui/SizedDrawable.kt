package com.lhacenmed.khatmah.core.ui

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

/**
 * Reports a fixed square intrinsic size; drawing and tinting delegate to [wrapped].
 *
 * The app's icons are authored at whatever size their source asset happened to be — some at 24dp,
 * some at 48dp for full-bleed use in the bottom bar. Anywhere icons are laid out side by side and
 * sized from the drawable itself (toolbar actions, preference rows), that difference shows. This
 * settles the size without touching the vector, so a tint applied to it still lands.
 */
class SizedDrawable(wrapped: Drawable, private val sizePx: Int) : DrawableWrapper(wrapped) {
    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}
