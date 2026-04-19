package com.lhacenmed.khatmah.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.lhacenmed.khatmah.R

val AmiriQuranFamily = FontFamily(Font(R.font.amiri_regular))
val WarshFamily = FontFamily(Font(R.font.warsh_regular))
val OutfitFamily = FontFamily(Font(R.font.outfit_regular))
val DinNextLtFamily = FontFamily(
    Font(R.font.din_next_lt_ultra_light, FontWeight.ExtraLight),
    Font(R.font.din_next_lt_light,       FontWeight.Light),
    Font(R.font.din_next_lt_regular,     FontWeight.Normal),
    Font(R.font.din_next_lt_bold,        FontWeight.Bold),
    Font(R.font.din_next_lt_heavy,       FontWeight.ExtraBold),
    Font(R.font.din_next_lt_black,       FontWeight.Black),
)
val CairoFamily = FontFamily(
    Font(R.font.cairo_extra_light, FontWeight.ExtraLight),
    Font(R.font.cairo_light,       FontWeight.Light),
    Font(R.font.cairo_regular,     FontWeight.Normal),
    Font(R.font.cairo_bold,        FontWeight.Bold),
    Font(R.font.cairo_extra_bold,  FontWeight.ExtraBold),
    Font(R.font.cairo_black,       FontWeight.Black),
    Font(R.font.cairo_medium,       FontWeight.Medium),
    Font(R.font.cairo_semi_bold,       FontWeight.SemiBold),
)

// TextDirection.Content: Compose text auto-resolves direction based on locale (RTL for Arabic, LTR for English)
val Typography = Typography().run {
    copy(
        displayLarge   = displayLarge.styled(),
        displayMedium  = displayMedium.styled(),
        displaySmall   = displaySmall.styled(),
        headlineLarge  = headlineLarge.styled(),
        headlineMedium = headlineMedium.styled(),
        headlineSmall  = headlineSmall.styled(),
        titleLarge     = titleLarge.styled(),
        titleMedium    = titleMedium.styled(),
        titleSmall     = titleSmall.styled(),
        bodyLarge      = bodyLarge.styled(),
        bodyMedium     = bodyMedium.styled(),
        bodySmall      = bodySmall.styled(),
        labelLarge     = labelLarge.styled(),
        labelMedium    = labelMedium.styled(),
        labelSmall     = labelSmall.styled(),
    )
}

private fun TextStyle.styled(): TextStyle =
    copy(fontFamily = DinNextLtFamily, textDirection = TextDirection.Content)