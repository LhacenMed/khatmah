package com.lhacenmed.khatmah.core.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * A bottom navigation destination — tab bar metadata and composable screen content.
 *
 * Developer flow:
 *  1. Create a NavScreen val in ui/page/tabs/ using Route.* for route.
 *  2. Append it to the tabs list in MainActivity.
 *
 * NavHost registration and bottom bar wiring derive from the list automatically.
 * Routes must match Route.* constants to keep a single source of truth.
 *
 * @param route     Navigation route; use Route.* constants.
 * @param iconRes   Drawable resource for the tab icon.
 * @param labelRes  String resource for the tab label and system long-press tooltip.
 * @param content   Screen composable; receives the Scaffold's inner PaddingValues.
 */
class NavScreen(
    val route: String,
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val content: @Composable (PaddingValues) -> Unit,
)