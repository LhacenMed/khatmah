package com.lhacenmed.khatmah.core.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry

// ── Route auto-derivation ─────────────────────────────────────────────────────

/**
 * Converts the receiver's class name to a snake_case route string.
 * Strips a trailing "Page" or "Tab" suffix first.
 *
 *   TodayTab          → "today"
 *   ThemeSettingsPage → "theme_settings"
 *   AdhkarDetailPage  → "adhkar_detail"  (then override to append /{id})
 */
private fun Any.autoRoute(): String =
    buildString {
        val base = this@autoRoute::class.simpleName!!
            .removeSuffix("Page")
            .removeSuffix("Tab")
        base.forEachIndexed { i, c ->
            if (c.isUpperCase() && i != 0) append('_')
            append(c.lowercaseChar())
        }
    }

// ── AppTab ────────────────────────────────────────────────────────────────────

/**
 * A bottom navigation destination — tab bar metadata and composable screen content.
 *
 * Developer flow:
 *  1. Create a NavTab val in ui/page/tabs/ using Route.* for route.
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
class NavTab(
    val route: String,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
    val content: @Composable (PaddingValues) -> Unit,
)

/**
 * Self-contained bottom-navigation destination.
 *
 * Route is auto-derived from the object's class name unless overridden.
 * [order] controls tab-bar position (0 = leftmost).
 *
 * Developer flow:
 *  1. `object YourTab : AppTab(iconRes, labelRes, order)` in its feature package.
 *  2. Add to [AppRegistry.tabs] — done. No other file changes needed.
 */
abstract class AppTab(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes   val labelRes: Int,
    val order: Int,
) {
    open val route: String get() = autoRoute()

    @Composable abstract fun Content(padding: PaddingValues)

    /** Wraps this destination as the [NavTab] type expected by [BottomNavBar]. */
    internal fun toNavTab() = NavTab(route, iconRes, labelRes) { Content(it) }
}

// ── AppPage ───────────────────────────────────────────────────────────────────

/**
 * Self-contained full-screen navigation destination.
 *
 * Route is auto-derived from the object's class name unless overridden.
 * For destinations with path/query arguments, override [route] and [arguments],
 * and expose a `routeFor(...)` function for type-safe navigation at call sites.
 *
 * Developer flow:
 *  1. `object YourPage : AppPage()` in its feature package.
 *  2. Add to [AppRegistry.pages] — done. No other file changes needed.
 */
abstract class AppPage {
    open val route: String get() = autoRoute()
    open val arguments: List<NamedNavArgument> = emptyList()

    @Composable abstract fun Content(back: NavBackStackEntry)
}