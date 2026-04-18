package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.lhacenmed.khatmah.R

/**
 * A single Adhkar category rendered as a card in the grid.
 *
 * [span] = 1 → half-width cell · [span] = 2 → full-width cell.
 */
data class AdhkarCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val color: Color,
    val span: Int = 1,
)

/** Ordered list powering the Adhkar grid. */
val adhkarCategories: List<AdhkarCategory> = listOf(
    AdhkarCategory(
        id       = "morning",
        titleRes = R.string.adhkar_morning,
        iconRes  = R.drawable.ic_fajr,
        color    = Color(0xFF1565C0),
        span     = 2,
    ),
    AdhkarCategory(
        id       = "evening",
        titleRes = R.string.adhkar_evening,
        iconRes  = R.drawable.ic_isha,
        color    = Color(0xFF4527A0),
        span     = 2,
    ),
    AdhkarCategory(
        id       = "after_prayer",
        titleRes = R.string.adhkar_after_prayer,
        iconRes  = R.drawable.ic_mosque,
        color    = Color(0xFF2E7D32),
    ),
    AdhkarCategory(
        id       = "sleep",
        titleRes = R.string.adhkar_sleep,
        iconRes  = R.drawable.ic_isha,
        color    = Color(0xFF6A1B9A),
    ),
    AdhkarCategory(
        id       = "mosque",
        titleRes = R.string.adhkar_mosque,
        iconRes  = R.drawable.ic_mosque,
        color    = Color(0xFFBF360C),
    ),
    AdhkarCategory(
        id       = "wakeup",
        titleRes = R.string.adhkar_wakeup,
        iconRes  = R.drawable.ic_sunrise,
        color    = Color(0xFF00838F),
    ),
    AdhkarCategory(
        id       = "quran_duas",
        titleRes = R.string.adhkar_quran_duas,
        iconRes  = R.drawable.ic_book,
        color    = Color(0xFFAFB42B),
    ),
    AdhkarCategory(
        id       = "ruqyah",
        titleRes = R.string.adhkar_ruqyah,
        iconRes  = R.drawable.ic_athkar,
        color    = Color(0xFF006064),
    ),
    AdhkarCategory(
        id       = "maathura",
        titleRes = R.string.adhkar_maathura,
        iconRes  = R.drawable.ic_book,
        color    = Color(0xFF558B2F),
    ),
    AdhkarCategory(
        id       = "travel",
        titleRes = R.string.adhkar_travel,
        iconRes  = R.drawable.ic_athkar,
        color    = Color(0xFF880E4F),
    ),
)