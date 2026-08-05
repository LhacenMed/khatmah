package com.lhacenmed.khatmah.core.nav

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment

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
 * A bottom-navigation tab: its bar icon/label, optional toolbar title/subtitle/actions, and the
 * [Fragment] that is its body. The ordered [AppTabs] list is the single source of truth —
 * MainActivity derives the bottom-nav menu, the bodies and the toolbar from it, with no menu XML
 * and no per-tab switch statements.
 *
 * A tab's fragment is created the first time the tab is selected and then kept — switching tabs
 * hides it rather than tearing it down, so a screen is never rebuilt on the way back to it.
 *
 * Add a tab:
 *  1. `object YourTab : AppTab(icon, title, route) { … newFragment() }` in its feature package.
 *  2. Add it to [AppTabs].
 */
abstract class AppTab(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    /**
     * Deep-link key matched against widget/reminder intents (see MainActivity). Doubles as the
     * fragment tag the body is kept under, so it must stay unique across [AppTabs].
     */
    val route: String,
) {
    /** Toolbar title; defaults to the bar [titleRes]. Override only when they differ. */
    @get:StringRes
    open val toolbarTitleRes: Int get() = titleRes

    /** Toolbar action icons for this tab. Default: none. */
    open val actions: List<TabAction> = emptyList()

    /** Optional toolbar subtitle (e.g. the selected city on Prayers). Default: none. */
    open fun subtitle(context: Context): String? = null

    /** Builds the tab's body. Called once per tab, on first selection. */
    abstract fun newFragment(): Fragment
}

/**
 * A tab whose body is still Compose. [ComposeTabFragment] hosts it, so such a tab sits in the
 * fragment-based host exactly like a native one and needs to know nothing about it.
 *
 * This is the bridge for the migration to native tabs: as each tab is rewritten it goes back to
 * extending [AppTab] directly and returns its own fragment, and this class goes away with the
 * last one.
 */
abstract class ComposeTab(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    route: String,
) : AppTab(iconRes, titleRes, route) {

    final override fun newFragment(): Fragment = ComposeTabFragment.newInstance(route)

    @Composable
    abstract fun Content(padding: PaddingValues)
}
