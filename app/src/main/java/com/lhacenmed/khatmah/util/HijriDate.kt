package com.lhacenmed.khatmah.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate

/**
 * Converts Gregorian dates to the Tabular Islamic (Hijri) Calendar.
 *
 * Uses the Kuwaiti / De Blois algorithm via Julian Day Number.
 * Verified: 13 April 2026 → 25 Shawwal 1447.
 *
 * Note: Differs from the observational (Umm al-Qura) calendar by 0–2 days
 * around month boundaries, which is acceptable for display purposes.
 */
object HijriDate {

    data class Date(val day: Int, val month: Int, val year: Int)

    @RequiresApi(Build.VERSION_CODES.O)
    fun fromGregorian(date: LocalDate): Date =
        fromJdn(toJdn(date.year, date.monthValue, date.dayOfMonth))

    // ── Gregorian → Julian Day Number ─────────────────────────────────────────

    private fun toJdn(y: Int, m: Int, d: Int): Long {
        val a  = (14 - m) / 12
        val yr = y + 4800 - a
        val mo = m + 12 * a - 3
        return d + (153 * mo + 2) / 5 + 365L * yr + yr / 4 - yr / 100 + yr / 400 - 32045
    }

    // ── Julian Day Number → Hijri date ─────────────────────────────────────────

    private fun fromJdn(jdn: Long): Date {
        var l = jdn - 1948440 + 10632
        val n = (l - 1) / 10631
        l    -= 10631 * n - 354
        val j = (10985 - l) / 5316 * (50 * l / 17719) +
                (l / 5670) * (43 * l / 15238)
        l = l - (30 - j) / 15 * (17719 * j / 50) -
                (j / 16) * (15238 * j / 43) + 29
        val m = 24 * l / 709
        val d = l - 709 * m / 24
        val y = 30 * n + j - 30
        return Date(d.toInt(), m.toInt(), y.toInt())
    }
}