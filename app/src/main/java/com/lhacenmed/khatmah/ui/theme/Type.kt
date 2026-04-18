package com.lhacenmed.khatmah.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import com.lhacenmed.khatmah.R

val AmiriQuranFamily = FontFamily(Font(R.font.amiri_regular))

val WarshFamily = FontFamily(Font(R.font.warsh_regular))
val OutfitFamily = FontFamily(Font(R.font.outfit_regular))

// TextDirection.Content: Compose text auto-resolves direction based on locale (RTL for Arabic, LTR for English)
val Typography = Typography().run {
    copy(
        displayLarge  = displayLarge.rtl(),
        displayMedium = displayMedium.rtl(),
        displaySmall  = displaySmall.rtl(),
        headlineLarge  = headlineLarge.rtl(),
        headlineMedium = headlineMedium.rtl(),
        headlineSmall  = headlineSmall.rtl(),
        titleLarge  = titleLarge.rtl(),
        titleMedium = titleMedium.rtl(),
        titleSmall  = titleSmall.rtl(),
        bodyLarge  = bodyLarge.rtl(),
        bodyMedium = bodyMedium.rtl(),
        bodySmall  = bodySmall.rtl(),
        labelLarge  = labelLarge.rtl(),
        labelMedium = labelMedium.rtl(),
        labelSmall  = labelSmall.rtl(),
    )
}

private fun TextStyle.rtl(): TextStyle = copy(textDirection = TextDirection.Content)