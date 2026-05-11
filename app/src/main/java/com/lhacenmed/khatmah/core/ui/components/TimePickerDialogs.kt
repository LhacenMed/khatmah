package com.lhacenmed.khatmah.core.ui.components

import android.app.TimePickerDialog
import android.content.Context

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
    TimePickerDialog(
        context,
        { _, h, m -> onTimeSet(h, m) },
        hour,
        minute,
        true, // 24-hour
    ).show()
}