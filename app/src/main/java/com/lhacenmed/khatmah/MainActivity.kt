package com.lhacenmed.khatmah

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.BottomNavBar
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.nav.NavScreen
import com.lhacenmed.khatmah.ui.page.settings.appearance.LanguagePage
import com.lhacenmed.khatmah.ui.page.settings.appearance.ThemeSettingsPage
import com.lhacenmed.khatmah.ui.page.tabs.AthkarTab
import com.lhacenmed.khatmah.ui.page.tabs.IndexTab
import com.lhacenmed.khatmah.ui.page.tabs.MoreTab
import com.lhacenmed.khatmah.ui.page.tabs.PrayersTab
import com.lhacenmed.khatmah.ui.page.tabs.TodayTab
import com.lhacenmed.khatmah.ui.theme.KhatmahTheme
import com.lhacenmed.khatmah.util.ThemeManager
import android.os.Bundle
import androidx.activity.compose.setContent

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhatmahTheme {
                KhatmahApp()
            }
        }
    }
}

// ─── Root composable ──────────────────────────────────────────────────────────

@Composable
private fun KhatmahApp() {

    // ── Tab list ───────────────────────────────────────────────────────────────
    // To add a new tab:
    //   1. Create a NavScreen val in ui/page/tabs/ (route from Route.*, icon, label, content).
    //   2. Append it here.
    // NavHost registration, bottom bar, and route mapping are all automatic.
    val tabs = listOf(
        TodayTab,
        AthkarTab,
        PrayersTab,
        IndexTab,
        MoreTab,
    )

    val navController = rememberNavController()
    val currentRoute  = navController.currentBackStackEntryAsState().value?.destination?.route
    val isTopLevel    = currentRoute in Route.TABS

    // ── Sub-page title map ─────────────────────────────────────────────────────
    // Add an entry here whenever a new sub-page composable is registered below.
    val subPageTitles = mapOf(
        Route.THEME_SETTINGS to stringResource(R.string.theme_settings),
        Route.LANGUAGE       to stringResource(R.string.language_settings),
    )

    val appBarTitle = when {
        isTopLevel       -> tabs.find { it.route == currentRoute }
            ?.let { stringResource(it.labelRes) }
            ?: stringResource(R.string.app_name)
        currentRoute != null -> subPageTitles[currentRoute] ?: stringResource(R.string.app_name)
        else             -> stringResource(R.string.app_name)
    }

    // Anchor views for tab long-press tooltips, indexed in the same order as tabs.
    // Written once inside TooltipAnchorRow's factory; read lazily by NavButton at touch time.
    val anchorViews = remember { arrayOfNulls<View>(tabs.size) }

    CompositionLocalProvider(LocalNavController provides navController) {
        Scaffold(
            topBar = {
                KhatmahTopBar(
                    title      = appBarTitle,
                    isTopLevel = isTopLevel,
                    onBack     = { navController.popBackStack() },
                )
            },
            bottomBar = {
                // Bottom bar is only part of the Scaffold when on a top-level tab so that
                // Scaffold's innerPadding correctly reflects its absence on sub-pages.
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
                        anchorViews = anchorViews.toList(),
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                KhatmahNavHost(
                    tabs          = tabs,
                    navController = navController,
                    innerPadding  = innerPadding,
                )

                // ── Tooltip anchor strip ──────────────────────────────────────
                // Invisible 1 dp row just above the nav bar. Each slot has the same
                // weight(1f) width as its NavButton, so touch X is transferable.
                // Only present when the nav bar is visible.
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

// ─── Top app bar ──────────────────────────────────────────────────────────────

/**
 * Adaptive top app bar:
 *  • Top-level tab  → title only, no navigation icon.
 *  • Sub-page       → back arrow + sub-page title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KhatmahTopBar(
    title: String,
    isTopLevel: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (!isTopLevel) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_up),
                    )
                }
            }
        },
    )
}

// ─── Navigation host ──────────────────────────────────────────────────────────

/**
 * Builds the NavHost from [tabs] automatically, then registers sub-pages explicitly.
 * The start destination is always the first tab in the list.
 *
 * To add a new sub-page:
 *  1. Add a Route.* constant in Route.kt.
 *  2. Add it to MainActivity's subPageTitles map.
 *  3. Register a composable() block here.
 */
@Composable
private fun KhatmahNavHost(
    tabs: List<NavScreen>,
    navController: NavHostController,
    innerPadding: PaddingValues,
) {
    val context = LocalContext.current

    NavHost(
        navController    = navController,
        startDestination = tabs.first().route,
        modifier         = Modifier.fillMaxSize(),
    ) {
        // ── Tabs: auto-registered from the tabs list ───────────────────────────
        tabs.forEach { screen ->
            composable(screen.route) { screen.content(innerPadding) }
        }

        // ── Sub-pages ──────────────────────────────────────────────────────────
        composable(Route.THEME_SETTINGS) {
            ThemeSettingsPage(
                padding        = innerPadding,
                currentMode    = ThemeManager.getMode(context),
                onModeSelected = { ThemeManager.setMode(context, it) },
            )
        }
        composable(Route.LANGUAGE) {
            LanguagePage(padding = innerPadding)
        }
    }
}

// ─── Tooltip anchor strip ─────────────────────────────────────────────────────

/**
 * An invisible 1 dp row of Views — one per tab — positioned just above the nav bar.
 * Each view carries a tooltip text; NavButton forwards long-press events here so the
 * system tooltip fires above the bar. Visibility is INVISIBLE so the strip never
 * intercepts real touches from the user.
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