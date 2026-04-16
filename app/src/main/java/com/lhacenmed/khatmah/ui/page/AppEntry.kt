package com.lhacenmed.khatmah.ui.page

import android.os.Build
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.common.animatedComposable
import com.lhacenmed.khatmah.ui.component.AppTopBar
import com.lhacenmed.khatmah.ui.nav.*
import com.lhacenmed.khatmah.ui.onboarding.*
import com.lhacenmed.khatmah.ui.page.settings.about.AboutPage
import com.lhacenmed.khatmah.ui.page.settings.appearance.LanguagePage
import com.lhacenmed.khatmah.ui.page.settings.appearance.ThemeSettingsPage
import com.lhacenmed.khatmah.ui.page.settings.prayers.*
import com.lhacenmed.khatmah.ui.page.tabs.*
import com.lhacenmed.khatmah.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.WidgetNavRequest
import kotlinx.coroutines.flow.MutableSharedFlow

// ─── Root composable ──────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppEntry() {
    val context = LocalContext.current

    // Determine start destination once per composition — OnboardingPrefs read is fast
    // (SharedPreferences in-process cache) so no need to defer behind a LaunchedEffect.
    val start = remember {
        if (OnboardingPrefs.isComplete(context)) Route.MAIN
        else Route.ONBOARDING_LANGUAGE
    }

    // ── Tab list ──────────────────────────────────────────────────────────────
    val tabs = listOf(TodayTab, AthkarTab, PrayersTab, IndexTab, MoreTab)

    // ── General settings sub-pages ────────────────────────────────────────────
    val pages = listOf(ThemeSettingsPage, LanguagePage, AboutPage)

    val navController = rememberNavController()

    // ── Tab selection state ───────────────────────────────────────────────────
    // Intentionally hoisted here rather than inside MainScreen.
    //
    // MainScreen leaves the composition tree every time the user navigates to a
    // sub-page (NavHost replaces the Route.MAIN composable with the destination
    // composable). Any rememberSaveable inside MainScreen is therefore tied to a
    // NavHost back-stack entry whose SaveableStateHolder can be cleared between
    // the user going to another app and returning — causing a reset to tab 0.
    //
    // AppEntry itself is composed once for the entire Activity lifetime and never
    // leaves the composition, so rememberSaveable here survives all sub-page
    // navigation, app backgrounding, and configuration changes.
    //
    // Cold-start widget tap: the initializer reads the pending route synchronously
    // and immediately consumes it so the very first frame renders the correct tab.
    // Warm-start widget tap: handled by the LaunchedEffect below.
    var selectedTabIndex by rememberSaveable {
        mutableStateOf(
            WidgetNavRequest.route.value
                ?.let { r -> tabs.indexOfFirst { it.route == r }.takeIf { it >= 0 } }
                ?.also { WidgetNavRequest.consume() }
                ?: 0
        )
    }

    // Handles widget taps while the app is already running (warm start).
    // StateFlow replay-1 guarantees visibility even if the request was emitted in
    // MainActivity.onCreate before this effect started collecting.
    val widgetRoute by WidgetNavRequest.route.collectAsState()
    LaunchedEffect(widgetRoute) {
        val route = widgetRoute ?: return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.route == route }
        if (idx >= 0) selectedTabIndex = idx
        WidgetNavRequest.consume() // Reset so recomposition doesn't re-apply it.
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController    = navController,
            startDestination = start,
            modifier         = Modifier.fillMaxSize(),
        ) {
            // ── Onboarding ────────────────────────────────────────────────────
            animatedComposable(Route.ONBOARDING_LANGUAGE)      { LanguageOnboardingPage()     }
            animatedComposable(Route.ONBOARDING_NOTIFICATIONS) { NotificationPermissionPage() }
            animatedComposable(Route.ONBOARDING_LOCATION)      { LocationPermissionPage()     }
            animatedComposable(
                route     = Route.ONBOARDING_COUNTRY_SELECT,
                arguments = listOf(
                    navArgument("fromSettings") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { CountrySelectPage() }
            animatedComposable(
                route     = Route.ONBOARDING_CITY_SELECT,
                arguments = listOf(
                    navArgument("country") { type = NavType.StringType; defaultValue = "" },
                    navArgument("iso2")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("fromSettings") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStack ->
                CitySelectPage(
                    country      = backStack.arguments?.getString("country") ?: "",
                    iso2         = backStack.arguments?.getString("iso2") ?: "",
                    fromSettings = backStack.arguments?.getBoolean("fromSettings") ?: false,
                )
            }

            // ── App shell ─────────────────────────────────────────────────────
            animatedComposable(Route.MAIN) {
                MainScreen(
                    tabs          = tabs,
                    selectedIndex = selectedTabIndex,
                    onSelect      = { selectedTabIndex = it },
                )
            }

            // ── General settings sub-pages ────────────────────────────────────
            pages.forEach { page ->
                animatedComposable(page.route) { page.content() }
            }

            // ── Prayer settings sub-pages ─────────────────────────────────────
            animatedComposable(Route.PRAYER_SETTINGS)           { PrayerSettingsContent()     }
            animatedComposable(Route.PRAYER_CALC_METHOD)        { CalcMethodContent()         }
            animatedComposable(Route.PRAYER_JURISTIC)           { JuristicContent()           }
            animatedComposable(Route.PRAYER_DST)                { DstContent()                }
            animatedComposable(Route.PRAYER_MANUAL_CORRECTIONS) { ManualCorrectionsContent()  }
            animatedComposable(Route.PRAYER_HIGHER_LAT)         { HigherLatContent()          }
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
 *
 * zIndex elevates the selected tab above all others so it always wins touch dispatch —
 * without it, tabs stacked later in the Box would intercept touches meant for the visible tab.
 *
 * Hidden tabs have touch events consumed at the Initial pass as a safety net for any
 * edge cases where zIndex alone doesn't fully isolate input.
 *
 * Re-tapping the active tab's nav button emits on that tab's [LocalScrollToTop] flow,
 * which the tab's content collects to animate its scroll state smoothly to the top.
 *
 * Back press while on a non-primary tab returns to the first tab (Today) rather than
 * exiting the app, matching standard Android bottom-nav back behaviour.
 *
 * Tab selection state is owned by [AppEntry] and passed in — see its kdoc for why.
 */
@Composable
private fun MainScreen(
    tabs: List<NavScreen>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val currentTab = tabs[selectedIndex]

    // Intercept back press on any non-primary tab and return to tab 0 (Today).
    // Disabled on tab 0 so the system back (NavHost exit / app dismiss) proceeds normally.
    BackHandler(enabled = selectedIndex != 0) { onSelect(0) }

    // One SharedFlow per tab — emits Unit when the active tab's nav button is re-tapped.
    val scrollToTopFlows = remember { Array(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }

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
                        if (idx == selectedIndex) scrollToTopFlows[idx].tryEmit(Unit)
                        else onSelect(idx)
                    }
                },
                anchorViewAt = { anchorViews[it] },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // All tabs stay composed — visibility, touch, and Z-order toggle on selection.
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                CompositionLocalProvider(LocalScrollToTop provides scrollToTopFlows[index]) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // Selected tab sits on top — wins all touch dispatch.
                            .zIndex(if (selected) 1f else 0f)
                            .graphicsLayer { alpha = if (selected) 1f else 0f }
                            // Safety net: hidden tabs consume residual pointer events.
                            .then(
                                if (!selected) Modifier.pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Initial)
                                                .changes.forEach { it.consume() }
                                        }
                                    }
                                } else Modifier
                            ),
                    ) { tab.content(innerPadding) }
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