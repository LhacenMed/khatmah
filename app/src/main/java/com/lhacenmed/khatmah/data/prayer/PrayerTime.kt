package com.lhacenmed.khatmah.data.prayer

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A single computed prayer with its local wall-clock time. */
data class PrayerTime(val name: String, val time: LocalTime)

/** 12-hour AM/PM format; always English locale so "AM/PM" is language-neutral. */
@RequiresApi(Build.VERSION_CODES.O)
fun LocalTime.toAmPm(): String =
    DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH).format(this)