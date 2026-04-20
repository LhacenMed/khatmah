package com.lhacenmed.khatmah.data.adhkar

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * Source of a category card icon.
 *
 * [Res]  → built-in vector drawable (seeded categories).
 * [Uri]  → user-provided image file path (supports PNG/JPG/SVG via Coil).
 * [None] → no icon.
 */
sealed class IconSource {
    data class Res(@DrawableRes val resId: Int) : IconSource()
    data class Uri(val path: String) : IconSource()
    object None : IconSource()
}

/**
 * A single Adhkar category as a UI model, sourced from the database.
 *
 * [span] = 1 → half-width grid cell.
 * [span] = 2 → full-width grid cell.
 */
data class AdhkarCategory(
    val id: String,
    val title: String,
    val iconSource: IconSource = IconSource.None,
    val color: Color,
    val span: Int = 1,
)

/**
 * Snapshot of a built-in category's original defaults.
 * Used by [AdhkarEditorPage] to determine what has changed and enable "Reset to Default".
 */
data class BuiltInDefaults(
    val title:     String,
    val color:     Color,
    val iconResId: Int?,
    val span:      Int,
    val dhikrList: List<Dhikr>,
)

// ── Built-in seed descriptors ─────────────────────────────────────────────────
// Used only once during first-launch DB seeding; afterwards everything lives in DB.

internal data class BuiltInCategoryDescriptor(
    val id: String,
    val titleResName: String,
    val iconResName: String,
    val colorArgb: Int,
    val span: Int = 1,
)

internal val builtInDescriptors: List<BuiltInCategoryDescriptor> = listOf(
    BuiltInCategoryDescriptor("morning",      "adhkar_morning",      "ic_fajr",    0xFF1565C0.toInt(), 2),
    BuiltInCategoryDescriptor("evening",      "adhkar_evening",      "ic_isha",    0xFF4527A0.toInt(), 2),
    BuiltInCategoryDescriptor("after_prayer", "adhkar_after_prayer", "ic_mosque",  0xFF2E7D32.toInt()),
    BuiltInCategoryDescriptor("sleep",        "adhkar_sleep",        "ic_isha",    0xFF6A1B9A.toInt()),
    BuiltInCategoryDescriptor("mosque",       "adhkar_mosque",       "ic_mosque",  0xFFBF360C.toInt()),
    BuiltInCategoryDescriptor("wakeup",       "adhkar_wakeup",       "ic_sunrise", 0xFF00838F.toInt()),
    BuiltInCategoryDescriptor("quran_duas",   "adhkar_quran_duas",   "ic_book",    0xFFAFB42B.toInt()),
    BuiltInCategoryDescriptor("ruqyah",       "adhkar_ruqyah",       "ic_adhkar",  0xFF006064.toInt()),
    BuiltInCategoryDescriptor("maathura",     "adhkar_maathura",     "ic_book",    0xFF558B2F.toInt()),
    BuiltInCategoryDescriptor("travel",       "adhkar_travel",       "ic_adhkar",  0xFF880E4F.toInt()),
)