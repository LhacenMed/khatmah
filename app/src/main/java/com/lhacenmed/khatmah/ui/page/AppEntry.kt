package com.lhacenmed.khatmah.ui.page

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.ui.common.animatedComposable
import com.lhacenmed.khatmah.ui.common.fadeThroughComposable
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.BottomNavBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.about.AboutPage
import com.lhacenmed.khatmah.ui.page.settings.appearance.LanguagePage
import com.lhacenmed.khatmah.ui.page.settings.appearance.ThemeSettingsPage
import com.lhacenmed.khatmah.ui.page.tabs.AthkarTab
import com.lhacenmed.khatmah.ui.page.tabs.IndexTab
import com.lhacenmed.khatmah.ui.page.tabs.MoreTab
import com.lhacenmed.khatmah.ui.page.tabs.PrayersTab
import com.lhacenmed.khatmah.ui.page.tabs.TodayTab

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun AppEntry() {

    // ── Tab list ──────────────────────────────────────────────────────────────
    // To add a new tab:
    //   1. Create a NavScreen val in ui/page/tabs/ with route, iconRes, labelRes, content.
    //   2. Append it here.
    // Bottom bar wiring, NavHost registration, and top-level detection are all automatic.
    val tabs = listOf(
        TodayTab,
        AthkarTab,
        PrayersTab,
        IndexTab,
        MoreTab,
    )

    // ── Sub-page list ─────────────────────────────────────────────────────────
    // To add a new sub-page:
    //   1. Create a NavPage val anywhere in ui/page/ with route, titleRes, content.
    //   2. Append it here.
    // NavHost registration and TopAppBar title resolution are both automatic.
    val pages = listOf(
        ThemeSettingsPage,
        LanguagePage,
        AboutPage,
    )

    val navController = rememberNavController()
    val currentEntry  by navController.currentBackStackEntryAsState()
    val currentRoute  = currentEntry?.destination?.route

    // Derive top-level detection from the tabs list — no separate Route.TABS set needed.
    // currentRoute is null before the NavHost initialises on the very first composition;
    // treating null as top-level prevents a back-arrow + app-name flash at startup.
    val tabRoutes  = remember { tabs.map { it.route }.toHashSet() }
    val isTopLevel = currentRoute == null || currentRoute in tabRoutes

    // null  → pre-init: show first tab label immediately, no "Khatmah" flash.
    // tab   → matching tab label.
    // page  → titleRes declared in the NavPage val, co-located with the page content.
    val appBarTitle = when {
        currentRoute == null -> stringResource(tabs.first().labelRes)
        isTopLevel           -> tabs.find { it.route == currentRoute }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.app_name)
        else                 -> pages.find { it.route == currentRoute }
            ?.let { stringResource(it.titleRes) }
            ?: stringResource(R.string.app_name)
    }

    // Anchor views for tab long-press tooltips, indexed in the same order as tabs.
    // Populated by TooltipAnchorRow factory blocks; passed into BottomNavBar as a
    // lambda so reads happen at touch time — never a stale snapshot of nulls.
    val anchorViews = remember { arrayOfNulls<View>(tabs.size) }

    CompositionLocalProvider(LocalNavController provides navController) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title      = appBarTitle,
                    isTopLevel = isTopLevel,
                    onBack     = { navController.popBackStack() },
                )
            },
            bottomBar = {
                // Excluded from Scaffold on sub-pages so innerPadding.bottom = 0
                // and sub-page content fills the full screen without a nav bar offset.
                if (isTopLevel) {
                    BottomNavBar(
                        screens      = tabs,
                        currentRoute = currentRoute,
                        onNavigate   = { route ->
                            navController.navigate(route) {
                                // Avoid building a large back-stack when switching tabs.
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        // Lambda reads the live array value at touch time — guaranteed
                        // to reflect whatever the most recent factory block wrote,
                        // whether that was on first composition or after a back-navigation.
                        anchorViewAt = { anchorViews[it] },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                AppNavHost(
                    tabs          = tabs,
                    pages         = pages,
                    navController = navController,
                    innerPadding  = innerPadding,
                )

                // ── Tooltip anchor strip ──────────────────────────────────────
                // Invisible 1 dp row just above the nav bar — one slot per tab,
                // each weighted to match its NavButton width so touch X transfers.
                // Present only when the nav bar is visible; factory blocks run on
                // every re-entry so anchorViews always holds current View references.
                if (isTopLevel) {
                    TooltipAnchorRow(
                        screens  = tabs,
                        onReady  = { index, view -> anchorViews[index] = view },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .offset(y = -innerPadding.calculateBottomPadding() - 12.dp),
                    )
                }
            }
        }
    }
}

// ─── Navigation host ──────────────────────────────────────────────────────────

/**
 * Builds the NavHost entirely from [tabs] and [pages] — no manual composable() calls.
 * The start destination is always the first entry in [tabs].
 *
 * Transition strategy follows Material 3 motion guidance:
 *  • Tabs    → fadeThroughComposable (scale-fade: peer-level navigation)
 *  • Sub-pages → animatedComposable (shared-axis X: hierarchical navigation)
 *
 * To add a tab   : append a NavScreen to the tabs list in AppEntry.
 * To add a page  : append a NavPage  to the pages list in AppEntry.
 */
@Composable
private fun AppNavHost(
    tabs: List<NavScreen>,
    pages: List<NavPage>,
    navController: NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController    = navController,
        startDestination = tabs.first().route,
        modifier         = Modifier.fillMaxSize(),
    ) {
        // ── Tabs: scale-fade (Material 3 peer-level transition) ────────────────
        tabs.forEach { screen ->
            fadeThroughComposable(screen.route) { screen.content(innerPadding) }
        }

        // ── Sub-pages: shared-axis X (Material 3 hierarchical transition) ───────
        pages.forEach { page ->
            animatedComposable(page.route) { page.content(innerPadding) }
        }
    }
}

// ─── Tooltip anchor strip ─────────────────────────────────────────────────────

/**
 * An invisible 1 dp row of Views — one per tab — positioned just above the nav bar.
 * Each view carries tooltip text; NavButton forwards long-press MotionEvents here so
 * the system tooltip fires above the bar. INVISIBLE so the strip never intercepts real
 * touches from the user.
 *
 * The factory block runs each time this composable enters composition, refreshing the
 * anchorViews array with current (window-attached) View references.
 */
@Composable
private fun TooltipAnchorRow(
    screens: List<NavScreen>,
    onReady: (index: Int, view: View) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        screens.forEachIndexed { index, screen ->
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp),
                factory  = { ctx ->
                    View(ctx).apply {
                        visibility = View.INVISIBLE  // never intercepts real touches
                        ViewCompat.setTooltipText(this, ctx.getString(screen.labelRes))
                        onReady(index, this)
                    }
                },
                update = { view ->
                    // Keep label in sync if the screens list ever changes.
                    ViewCompat.setTooltipText(view, view.context.getString(screen.labelRes))
                },
            )
        }
    }
}