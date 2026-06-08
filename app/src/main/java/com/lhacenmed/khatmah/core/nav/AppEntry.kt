package com.lhacenmed.khatmah.core.nav

import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.motion.PredictiveBackContainer
import com.lhacenmed.khatmah.core.motion.animatedComposable
import com.lhacenmed.khatmah.core.motion.pageComposable
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.core.ui.components.bottomnav.BottomNavBar
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaPage
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaTab
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaViewModel
import com.lhacenmed.khatmah.onboarding.CitySelectPage
import com.lhacenmed.khatmah.onboarding.CountrySelectPage
import com.lhacenmed.khatmah.onboarding.LanguageOnboardingPage
import com.lhacenmed.khatmah.onboarding.LocationPermissionPage
import com.lhacenmed.khatmah.onboarding.NotificationPermissionPage
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.WidgetNavRequest
import kotlinx.coroutines.flow.MutableSharedFlow

object ShellRoutes {
    const val MAIN = "main"
    const val ONBOARDING_LANGUAGE       = "onboarding_language"
    const val ONBOARDING_NOTIFICATIONS  = "onboarding_notifications"
    const val ONBOARDING_LOCATION       = "onboarding_location"
    const val ONBOARDING_COUNTRY_SELECT = "onboarding_country_select?fromSettings={fromSettings}"
    const val ONBOARDING_CITY_SELECT    = "onboarding_city_select?country={country}&iso2={iso2}&fromSettings={fromSettings}"

    fun citySelect(country: String, iso2: String, fromSettings: Boolean = false) =
        "onboarding_city_select?country=${android.net.Uri.encode(country)}&iso2=${android.net.Uri.encode(iso2)}&fromSettings=$fromSettings"
}

// ─── Root composable ──────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppEntry() {
    val context = LocalContext.current

    val start = remember {
        if (OnboardingPrefs.isComplete(context)) ShellRoutes.MAIN else ShellRoutes.ONBOARDING_LANGUAGE
    }

    // Tabs driven by AppRegistry — add a new AppTab object + one registry line to extend.
    val tabs = AppRegistry.tabs.map { it.toNavTab() }

    val navController = rememberNavController()

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(
            WidgetNavRequest.route.value
                ?.let { r -> tabs.indexOfFirst { it.route == r }.takeIf { it >= 0 } }
                ?.also { WidgetNavRequest.consume() }
                ?: 0
        )
    }

    val widgetRoute by WidgetNavRequest.route.collectAsState()
    LaunchedEffect(widgetRoute) {
        val route = widgetRoute ?: return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.route == route }
        if (idx >= 0) {
            selectedTabIndex = idx
        } else {
            if (route.startsWith("adhkar_detail/")) {
                selectedTabIndex = tabs.indexOfFirst { it.route == "adhkar" }.coerceAtLeast(0)
            }
            navController.navigate(route)
        }
        WidgetNavRequest.consume()
    }

    // Observe the back stack as State so canGoBack recomposes when the stack changes.
    // currentBackStackEntry is read inside derivedStateOf to establish the dependency.
    val currentEntry by navController.currentBackStackEntryAsState()
    val canGoBack by remember {
        derivedStateOf {
            // Reading currentEntry establishes the recomposition trigger;
            // previousBackStackEntry is the actual signal we care about.
            currentEntry?.let { navController.previousBackStackEntry } != null
        }
    }

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController    = navController,
            startDestination = start,
            modifier         = Modifier.fillMaxSize(),
        ) {
            // ── Onboarding ────────────────────────────────────────────────────
            // Uses legacy animatedComposable — linear flow, back gesture not needed.
            animatedComposable(ShellRoutes.ONBOARDING_LANGUAGE)      { LanguageOnboardingPage()     }
            animatedComposable(ShellRoutes.ONBOARDING_NOTIFICATIONS) { NotificationPermissionPage() }
            animatedComposable(ShellRoutes.ONBOARDING_LOCATION)      { LocationPermissionPage()     }
            animatedComposable(
                route     = ShellRoutes.ONBOARDING_COUNTRY_SELECT,
                arguments = listOf(
                    navArgument("fromSettings") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { CountrySelectPage() }
            animatedComposable(
                route     = ShellRoutes.ONBOARDING_CITY_SELECT,
                arguments = listOf(
                    navArgument("country")      { type = NavType.StringType; defaultValue = "" },
                    navArgument("iso2")         { type = NavType.StringType; defaultValue = "" },
                    navArgument("fromSettings") { type = NavType.BoolType;   defaultValue = false },
                ),
            ) { backStack ->
                CitySelectPage(
                    country      = backStack.arguments?.getString("country") ?: "",
                    iso2         = backStack.arguments?.getString("iso2") ?: "",
                    fromSettings = backStack.arguments?.getBoolean("fromSettings") ?: false,
                )
            }

            // ── App shell (root) ──────────────────────────────────────────────────────
            // MAIN is the root destination — registered with pageComposable so popEnter
            // is EnterTransition.None and it never animates when returning to it.
            pageComposable(ShellRoutes.MAIN) {
                MainScreen(
                    tabs          = tabs,
                    selectedIndex = selectedTabIndex,
                    onSelect      = { selectedTabIndex = it },
                )
            }

            // ── AppRegistry pages ─────────────────────────────────────────────
            // Each page uses pageComposable (enter-only push / exit-only pop) and
            // is wrapped in PredictiveBackContainer for the scale-down back gesture.
            AppRegistry.pages.forEach { page ->
                pageComposable(route = page.route, arguments = page.arguments) { back ->
                    PredictiveBackContainer(
                        enabled = canGoBack,
                        onBack  = { navController.popBackStack() },
                    ) {
                        page.Content(back)
                    }
                }
            }
        }
    }
}

