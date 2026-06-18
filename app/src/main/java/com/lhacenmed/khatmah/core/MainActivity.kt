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
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.databinding.ActivityMainBinding
import android.widget.Toast
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarEditorActivity
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.more.MoreTab
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.prayer.ui.PrayersTab
import com.lhacenmed.khatmah.feature.prayer.ui.settings.PrayerSettingsActivity
import com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla.QiblaActivity
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaHistoryActivity
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaTab
import com.lhacenmed.khatmah.feature.qadaa.ui.QadaaViewModel
import com.lhacenmed.khatmah.feature.today.TodayTab
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
    private val scrollFlows = Array(5) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    /** Current toolbar menu-icon tint, derived from the active colour scheme. */
    private var chromeIconColor = 0

    // Activity-scoped — shared with the tab bodies (same instance via viewModel(activity))
    // so the toolbar can drive Adhkar selection mode and Qadaa actions.
    private val adhkarVm: AdhkarViewModel by viewModels()
    private val qadaaVm: QadaaViewModel by viewModels()

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
                selectedTab != 0  -> binding.bottomNav.selectedItemId = R.id.nav_today
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
        get() = selectedTab == 1 && adhkarVm.uiState.value.selectionMode

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
        wireBottomNav()
        binding.bottomNav.selectedItemId = tabItemId(selectedTab)
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
                val selecting    = tab == 1 && adhkarState.selectionMode

                // Keep the native chrome in lock-step with the Compose scheme + tab state.
                LaunchedEffect(scheme, tab, selecting, adhkarState.selectedIds.size) {
                    applyChrome(scheme, selecting)
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val pagerState = rememberPagerState(initialPage = tab) { 5 }
                    // Tab taps drive the pager; the jump is instant (no swipe, no reload).
                    LaunchedEffect(tab) {
                        if (pagerState.currentPage != tab) pagerState.scrollToPage(tab)
                    }
                    HorizontalPager(
                        state                   = pagerState,
                        modifier                = Modifier.fillMaxSize(),
                        userScrollEnabled       = false,
                        beyondViewportPageCount = 4, // keep all five tabs composed
                        key                     = { it },
                    ) { page ->
                        CompositionLocalProvider(
                            LocalNavigator provides navigator,
                            LocalScrollToTop provides scrollFlows[page],
                        ) {
                            when (page) {
                                0    -> TodayTab.Content(PaddingValues(0.dp))
                                1    -> AdhkarTab.Content(PaddingValues(0.dp))
                                2    -> PrayersTab.Content(PaddingValues(0.dp))
                                3    -> QadaaTab.Content(PaddingValues(0.dp))
                                else -> MoreTab.Content(PaddingValues(0.dp))
                            }
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

    private fun tabItemId(tab: Int) = when (tab) {
        0    -> R.id.nav_today
        1    -> R.id.nav_adhkar
        2    -> R.id.nav_prayers
        3    -> R.id.nav_qadaa
        else -> R.id.nav_more
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
            val next = when (item.itemId) {
                R.id.nav_today   -> 0
                R.id.nav_adhkar  -> 1
                R.id.nav_prayers -> 2
                R.id.nav_qadaa   -> 3
                R.id.nav_more    -> 4
                else             -> return@setOnItemSelectedListener false
            }
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

        bar.title = when {
            selecting          -> getString(R.string.n_selected, adhkarVm.uiState.value.selectedIds.size)
            selectedTab == 0   -> getString(R.string.today)
            selectedTab == 1   -> getString(R.string.adhkar)
            selectedTab == 2   -> getString(R.string.prayers_screen_title)
            selectedTab == 3   -> getString(R.string.more_qadaa)
            else               -> getString(R.string.more)
        }
        bar.subtitle = if (!selecting && selectedTab == 2)
            OnboardingPrefs.location(this)?.cityName?.takeIf { it.isNotBlank() } else null
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tab_actions, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val selecting = adhkarSelecting
        menu.findItem(R.id.action_adhkar_add)?.isVisible = selectedTab == 1 && !selecting
        menu.findItem(R.id.action_adhkar_select_all)?.isVisible = selecting
        menu.findItem(R.id.action_adhkar_delete)?.isVisible = selecting
        menu.findItem(R.id.action_qibla)?.isVisible = selectedTab == 2
        menu.findItem(R.id.action_prayer_settings)?.isVisible = selectedTab == 2
        menu.findItem(R.id.action_qadaa_history)?.isVisible = selectedTab == 3
        menu.findItem(R.id.action_qadaa_add)?.isVisible = selectedTab == 3
        // Tint visible icons to match the scheme-derived chrome colour.
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(chromeIconColor)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home             -> { adhkarVm.exitSelectionMode(); true }
        R.id.action_adhkar_add        -> { startActivity(Intent(this, AdhkarEditorActivity::class.java)); true }
        R.id.action_adhkar_select_all -> { adhkarVm.toggleSelectAll(); true }
        R.id.action_adhkar_delete     -> { adhkarVm.deleteSelected(); true }
        R.id.action_qibla             -> { startActivity(Intent(this, QiblaActivity::class.java)); true }
        R.id.action_prayer_settings   -> { startActivity(Intent(this, PrayerSettingsActivity::class.java)); true }
        R.id.action_qadaa_history     -> { startActivity(Intent(this, QadaaHistoryActivity::class.java)); true }
        R.id.action_qadaa_add         -> { qadaaVm.requestAddPrayers(); true }
        else                          -> super.onOptionsItemSelected(item)
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
        val itemId = when (route) {
            "today"   -> R.id.nav_today
            "adhkar"  -> R.id.nav_adhkar
            "prayers" -> R.id.nav_prayers
            "more"    -> R.id.nav_more
            else      -> null
        } ?: return
        binding.bottomNav.selectedItemId = itemId
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
    }
}
