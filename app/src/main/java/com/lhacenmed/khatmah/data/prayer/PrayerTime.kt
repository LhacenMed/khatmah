package com.lhacenmed.khatmah.data.prayer

import java.time.LocalTime

/** A single computed prayer with its local wall-clock time. */
data class PrayerTime(val name: String, val time: LocalTime)