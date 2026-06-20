package com.lhacenmed.khatmah.core.nav

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/**
 * A toolbar action for a tab, rendered as a top-app-bar icon (or text button) by
 * MainActivity. [onClick] receives the host Activity so it can launch a [Dest] or
 * reach an Activity-scoped ViewModel.
 */
class TabAction(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    /** Render the label beside the icon (text button) instead of icon-only. */
    val showAsText: Boolean = false,
    val onClick: (ComponentActivity) -> Unit,
)

/**
 * A bottom-navigation tab: its bar icon/label, optional toolbar title/subtitle/actions,
 * and its Compose body. The ordered [AppTabs] list is the single source of truth —
 * MainActivity derives the bottom-nav menu, the pager and the toolbar from it, with no
 * menu XML and no per-tab switch statements.
 *
 * Add a tab:
 *  1. `object YourTab : AppTab(icon, title, route) { … Content(…) }` in its feature package.
 *  2. Add it to [AppTabs].
 */
abstract class AppTab(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    /** Deep-link key matched against widget/reminder intents (see MainActivity). */
    val route: String,
) {
    /** Toolbar title; defaults to the bar [titleRes]. Override only when they differ. */
    @get:StringRes
    open val toolbarTitleRes: Int get() = titleRes

    /** Toolbar action icons for this tab. Default: none. */
    open val actions: List<TabAction> = emptyList()

    /** Optional toolbar subtitle (e.g. the selected city on Prayers). Default: none. */
    open fun subtitle(context: Context): String? = null

    @Composable
    abstract fun Content(padding: PaddingValues)
}
