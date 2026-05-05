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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
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
import com.lhacenmed.khatmah.feature.settings.AboutPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarDetailPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorPage
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.more.MoreTab
import com.lhacenmed.khatmah.feature.khatmah.ui.NewKhatmahPage
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.AdhanRemindersPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.CalcMethodContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.DstContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.HigherLatContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.JuristicContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.calculations.ManualCorrectionsContent
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaPage
import com.lhacenmed.khatmah.feature.prayer.ui.settings.reminders.sound.AdhanSoundSelectionPage
import com.lhacenmed.khatmah.feature.quran.ui.IndexTab
import com.lhacenmed.khatmah.feature.quran.ui.debug.DebugWarshPage
import com.lhacenmed.khatmah.feature.quran.ui.reader.QuranReaderScreen
import com.lhacenmed.khatmah.feature.quran.ui.search.QuranSearchPage
import com.lhacenmed.khatmah.feature.settings.DarkThemePage
import com.lhacenmed.khatmah.feature.settings.LanguagePage
import com.lhacenmed.khatmah.feature.settings.ThemeSettingsPage
import com.lhacenmed.khatmah.feature.today.TodayTab
import com.lhacenmed.khatmah.feature.debug.DbBrowserPage
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

    val tabs  = listOf(TodayTab, AdhkarTab, PrayersTab, IndexTab, MoreTab)
    val pages = listOf(ThemeSettingsPage, DarkThemePage, LanguagePage, AboutPage)

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
        if (idx >= 0) selectedTabIndex = idx
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

            // ── General settings sub-pages ────────────────────────────────────
            pages.forEach { page -> animatedComposable(page.route) { page.content() } }

            // ── Prayer settings sub-pages ─────────────────────────────────────
            animatedComposable(Route.PRAYER_SETTINGS)           { PrayerSettingsContent()    }
            animatedComposable(Route.PRAYER_CALC_METHOD)        { CalcMethodContent()        }
            animatedComposable(Route.PRAYER_JURISTIC)           { JuristicContent()          }
            animatedComposable(Route.PRAYER_DST)                { DstContent()               }
            animatedComposable(Route.PRAYER_MANUAL_CORRECTIONS) { ManualCorrectionsContent() }
            animatedComposable(Route.PRAYER_HIGHER_LAT)         { HigherLatContent()         }
            animatedComposable(Route.ADHAN_REMINDERS)           { AdhanRemindersPage() }
            animatedComposable(
                route     = Route.ADHAN_SOUND_SELECTION,
                arguments = listOf(
                    navArgument("prayerId") { type = NavType.IntType },
                ),
            ) { backStack ->
                val prayerId = backStack.arguments?.getInt("prayerId") ?: 0
                AdhanSoundSelectionPage(prayerId = prayerId)
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
            animatedComposable(Route.DEBUG_WARSH) { DebugWarshPage() }

            // ── Qibla ─────────────────────────────────────────────────────────────────────
            animatedComposable(Route.QIBLA) { QiblaPage() }

            // ── Adhkar editor (create + edit) ─────────────────────────────────
            // categoryId empty → create mode; non-empty → edit mode.
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

            // ── Adhkar detail ─────────────────────────────────────────────────
            animatedComposable(
                route     = Route.ADHKAR_DETAIL,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType },
                ),
            ) { backStack ->
                AdhkarDetailPage(
                    categoryId = backStack.arguments?.getString("categoryId").orEmpty(),
                )
            }

            // ── Khatmah ───────────────────────────────────────────────────────────────────
            animatedComposable(Route.NEW_KHATMAH) { NewKhatmahPage() }

            animatedComposable(Route.DEBUG_DB)    { DbBrowserPage()  }
        }
    }
}

// ─── Main shell ───────────────────────────────────────────────────────────────

/**
 * Tab host screen with Adhkar-aware top bar.
 *
 * [AdhkarViewModel] is activity-scoped so [AdhkarTab]'s grid and this top bar
 * share the exact same instance — selection state stays in sync automatically.
 *
 * Back-press priority:
 *  1. If Adhkar selection mode is active → exit selection mode.
 *  2. If on any non-primary tab → return to tab 0 (Today).
 *  3. Otherwise fall through to system back.
 */
@Composable
private fun MainScreen(
    tabs: List<NavScreen>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val context    = LocalContext.current
    val nav        = LocalNavController.current
    val activity   = LocalActivity.current as ComponentActivity
    val adhkarVm: AdhkarViewModel = viewModel(activity)
    val adhkarState by adhkarVm.uiState.collectAsState()

    val currentTab     = tabs[selectedIndex]
    val isAdhkarTab    = currentTab.route == Route.ADHKAR
    val isPrayersTab   = currentTab.route == Route.PRAYERS
    val inAdhkarSelect = isAdhkarTab && adhkarState.selectionMode

    BackHandler(enabled = selectedIndex != 0 || inAdhkarSelect) {
        when {
            inAdhkarSelect -> adhkarVm.exitSelectionMode()
            else           -> onSelect(0)
        }
    }

    val scrollToTopFlows = remember { Array(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }
    val anchorViews      = remember { arrayOfNulls<View>(tabs.size) }

    // City name shown as subtitle when the Prayers tab is active.
    val prayersCityName = remember(isPrayersTab) {
        if (isPrayersTab) OnboardingPrefs.location(context)?.cityName.orEmpty() else ""
    }

    // Top bar title: show selection count when in selection mode
    val topBarTitle = when {
        inAdhkarSelect -> stringResource(R.string.n_selected, adhkarState.selectedIds.size)
        isPrayersTab   -> stringResource(R.string.prayers_screen_title)
        else           -> stringResource(currentTab.labelRes)
    }

    // Top bar container color highlights contextual (selection) mode
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
                            // Select-all toggle
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
                            // Delete selected
                            TextButton(onClick = adhkarVm::deleteSelected) {
                                Text(
                                    stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            // Add new adhkar category
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
                        // Prayer settings
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
                currentRoute = currentTab.route,
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
                modifier = Modifier.weight(1f).height(1.dp),
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