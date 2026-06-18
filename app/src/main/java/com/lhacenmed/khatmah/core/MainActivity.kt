package com.lhacenmed.khatmah.core

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTabs
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.databinding.ActivityMainBinding
import android.widget.Toast
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.today.TodayViewModel
import com.lhacenmed.khatmah.onboarding.OnboardingActivity
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.PrayerWidget
import com.lhacenmed.khatmah.widget.WidgetAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Native edge-to-edge tab host. A MaterialToolbar + BottomNavigationView provide the
 * chrome with native gestures, tooltips and transitions; the five tab bodies live in a
 * swipe-disabled [HorizontalPager] so every tab stays composed — switching tabs never
 * disposes/reloads a screen. Detail screens are separate Activities (see [IntentNavigator]).
 *
 * The XML chrome is recoloured from the Compose [MaterialTheme.colorScheme] (the single
 * source of truth), so dynamic colour, custom palettes and high-contrast all stay in sync.
 *
 * Tab order: 0 Today · 1 Adhkar · 2 Prayers · 3 Qadaa · 4 More.
 */
@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var selectedTab by mutableIntStateOf(0)

    /** Per-tab scroll-to-top signal, emitted when the active tab is reselected. */
    private val scrollFlows = List(AppTabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    /** Index of the Adhkar tab — selection mode (the contextual toolbar) is its feature. */
    private val adhkarTabIndex = AppTabs.indexOf(AdhkarTab)

    /** Current toolbar menu-icon tint, derived from the active colour scheme. */
    private var chromeIconColor = 0

    // Activity-scoped — shared with the Adhkar tab body (same instance via viewModel(activity))
    // so the toolbar can drive its selection mode.
    private val adhkarVm: AdhkarViewModel by viewModels()

    /** Enabled only while the Adhkar tab is in selection mode; back then exits selection. */
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = adhkarVm.exitSelectionMode()
    }

    /** True while the "tap back again to exit" window is open (Today tab only). */
    private var backToExitPending = false

    /**
     * System back when not on the home tab returns to it (AntennaPod's default-page logic);
     * on the home tab, the first back asks for confirmation and the second exits.
     */
    private val tabBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            when {
                selectedTab != 0  -> binding.bottomNav.selectedItemId = 1 // home tab (index 0 + 1)
                backToExitPending -> finish()
                else              -> {
                    backToExitPending = true
                    Toast.makeText(this@MainActivity, R.string.tap_back_again_to_exit, Toast.LENGTH_SHORT).show()
                    binding.bottomNav.postDelayed({ backToExitPending = false }, EXIT_CONFIRM_WINDOW_MS)
                }
            }
        }
    }

    private val adhkarSelecting: Boolean
        get() = selectedTab == adhkarTabIndex && adhkarVm.uiState.value.selectionMode

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // First run → the onboarding wizard; it returns to a fresh MainActivity when done.
        if (!OnboardingPrefs.isComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Hoist TodayViewModel to Activity scope at the earliest moment so its Room flow
        // is in flight before Compose runs; keep the splash until the first real frame.
        val todayVm = ViewModelProvider(
            this,
            TodayViewModel.Factory(applicationContext),
        )[TodayViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { !todayVm.splashReady }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { adhkarVm.exitSelectionMode() }

        // Order matters: the dispatcher invokes the most-recently-added enabled callback first,
        // so selection (added last) takes priority over the tab/exit handler when active.
        onBackPressedDispatcher.addCallback(this, tabBackCallback)
        onBackPressedDispatcher.addCallback(this, selectionBackCallback)

        // Restore the active tab across recreation (night-mode / locale change) so the
        // Compose content and the bottom-nav selection stay in agreement.
        selectedTab = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: 0

        applyInsets()
        buildBottomNav()
        wireBottomNav()
        binding.bottomNav.selectedItemId = selectedTab + 1
        // Deep links apply only on a fresh launch — never override the restored tab on recreate.
        if (savedInstanceState == null) handleLaunchIntent(intent)
        observeSettingsForWidget()

        // Colour the native chrome + set the title/pill synchronously before the first frame, so a
        // theme switch (Activity recreate) never flashes baseline colours, the app name, or a
        // missing active pill before the Compose effect catches up.
        applyChrome(resolveColorScheme(this, isAppInDarkTheme(this)), adhkarSelecting)

        val navigator = IntentNavigator(this)

        binding.composeView.setContent {
            Theme {
                val scheme       = MaterialTheme.colorScheme
                val adhkarState  by adhkarVm.uiState.collectAsState()
                val tab          = selectedTab
                val selecting    = tab == adhkarTabIndex && adhkarState.selectionMode

                // Keep the native chrome in lock-step with the Compose scheme + tab state.
                LaunchedEffect(scheme, tab, selecting, adhkarState.selectedIds.size) {
                    applyChrome(scheme, selecting)
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val pagerState = rememberPagerState(initialPage = tab) { AppTabs.size }
                    // Tab taps drive the pager; the jump is instant (no swipe, no reload).
                    LaunchedEffect(tab) {
                        if (pagerState.currentPage != tab) pagerState.scrollToPage(tab)
                    }
                    HorizontalPager(
                        state                   = pagerState,
                        modifier                = Modifier.fillMaxSize(),
                        userScrollEnabled       = false,
                        beyondViewportPageCount = AppTabs.size - 1, // keep every tab composed
                        key                     = { it },
                    ) { page ->
                        CompositionLocalProvider(
                            LocalNavigator provides navigator,
                            LocalScrollToTop provides scrollFlows[page],
                        ) {
                            AppTabs[page].Content(PaddingValues(0.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTab)
    }

    /** Refresh the widget whenever the user leaves the app. */
    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { PrayerWidget().updateAll(this@MainActivity) }
    }

    /** Builds the bottom-nav items from [AppTabs]; item id = tab index + 1 (avoids id 0). */
    private fun buildBottomNav() {
        val menu = binding.bottomNav.menu
        AppTabs.forEachIndexed { index, tab ->
            menu.add(Menu.NONE, index + 1, index, tab.titleRes).setIcon(tab.iconRes)
        }
    }

    // ── Native chrome ─────────────────────────────────────────────────────────

    private fun applyInsets() {
        // Seed the status-bar inset synchronously so the toolbar is full-height on the very first
        // frame after a recreate (theme / locale change) — before the async listener runs, and even
        // if the first inset dispatch arrives as a transient zero.
        binding.toolbar.updatePadding(top = statusBarHeight())

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Ignore a transient zero status-bar inset that would momentarily collapse the toolbar.
            if (bars.top > 0) binding.toolbar.updatePadding(top = bars.top)
            // Keep the bar a fixed 64dp; the system-nav area is the separate strip below it.
            binding.bottomPadding.updateLayoutParams { height = bars.bottom }
            insets
        }
    }

    /** Platform status-bar height, resolved synchronously (no inset dispatch required). */
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun wireBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val next = item.itemId - 1 // ids are tab index + 1
            if (next == selectedTab) {
                scrollFlows[next].tryEmit(Unit)
            } else {
                if (adhkarVm.uiState.value.selectionMode) adhkarVm.exitSelectionMode()
                selectedTab = next // recomposes → applyChrome + pager jump
            }
            true
        }
    }

    /**
     * Re-applies the toolbar (title/subtitle/colours/up-arrow) and bottom-nav colours from
     * [scheme], plus the Adhkar selection back-callback and menu. Called from a Compose effect
     * so it tracks the live colour scheme and tab/selection state.
     */
    private fun applyChrome(scheme: ColorScheme, selecting: Boolean) {
        val bar = supportActionBar ?: return

        val tabSpec = AppTabs[selectedTab]
        bar.title = if (selecting)
            getString(R.string.n_selected, adhkarVm.uiState.value.selectedIds.size)
        else getString(tabSpec.toolbarTitleRes)
        bar.subtitle = if (!selecting) tabSpec.subtitle(this) else null
        bar.setDisplayHomeAsUpEnabled(selecting)
        selectionBackCallback.isEnabled = selecting

        // Paint the window background with the active surface colour so the recreate cross-fade
        // (theme / locale change) shows the chrome's colour in the status-bar and nav-strip inset
        // regions from the first frame — instead of the default windowBackground flashing through
        // as a momentarily shorter toolbar or a reset bottom bar.
        window.setBackgroundDrawable(ColorDrawable(scheme.surface.toArgb()))

        // ── Toolbar colours ──
        val toolbarBg = if (selecting) scheme.primaryContainer else scheme.surface
        val onToolbar = if (selecting) scheme.onPrimaryContainer else scheme.onSurface
        chromeIconColor = (if (selecting) scheme.onPrimaryContainer else scheme.onSurfaceVariant).toArgb()
        binding.toolbar.setBackgroundColor(toolbarBg.toArgb())
        binding.toolbar.setTitleTextColor(onToolbar.toArgb())
        binding.toolbar.setSubtitleTextColor(scheme.onSurfaceVariant.toArgb())
        binding.toolbar.navigationIcon?.setTint(onToolbar.toArgb())

        // ── Bottom-nav colours ──
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        binding.bottomNav.setBackgroundColor(scheme.surfaceContainer.toArgb())
        binding.bottomPadding.setBackgroundColor(scheme.surfaceContainer.toArgb())
        binding.bottomNav.itemIconTintList = ColorStateList(
            states, intArrayOf(scheme.onSecondaryContainer.toArgb(), scheme.onSurfaceVariant.toArgb()),
        )
        binding.bottomNav.itemTextColor = ColorStateList(
            states, intArrayOf(scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb()),
        )
        binding.bottomNav.itemActiveIndicatorColor =
            ColorStateList.valueOf(scheme.secondaryContainer.toArgb())

        invalidateOptionsMenu()
    }

    // ── Per-tab toolbar actions ───────────────────────────────────────────────

    // Toolbar actions are built dynamically in onPrepareOptionsMenu (from the active tab,
    // or the Adhkar selection-mode contextual bar) — there is no static menu XML.
    override fun onCreateOptionsMenu(menu: Menu): Boolean = true

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (adhkarSelecting) {
            // Contextual action bar for Adhkar selection mode.
            menu.add(Menu.NONE, ID_SELECT_ALL, 0, R.string.select_all)
                .setIcon(R.drawable.ic_done_all)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, ID_DELETE, 1, R.string.delete)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        } else {
            // Item id = action index within the active tab's action list.
            AppTabs[selectedTab].actions.forEachIndexed { index, action ->
                menu.add(Menu.NONE, index, index, action.titleRes)
                    .setIcon(action.iconRes)
                    .setShowAsActionFlags(
                        if (action.showAsText)
                            MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT
                        else MenuItem.SHOW_AS_ACTION_ALWAYS,
                    )
            }
        }
        // Tint visible icons to match the scheme-derived chrome colour.
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(chromeIconColor)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { adhkarVm.exitSelectionMode(); return true }
            ID_SELECT_ALL     -> { adhkarVm.toggleSelectAll(); return true }
            ID_DELETE         -> { adhkarVm.deleteSelected(); return true }
        }
        val action = AppTabs[selectedTab].actions.getOrNull(item.itemId)
            ?: return super.onOptionsItemSelected(item)
        action.onClick(this)
        return true
    }

    // ── Widget / reminder deep links ──────────────────────────────────────────

    /** Routes a widget/reminder tap to the matching tab. */
    private fun handleLaunchIntent(intent: Intent?) {
        if (!::binding.isInitialized) return
        val route = when (intent?.action) {
            WidgetAction.OPEN_PRAYERS        -> "prayers"
            "com.lhacenmed.khatmah.REMINDER" -> intent.getStringExtra("route")
            else                             -> null
        }
        val index = AppTabs.indexOfFirst { it.route == route }
        if (index >= 0) binding.bottomNav.selectedItemId = index + 1
    }

    /** Push a widget update on every settings save while at least STARTED. */
    private fun observeSettingsForWidget() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PrayerSettings.flow
                    .drop(1)
                    .collect { PrayerWidget().updateAll(this@MainActivity) }
            }
        }
    }

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
        private const val EXIT_CONFIRM_WINDOW_MS = 2000L

        // Contextual (Adhkar selection) menu item ids; offset to avoid clashing with the
        // per-tab action indices (0, 1, …) used for normal toolbar actions.
        private const val ID_SELECT_ALL = 1001
        private const val ID_DELETE = 1002
    }
}
