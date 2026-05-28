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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.motion.animatedComposable
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.BottomNavBar
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.debug.DbBrowserPage
import com.lhacenmed.khatmah.feature.debug.FileBrowserPage
import com.lhacenmed.khatmah.feature.khatmah.ui.DailyAlarmPage
import com.lhacenmed.khatmah.feature.khatmah.ui.NewKhatmahPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.CalcMethodContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.DstContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.HigherLatContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.JuristicContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.ManualCorrectionsContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.AdhanRemindersPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound.AdhanSoundSelectionPage
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryPage
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaPage
import com.lhacenmed.khatmah.feature.quran.ui.debug.DebugWarshPage
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranReaderScreen
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranSessionReaderScreen
import com.lhacenmed.khatmah.feature.quran.ui.search.QuranSearchPage
import com.lhacenmed.khatmah.feature.settings.DarkThemePage
import com.lhacenmed.khatmah.feature.settings.LanguagePage
import com.lhacenmed.khatmah.feature.settings.ThemeSettingsPage
import com.lhacenmed.khatmah.feature.trips.ui.TripRequestsPage
import com.lhacenmed.khatmah.onboarding.CitySelectPage
import com.lhacenmed.khatmah.onboarding.CountrySelectPage
import com.lhacenmed.khatmah.onboarding.LanguageOnboardingPage
import com.lhacenmed.khatmah.onboarding.LocationPermissionPage
import com.lhacenmed.khatmah.onboarding.NotificationPermissionPage
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.WidgetNavRequest
import kotlinx.coroutines.flow.MutableSharedFlow

