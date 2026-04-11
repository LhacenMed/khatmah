package com.lhacenmed.khatmah.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * A sub-page navigation destination — a screen reachable from a tab or another sub-page.
 *
 * Developer flow:
 *  1. Create a NavPage val in the appropriate ui/page/ subdirectory.
 *     Supply route (from Route.*), titleRes for the TopAppBar, and the content composable.
 *  2. Append it to the pages list in MainActivity's AppEntry.
 *
 * NavHost registration and TopAppBar title resolution are both automatic from the list.
 *
 * @param route    Navigation route string; use Route.* constants.
 * @param titleRes String resource displayed in the TopAppBar when this page is active.
 * @param content  Screen composable; receives the Scaffold's inner PaddingValues.
 */
class NavPage(
    val route: String,
    @StringRes val titleRes: Int,
    val content: @Composable (PaddingValues) -> Unit,
)