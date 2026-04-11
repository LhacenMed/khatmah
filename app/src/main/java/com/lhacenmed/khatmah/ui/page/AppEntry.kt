package com.lhacenmed.khatmah.ui.page

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.common.animatedComposable
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.BottomNavBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.LocalScrollToTop
import com.lhacenmed.khatmah.ui.nav.NavPage
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.settings.about.AboutPage
import com.lhacenmed.khatmah.ui.page.settings.appearance.LanguagePage
import com.lhacenmed.khatmah.ui.page.settings.appearance.ThemeSettingsPage
import com.lhacenmed.khatmah.ui.page.tabs.AthkarTab
import com.lhacenmed.khatmah.ui.page.tabs.IndexTab
import com.lhacenmed.khatmah.ui.page.tabs.MoreTab
import com.lhacenmed.khatmah.ui.page.tabs.PrayersTab
import com.lhacenmed.khatmah.ui.page.tabs.TodayTab
import kotlinx.coroutines.flow.MutableSharedFlow

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
fun AppEntry() {

    // ── Tab list ──────────────────────────────────────────────────────────────
    // To add a new tab:
    //   1. Create a NavScreen val in ui/page/tabs/ with route, iconRes, labelRes, content.
    //   2. Append it here.
    val tabs = listOf(
        TodayTab,
        AthkarTab,
        PrayersTab,
        IndexTab,
        MoreTab,
    )

    // ── Sub-page list ─────────────────────────────────────────────────────────
    // To add a new sub-page:
    //   1. Create a NavPage val anywhere in ui/page/ with route and a self-contained composable.
    //   2. Append it here.
    val pages = listOf(
        ThemeSettingsPage,
        LanguagePage,
        AboutPage,
    )

    val navController = rememberNavController()

    // ── Navigation host ───────────────────────────────────────────────────────
    // Flat NavHost — every route (shell + sub-pages) lives at the same level.
    // When navigating to a sub-page, the entire MainScreen (top bar + bottom nav +
    // content) slides out as one unit; the sub-page slides in with its own chrome.
    // Tab switching happens inside MainScreen via plain state — no NavHost, no animation.
    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController    = navController,
            startDestination = Route.MAIN,
            modifier         = Modifier.fillMaxSize(),
        ) {
            // Shell: top bar + bottom nav + active tab content, animated as a unit.
            animatedComposable(Route.MAIN) {
                MainScreen(tabs = tabs)
            }

            // Sub-pages: each owns its Scaffold + AppTopBar; animated with shared-axis X.
            pages.forEach { page ->
                animatedComposable(page.route) { page.content() }
            }
        }
    }
}

// ─── Main shell ───────────────────────────────────────────────────────────────

/**
 * Tab host screen — top bar, bottom nav bar, and all tab composables rendered simultaneously.
 * Animates as a single unit when navigating to/from sub-pages.
 *
 * All tabs stay composed at all times (alpha = 0 when not selected), so scroll state
 * and any rememberSaveable values survive tab switches without any manual caching.
 * Hidden tabs have touch events consumed so they never intercept user input.
 *
 * Re-tapping the active tab's nav button emits on that tab's [LocalScrollToTop] flow,
 * which the tab's content collects to animate its scroll state smoothly to the top.
 */
@Composable
private fun MainScreen(tabs: List<NavScreen>) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentTab    = tabs[selectedIndex]

    // One SharedFlow per tab — emits Unit when the active tab's nav button is re-tapped.
    val scrollToTopFlows = remember {
        Array(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    }

    // Anchor views for tab long-press tooltips, indexed in the same order as tabs.
    val anchorViews = remember { arrayOfNulls<View>(tabs.size) }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(currentTab.labelRes),
                isTopLevel = true,
                onBack     = {},
            )
        },
        bottomBar = {
            BottomNavBar(
                screens      = tabs,
                currentRoute = currentTab.route,
                onNavigate   = { route ->
                    val idx = tabs.indexOfFirst { it.route == route }
                    if (idx >= 0) {
                        if (idx == selectedIndex) {
                            // Re-tap: signal the active tab to scroll back to top.
                            scrollToTopFlows[idx].tryEmit(Unit)
                        } else {
                            selectedIndex = idx
                        }
                    }
                },
                anchorViewAt = { anchorViews[it] },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // All tabs stay composed — visibility and touch pass-through toggle on selection.
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                CompositionLocalProvider(LocalScrollToTop provides scrollToTopFlows[index]) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (selected) 1f else 0f }
                            .then(
                                // Hidden tabs consume all pointer events so they never
                                // intercept touches intended for the visible tab beneath.
                                if (!selected) Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                                .changes.forEach { it.consume() }
                                        }
                                    }
                                } else Modifier
                            ),
                    ) {
                        tab.content(innerPadding)
                    }
                }
            }

            // ── Tooltip anchor strip ──────────────────────────────────────────
            // Invisible 1 dp row just above the nav bar — one slot per tab.
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