package com.lhacenmed.khatmah.core.ui

import android.text.TextUtils
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

/**
 * Trims the tall Noto Kufi font's extra vertical space (`includeFontPadding`) from a [Toolbar]'s
 * built-in title & subtitle, so a stacked title + subtitle always fits the bar height without
 * clipping — the same font-padding drop the Compose Material3 top bar does by default. Each line is
 * also kept to a single ellipsized line.
 *
 * [Toolbar] creates the title/subtitle [TextView]s synchronously when their text is set, so call
 * this right after setting them (no post/layout wait needed). It is idempotent, so it is safe to
 * call after every title/subtitle change — e.g. the reader updating its meta per page.
 */
fun Toolbar.fitTitleText() {
    for (i in 0 until childCount) {
        (getChildAt(i) as? TextView)?.apply {
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
    }
}
