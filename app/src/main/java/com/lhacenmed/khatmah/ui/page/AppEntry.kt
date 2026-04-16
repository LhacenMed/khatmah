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
            animatedComposable(Route.MAIN) { MainScreen(tabs = tabs) }

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
 * Widget tap navigation: [WidgetNavRequest] carries the target route from [MainActivity]
 * into this composable. [selectedIndex] is seeded from [WidgetNavRequest.route] at
 * initialisation time so the very first frame already shows the correct tab — no
 * Today→Prayers flash on cold start. The [LaunchedEffect] below then consumes the
 * request and handles subsequent warm-start taps.
 */
@Composable
private fun MainScreen(tabs: List<NavScreen>) {
    // Seed from any pending widget request so the first rendered frame is already
    // on the correct tab. rememberSaveable preserves the value across config changes;
    // the initializer only runs once per composition lifetime.
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            WidgetNavRequest.route.value
                ?.let { r -> tabs.indexOfFirst { it.route == r }.takeIf { it >= 0 } }
                ?: 0
        )
    }
    val currentTab = tabs[selectedIndex]

    // Intercept back press on any non-primary tab and return to tab 0 (Today).
    // Disabled on tab 0 so the system back (NavHost exit / app dismiss) proceeds normally.
    BackHandler(enabled = selectedIndex != 0) { selectedIndex = 0 }

    // One SharedFlow per tab — emits Unit when the active tab's nav button is re-tapped.
    val scrollToTopFlows = remember { Array(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }

    // Anchor views for tab long-press tooltips, indexed in the same order as tabs.
    val anchorViews = remember { arrayOfNulls<View>(tabs.size) }

    // ── Widget-tap navigation ─────────────────────────────────────────────────
    // StateFlow replay-1 guarantees we see a request even if it was emitted in
    // MainActivity.onCreate before this LaunchedEffect started collecting.
    val widgetRoute by WidgetNavRequest.route.collectAsState()
    LaunchedEffect(widgetRoute) {
        val route = widgetRoute ?: return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.route == route }
        if (idx >= 0) selectedIndex = idx
        WidgetNavRequest.consume() // Reset so recomposition doesn't re-apply it.
    }

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
                        else selectedIndex = idx
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