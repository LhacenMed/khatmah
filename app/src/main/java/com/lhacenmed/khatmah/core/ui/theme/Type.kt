package com.lhacenmed.khatmah.core.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.lhacenmed.khatmah.R
import com.google.android.material.R as MaterialR

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

/**
 * The type-scale roles read back out of the active theme, sorted so one
 * `obtainStyledAttributes` call resolves all fifteen. The order written here is immaterial —
 * lookups go through `binarySearch` — but it follows the Material 3 scale to stay readable.
 */
private val TypeAttrs = intArrayOf(
    MaterialR.attr.textAppearanceDisplayLarge,
    MaterialR.attr.textAppearanceDisplayMedium,
    MaterialR.attr.textAppearanceDisplaySmall,
    MaterialR.attr.textAppearanceHeadlineLarge,
    MaterialR.attr.textAppearanceHeadlineMedium,
    MaterialR.attr.textAppearanceHeadlineSmall,
    MaterialR.attr.textAppearanceTitleLarge,
    MaterialR.attr.textAppearanceTitleMedium,
    MaterialR.attr.textAppearanceTitleSmall,
    MaterialR.attr.textAppearanceBodyLarge,
    MaterialR.attr.textAppearanceBodyMedium,
    MaterialR.attr.textAppearanceBodySmall,
    MaterialR.attr.textAppearanceLabelLarge,
    MaterialR.attr.textAppearanceLabelMedium,
    MaterialR.attr.textAppearanceLabelSmall,
).sortedArray()

/** The one attribute this reads out of a text appearance: which face it is set in. */
private val FontFamilyAttr = intArrayOf(android.R.attr.fontFamily)

/**
 * The active [Typography], with each role set in the face the native theme gives it.
 *
 * The theme's type scale is the app's single source of typographic truth: XML layouts, Material
 * widgets and dialogs read it directly, and this reads the same fifteen text appearances back —
 * so a heading in a fragment and a heading in a composable are set in the same font.
 *
 * Only the face is taken from the theme. Sizes, line heights and letter spacing are Material 3's
 * scale, which is identical on both sides, so there is nothing there to disagree about.
 */
fun resolveTypography(context: Context): Typography {
    val styles = context.theme.obtainStyledAttributes(TypeAttrs)

    /** The family a role is set in, falling back to the app font if the theme leaves it open. */
    fun family(attr: Int): FontFamily {
        val styleRes = styles.getResourceId(TypeAttrs.binarySearch(attr), 0)
        if (styleRes == 0) return NotoKufiFamily
        val appearance = context.obtainStyledAttributes(styleRes, FontFamilyAttr)
        val fontRes = appearance.getResourceId(0, 0)
        appearance.recycle()
        return if (fontRes == 0) NotoKufiFamily else FontFamily(Font(fontRes))
    }

    return try {
        Typography().run {
            copy(
                displayLarge   = displayLarge.setIn(family(MaterialR.attr.textAppearanceDisplayLarge)),
                displayMedium  = displayMedium.setIn(family(MaterialR.attr.textAppearanceDisplayMedium)),
                displaySmall   = displaySmall.setIn(family(MaterialR.attr.textAppearanceDisplaySmall)),
                headlineLarge  = headlineLarge.setIn(family(MaterialR.attr.textAppearanceHeadlineLarge)),
                headlineMedium = headlineMedium.setIn(family(MaterialR.attr.textAppearanceHeadlineMedium)),
                headlineSmall  = headlineSmall.setIn(family(MaterialR.attr.textAppearanceHeadlineSmall)),
                titleLarge     = titleLarge.setIn(family(MaterialR.attr.textAppearanceTitleLarge)),
                titleMedium    = titleMedium.setIn(family(MaterialR.attr.textAppearanceTitleMedium)),
                titleSmall     = titleSmall.setIn(family(MaterialR.attr.textAppearanceTitleSmall)),
                bodyLarge      = bodyLarge.setIn(family(MaterialR.attr.textAppearanceBodyLarge)),
                bodyMedium     = bodyMedium.setIn(family(MaterialR.attr.textAppearanceBodyMedium)),
                bodySmall      = bodySmall.setIn(family(MaterialR.attr.textAppearanceBodySmall)),
                labelLarge     = labelLarge.setIn(family(MaterialR.attr.textAppearanceLabelLarge)),
                labelMedium    = labelMedium.setIn(family(MaterialR.attr.textAppearanceLabelMedium)),
                labelSmall     = labelSmall.setIn(family(MaterialR.attr.textAppearanceLabelSmall)),
            )
        }
    } finally {
        styles.recycle()
    }
}

/**
 * The theme names the face outright, so the weight is already in the file. Asking Compose for it
 * again would have it synthesise emphasis on top of an already-emphasised face.
 *
 * TextDirection.Content: Compose text auto-resolves direction based on locale (RTL for Arabic,
 * LTR for English).
 */
private fun TextStyle.setIn(family: FontFamily): TextStyle =
    copy(fontFamily = family, fontWeight = FontWeight.Normal, textDirection = TextDirection.Content)