// ─── Main shell ───────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun MainScreen(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val context    = LocalContext.current
    val nav        = LocalNavController.current
    val activity   = LocalActivity.current as ComponentActivity
    val adhkarVm: AdhkarViewModel = viewModel(activity)
    val adhkarState by adhkarVm.uiState.collectAsState()
    val qadaaVm: QadaaViewModel = viewModel(activity)

    // ── Pager ─────────────────────────────────────────────────────────────────
    val pagerState   = rememberPagerState(
        initialPage = selectedIndex,
        pageCount   = { tabs.size },
    )
    val adhkarTabIdx = remember(tabs) { tabs.indexOfFirst { it.route == AdhkarTab.route } }

    val currentTab   = tabs[pagerState.currentPage]
    val isAdhkarTab  = currentTab.route == AdhkarTab.route
    val isPrayersTab = currentTab.route == PrayersTab.route
    val isQadaaTab   = currentTab.route == QadaaTab.route
    val inAdhkarSelect = isAdhkarTab && adhkarState.selectionMode

    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != adhkarTabIdx && adhkarState.selectionMode)
            adhkarVm.exitSelectionMode()
        if (pagerState.settledPage != selectedIndex)
            onSelect(pagerState.settledPage)
    }

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex)
            pagerState.scrollToPage(selectedIndex)
    }

    BackHandler(enabled = selectedIndex != 0 || inAdhkarSelect) {
        when {
            inAdhkarSelect -> adhkarVm.exitSelectionMode()
            else           -> onSelect(0)
        }
    }

    val scrollToTopFlows = remember { Array(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }
    val anchorViews      = remember { arrayOfNulls<View>(tabs.size) }

    val prayersCityName = remember(isPrayersTab) {
        if (isPrayersTab) OnboardingPrefs.location(context)?.cityName.orEmpty() else ""
    }

    val topBarTitle = when {
        inAdhkarSelect -> stringResource(R.string.n_selected, adhkarState.selectedIds.size)
        isPrayersTab   -> stringResource(R.string.prayers_screen_title)
        else           -> stringResource(currentTab.labelRes)
    }

    val topBarColor = when {
        inAdhkarSelect -> MaterialTheme.colorScheme.primaryContainer
        else           -> MaterialTheme.colorScheme.surfaceContainer
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title          = topBarTitle,
                subtitle       = if (isPrayersTab && prayersCityName.isNotBlank()) prayersCityName else null,
                isTopLevel     = !inAdhkarSelect,
                onBack         = { if (inAdhkarSelect) adhkarVm.exitSelectionMode() },
                containerColor = topBarColor,
                actions        = {
                    if (isAdhkarTab) {
                        if (inAdhkarSelect) {
                            IconButton(
                                onClick     = adhkarVm::toggleSelectAll,
                                tooltipText = stringResource(R.string.select_all),
                            ) {
                                Icon(
                                    imageVector        = if (adhkarState.allSelected)
                                        Icons.Default.CheckBox
                                    else
                                        Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = stringResource(R.string.select_all),
                                )
                            }
                            TextButton(onClick = adhkarVm::deleteSelected) {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            IconButton(
                                onClick     = { nav.navigate(AdhkarEditorPage.routeFor(null)) },
                                tooltipText = stringResource(R.string.add_adhkar),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_adhkar))
                            }
                        }
                    } else if (isPrayersTab) {
                        IconButton(
                            onClick     = { nav.navigate(QiblaPage.route) },
                            tooltipText = stringResource(R.string.prayers_qibla),
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_kaaba),
                                contentDescription = stringResource(R.string.prayers_qibla),
                                modifier           = Modifier.size(26.dp),
                            )
                        }
                        IconButton(
                            onClick     = { nav.navigate(PrayerSettingsPage.route) },
                            tooltipText = stringResource(R.string.prayers_settings),
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.prayers_settings),
                            )
                        }
                    } else if (isQadaaTab) {
                        IconButton(
                            onClick     = { nav.navigate("qadaa_history") },
                            tooltipText = stringResource(R.string.qadaa_history),
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.qadaa_history))
                        }
                        IconButton(
                            onClick     = { qadaaVm.requestAddPrayers() },
                            tooltipText = stringResource(R.string.qadaa_add_prayers_title),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.qadaa_add_prayers_title))
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomNavBar(
                screens      = tabs,
                currentRoute = tabs[pagerState.currentPage].route,
                onNavigate   = { route ->
                    val idx = tabs.indexOfFirst { it.route == route }
                    if (idx >= 0) {
                        if (idx == selectedIndex) scrollToTopFlows[idx].tryEmit(Unit)
                        else {
                            if (inAdhkarSelect) adhkarVm.exitSelectionMode()
                            onSelect(idx)
                        }
                    }
                },
                anchorViewAt = { anchorViews[it] },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state                   = pagerState,
                modifier                = Modifier.fillMaxSize(),
                beyondViewportPageCount = tabs.size - 1,
                userScrollEnabled       = !inAdhkarSelect,
                key                     = { tabs[it].route },
            ) { page ->
                CompositionLocalProvider(LocalScrollToTop provides scrollToTopFlows[page]) {
                    tabs[page].content(innerPadding)
                }
            }

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

@Composable
private fun TooltipAnchorRow(
    screens: List<NavTab>,
    onReady: (index: Int, view: View) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        screens.forEachIndexed { index, screen ->
            AndroidView(
                modifier = Modifier.weight(1f).height(1.dp),
                factory  = { ctx ->
                    View(ctx).apply {
                        visibility = View.INVISIBLE
                        ViewCompat.setTooltipText(this, ctx.getString(screen.labelRes))
                        onReady(index, this)
                    }
                },
                update = { view ->
                    ViewCompat.setTooltipText(view, view.context.getString(screen.labelRes))
                },
            )
        }
    }
}