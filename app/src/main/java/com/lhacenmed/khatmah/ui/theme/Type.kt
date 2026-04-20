package com.lhacenmed.khatmah.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.lhacenmed.khatmah.R

val OutfitFamily = FontFamily(Font(R.font.outfit_regular))
val AmiriQuranFamily = FontFamily(Font(R.font.amiri_regular))
val WarshFamily = FontFamily(Font(R.font.warsh_regular))
val AlmaghribiWarshFamily = FontFamily(Font(R.font.almaghribi_warsh))
val UthmaniWarshFamily = FontFamily(Font(R.font.uthmani_warsh))
val WarshTharwatEmaraFamily = FontFamily(Font(R.font.warsh_tharwat_emara))
val WarshSuraNameFamily = FontFamily(Font(R.font.warsh_sura_name))
val SurahHeaderFamily = FontFamily(Font(R.font.surah_header_color_regular))
val KFGQPCFamily = FontFamily(
    Font(R.font.kfgqpc_warsh_v2_regular, FontWeight.Normal),
    Font(R.font.kfgqpc_warsh_v2_bold, FontWeight.Bold),
)
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
val NotoKufiFamily = FontFamily(
    Font(R.font.noto_kufi_extra_light, FontWeight.ExtraLight),
    Font(R.font.noto_kufi_light,       FontWeight.Light),
    Font(R.font.noto_kufi_regular,     FontWeight.Normal),
    Font(R.font.noto_kufi_bold,        FontWeight.Bold),
    Font(R.font.noto_kufi_extra_bold,  FontWeight.ExtraBold),
    Font(R.font.noto_kufi_black,       FontWeight.Black),
    Font(R.font.noto_kufi_medium,       FontWeight.Medium),
    Font(R.font.noto_kufi_semi_bold,       FontWeight.SemiBold),
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
    copy(fontFamily = NotoKufiFamily, textDirection = TextDirection.Content)