// ─── Root composable ──────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppEntry() {
    val context = LocalContext.current

    val start = remember {
        if (OnboardingPrefs.isComplete(context)) Route.MAIN else Route.ONBOARDING_LANGUAGE
    }

    // Tabs driven by AppRegistry — add a new AppTab object + one registry line to extend.
    val tabs = AppRegistry.tabs.map { it.toNavTab() }

    // Legacy NavPage destinations not yet migrated to AppPage.
    // Remove an entry here once its file gains an AppPage object and is added to AppRegistry.pages.
    val legacyPages = listOf(ThemeSettingsPage, DarkThemePage, LanguagePage)

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
                selectedTabIndex = tabs.indexOfFirst { it.route == Route.ADHKAR }.coerceAtLeast(0)
            }
            navController.navigate(route)
        }
        WidgetNavRequest.consume()
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

            // ── App shell ─────────────────────────────────────────────────────
            animatedComposable(Route.MAIN) {
                MainScreen(
                    tabs          = tabs,
                    selectedIndex = selectedTabIndex,
                    onSelect      = { selectedTabIndex = it },
                )
            }

            // ── Legacy settings pages (migrate to AppPage + AppRegistry progressively) ──
            legacyPages.forEach { page -> animatedComposable(page.route) { page.content() } }

            // ── Prayer settings sub-pages ─────────────────────────────────────
            animatedComposable(Route.PRAYER_SETTINGS)           { PrayerSettingsContent()    }
            animatedComposable(Route.PRAYER_CALC_METHOD)        { CalcMethodContent()        }
            animatedComposable(Route.PRAYER_JURISTIC)           { JuristicContent()          }
            animatedComposable(Route.PRAYER_DST)                { DstContent()               }
            animatedComposable(Route.PRAYER_MANUAL_CORRECTIONS) { ManualCorrectionsContent() }
            animatedComposable(Route.PRAYER_HIGHER_LAT)         { HigherLatContent()         }
            animatedComposable(Route.ADHAN_REMINDERS)           { AdhanRemindersPage()       }
            animatedComposable(
                route     = Route.ADHAN_SOUND_SELECTION,
                arguments = listOf(navArgument("prayerId") { type = NavType.IntType }),
            ) { backStack ->
                AdhanSoundSelectionPage(prayerId = backStack.arguments?.getInt("prayerId") ?: 0)
            }

            // ── Quran ─────────────────────────────────────────────────────────
            animatedComposable(
                route     = Route.QURAN_READER,
                arguments = listOf(
                    navArgument("suraNum") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("ayaNum")  { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { QuranReaderScreen() }
            animatedComposable(Route.QURAN_SEARCH) { QuranSearchPage() }
            animatedComposable(Route.DEBUG_WARSH)  { DebugWarshPage()  }

            // ── Qibla ─────────────────────────────────────────────────────────
            animatedComposable(Route.QIBLA) { QiblaPage() }

            // ── Adhkar editor ─────────────────────────────────────────────────
            animatedComposable(
                route     = Route.ADHKAR_EDITOR,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                AdhkarEditorPage(
                    categoryId = backStack.arguments?.getString("categoryId")
                        .orEmpty().ifEmpty { null },
                )
            }

            // ── Khatmah ───────────────────────────────────────────────────────
            animatedComposable(Route.NEW_KHATMAH) { NewKhatmahPage() }
            animatedComposable(Route.DAILY_ALARM) { DailyAlarmPage() }
            animatedComposable(
                route     = Route.QURAN_SESSION_READER,
                arguments = listOf(
                    navArgument("startPage") { type = NavType.IntType },
                    navArgument("endPage")   { type = NavType.IntType },
                ),
            ) { back ->
                QuranSessionReaderScreen(
                    startPage = back.arguments?.getInt("startPage") ?: 1,
                    endPage   = back.arguments?.getInt("endPage")   ?: 1,
                )
            }

            // ── Debug ─────────────────────────────────────────────────────────
            animatedComposable(Route.DEBUG_DB)      { DbBrowserPage()   }
            animatedComposable(Route.FILES_BROWSER) { FileBrowserPage() }

            // ── Trips / Qadaa ─────────────────────────────────────────────────
            animatedComposable(Route.TRIP_REQUESTS) { TripRequestsPage()  }
            animatedComposable(Route.QADAA)         { QadaaPage()         }
            animatedComposable(Route.QADAA_HISTORY) { QadaaHistoryPage()  }

            // ── AppRegistry pages (auto-driven) ───────────────────────────────
            // To add a new page: create AppPage object → add to AppRegistry.pages.
            // Remove the corresponding manual block above when migrating an existing page.
            AppRegistry.pages.forEach { page ->
                animatedComposable(route = page.route, arguments = page.arguments) { back ->
                    page.Content(back)
                }
            }
        }
    }
}

// ─── Main shell ───────────────────────────────────────────────────────────────

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

    // ── Pager ─────────────────────────────────────────────────────────────────
    val pagerState   = rememberPagerState(
        initialPage = selectedIndex,
        pageCount   = { tabs.size },
    )
    val adhkarTabIdx = remember(tabs) { tabs.indexOfFirst { it.route == Route.ADHKAR } }

    val currentTab     = tabs[pagerState.currentPage]
    val isAdhkarTab    = currentTab.route == Route.ADHKAR
    val isPrayersTab   = currentTab.route == Route.PRAYERS
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
                                onClick     = { nav.navigate(Route.adhkarEditor()) },
                                tooltipText = stringResource(R.string.add_adhkar),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_adhkar))
                            }
                        }
                    } else if (isPrayersTab) {
                        IconButton(
                            onClick     = { nav.navigate(Route.QIBLA) },
                            tooltipText = stringResource(R.string.prayers_qibla),
                        ) {
                            Icon(
                                painter            = painterResource(R.drawable.ic_kaaba),
                                contentDescription = stringResource(R.string.prayers_qibla),
                                modifier           = Modifier.size(26.dp),
                            )
                        }
                        IconButton(
                            onClick     = { nav.navigate(Route.PRAYER_SETTINGS) },
                            tooltipText = stringResource(R.string.prayers_settings),
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.prayers_settings),
                            )
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