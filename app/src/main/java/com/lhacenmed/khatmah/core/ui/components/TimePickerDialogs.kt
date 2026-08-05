package com.lhacenmed.khatmah.core.ui.components

import android.app.TimePickerDialog
import android.content.Context
import com.lhacenmed.khatmah.core.ui.hideScrollIndicators

/**
 * Opens the system-native 24-hour time-picker dialog.
 * Call from any click handler; no Compose context required.
 */
fun showTimePicker(
    context:    Context,
    hour:       Int,
    minute:     Int,
    onTimeSet:  (hour: Int, minute: Int) -> Unit,
) {
    val dialog = TimePickerDialog(
        context,
        { _, h, m -> onTimeSet(h, m) },
        hour,
        minute,
        true, // 24-hour
    )
    // The picker itself is the platform's and stays that way. Only the scrollbar and the indicator
    // lines its host scroll view draws over the wheels come off, so the dialog matches the rest of
    // the app. Done on show, because a dialog builds its hierarchy as it is shown.
    dialog.setOnShowListener { dialog.window?.decorView?.hideScrollIndicators() }
    dialog.show()
}
