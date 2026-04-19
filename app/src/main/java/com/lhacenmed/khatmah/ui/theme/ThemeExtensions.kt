package com.lhacenmed.khatmah.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.android.material.color.MaterialColors

// ─── Color utilities ──────────────────────────────────────────────────────────

/** Reduces alpha to 0.62 when [enabled] is false; identity otherwise. */
fun Color.applyOpacity(enabled: Boolean): Color =
    if (enabled) this else copy(alpha = 0.62f)

/** Harmonizes this color toward the current Material theme primary. */
@Composable
fun Color.harmonizeWithPrimary(): Color =
    Color(MaterialColors.harmonize(toArgb(), MaterialTheme.colorScheme.primary.toArgb()))

// ─── Fixed accent color tokens ────────────────────────────────────────────────
// Approximated from Material 3 fixed-accent roles using the live colorScheme.
// On Android 12+ with dynamic color these track the wallpaper palette automatically.
object FixedAccentColors {
    val primaryFixed: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryFixed: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryFixed: Color
        @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryFixed: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer
}

// ─── Preference typography ────────────────────────────────────────────────────

/** Title style used by preference list items — matches Material You settings conventions. */
val preferenceTitle = TextStyle(
    fontFamily = CairoFamily,
    fontWeight = FontWeight.Normal,
    fontSize   = 20.sp,
    lineHeight = 24.sp,
)