package com.lhacenmed.khatmah.data.prayer

/**
 * Cached prayer time row.
 *
 * [date] is an ISO-8601 string ("yyyy-MM-dd") so sorting by it lexicographically
 * is identical to sorting chronologically — no date parsing needed in queries.
 */
data class PrayerEntity(
    val id: Long = 0,
    val date: String,   // "yyyy-MM-dd"
    val name: String,
    val hour: Int,
    val minute: Int,
)