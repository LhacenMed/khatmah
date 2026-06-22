package com.lhacenmed.khatmah.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.lhacenmed.khatmah.R

val WarshFamily = FontFamily(Font(R.font.kfgqpc_warsh_uthmanic))
val WarshSuraNameFamily = FontFamily(Font(R.font.warsh_sura_name))
val HafsFamily = FontFamily(Font(R.font.kfgqpc_hafs_uthmanic))
val HafsSuraNameFamily = FontFamily(Font(R.font.hafs_sura_name))
val AmiriFamily = FontFamily(Font(R.font.amiri_regular))
// Only the weights the app actually requests are bundled (the others were dropped to save space).
val NotoKufiFamily = FontFamily(
    Font(R.font.noto_kufi_light,     FontWeight.Light),
    Font(R.font.noto_kufi_regular,   FontWeight.Normal),
    Font(R.font.noto_kufi_medium,    FontWeight.Medium),
    Font(R.font.noto_kufi_semi_bold, FontWeight.SemiBold),
    Font(R.font.noto_kufi_bold,      FontWeight.Bold),
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