package com.lhacenmed.khatmah.core.ui.components

import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape

/**
 * Circular ripple background sized for icon buttons.
 * The ripple colour is [rippleColor] at ~12% opacity so it works on both light and dark surfaces.
 */
internal fun createRippleDrawable(rippleColor: Int) =
    RippleDrawable(
        ColorStateList.valueOf(rippleColor and 0x1FFFFFFF),
        null,
        ShapeDrawable(OvalShape())
    )