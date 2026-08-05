package com.lhacenmed.khatmah.core

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.MessageQueue
import android.view.Menu
import android.view.View
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.lhacenmed.khatmah.core.ui.SizedDrawable
import com.lhacenmed.khatmah.core.ui.UiScale
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.commitNow
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTabs
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.nav.Reselectable
import com.lhacenmed.khatmah.core.ui.theme.Theme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.shared.util.ThemeManager
import com.lhacenmed.khatmah.databinding.ActivityMainBinding
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarTab
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import com.lhacenmed.khatmah.feature.prayer.data.PrayerSettings
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel
import com.lhacenmed.khatmah.feature.update.UpdateChecker
import com.lhacenmed.khatmah.feature.update.UpdateRegistry
import com.lhacenmed.khatmah.feature.update.UpdateState
import com.lhacenmed.khatmah.feature.update.UpdateStore
import com.lhacenmed.khatmah.feature.update.ui.UpdateGate
import com.lhacenmed.khatmah.shared.util.NetworkMonitor
import com.lhacenmed.khatmah.BuildConfig
import com.lhacenmed.khatmah.onboarding.OnboardingActivity
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import com.lhacenmed.khatmah.widget.PrayerWidget
import com.lhacenmed.khatmah.widget.WidgetAction
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Native edge-to-edge tab host. A MaterialToolbar + BottomNavigationView provide the chrome with
 * native gestures, tooltips and transitions; each tab's body is a [Fragment] in a single
 * container, created on first selection and then kept — switching tabs hides the old body rather
 * than tearing it down, so a screen is never rebuilt on the way back to it. Detail screens are
 * separate Activities (see [IntentNavigator]).
 *
 * Colours come from the Activity's own theme, which [ThemeManager] resolves in `onCreate`, so the
 * chrome, the tab bodies and any Compose left in them are painted from one palette.
 *
 * Tab order: 0 Today · 1 Adhkar · 2 Prayers · 3 Qadaa · 4 More.
 */
@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    private lateinit var binding: ActivityMainBinding

    private var selectedTab = 0

    /** The palette in force for this instance; a change to it recreates the Activity. */
    private val scheme: ColorScheme by lazy { resolveColorScheme(this) }

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

    /**
     * System back on a non-home tab returns to the home tab (AntennaPod's default-page logic). On the
     * home tab this callback is disabled (see [applyChrome]), so back falls through to the system and
     * the native predictive-back exit animation runs instead of being intercepted.
     */
    private val tabBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selectTab(0) // home tab
        }
    }

    private val adhkarSelecting: Boolean
        get() = selectedTab == adhkarTabIndex && adhkarVm.uiState.value.selectionMode

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // After the splash swaps in the app theme, so the palette layers on top of it.
        ThemeManager.applyTo(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // First run → the onboarding wizard; it returns to a fresh MainActivity when done.
        if (!OnboardingPrefs.isComplete(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Hoist QuranHomeViewModel to Activity scope at the earliest moment so its Room flow
        // is in flight before Compose runs; keep the splash until the first real frame.
        val homeVm = ViewModelProvider(
            this,
            QuranHomeViewModel.Factory(applicationContext),
        )[QuranHomeViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { !homeVm.splashReady }

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
        selectTab(selectedTab)
        // Deep links apply only on a fresh launch — never override the restored tab on recreate.
        if (savedInstanceState == null) handleLaunchIntent(intent)
        observeSettingsForWidget()
        // Check for a newer build once per fresh launch; UpdateGate prompts when one is found.
        if (savedInstanceState == null) checkForUpdate()

        // Colour the native chrome + set the title/pill synchronously before the first frame, so a
        // theme switch (Activity recreate) never flashes baseline colours, the app name, or a
        // missing active pill before the Compose effect catches up.
        applyChrome(adhkarSelecting)

        // Launch-time "update available" prompt. An AlertDialog, so it draws in its own window
        // and this view stays empty — taps fall straight through to the tab beneath it.
        binding.updateGate.setContent {
            Theme { UpdateGate() }
        }

        // The contextual toolbar follows Adhkar's selection mode wherever it is driven from —
        // the tab body, the menu, or system back.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adhkarVm.uiState.collect { applyChrome(adhkarSelecting) }
            }
        }
    }

    // ── Tab bodies ────────────────────────────────────────────────────────────

    /** The tab whose body is currently in front, so an unchanged selection costs no transaction. */
    private var shownRoute: String? = null

    /** Whether the idle-time tab builder is currently registered (see [prewarmTabs]). */
    private var prewarming = false

    /** Set while the bar is being moved in code, so it is not mistaken for a tap (see [selectTab]). */
    private var selectingProgrammatically = false

    /**
     * Brings tab [index] to the front: its body is created the first time it is asked for and
     * kept from then on, so returning to a tab shows it exactly as it was left. Hidden bodies are
     * held at STARTED — alive and still collecting, but not treated as the screen in front.
     *
     * A deep link can arrive while the state is saved (the app was in the background), when a
     * transaction would throw. Nothing is lost by returning: [onStart] runs before the user sees
     * anything and puts the tab up then.
     */
    private fun showTab(index: Int) {
        val target = AppTabs[index]
        if (supportFragmentManager.isStateSaved || shownRoute == target.route) return
        shownRoute = target.route
        // commitNow, not commit: a queued transaction would land a frame or two after the
        // toolbar, which reads as the bar changing before the body catches up.
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            val body = supportFragmentManager.findFragmentByTag(target.route)
                ?: target.newFragment().also { add(R.id.tab_container, it, target.route) }
            setMaxLifecycle(body, Lifecycle.State.RESUMED)
            AppTabs.forEach { tab ->
                if (tab === target) return@forEach
                supportFragmentManager.findFragmentByTag(tab.route)
                    ?.let { setMaxLifecycle(it, Lifecycle.State.STARTED) }
            }
        }
        applyTabVisibility()
    }

    /**
     * Shows the selected tab and holds the rest INVISIBLE rather than GONE.
     *
     * That distinction is the whole of what prewarming buys. A gone view is never measured, and a
     * body's content — a Compose composition, a list's first bind — is built when it is measured.
     * Kept gone, a prewarmed tab is an empty shell that does its real work at the moment it is
     * shown, which is the delay prewarming exists to remove. Invisible costs a measure pass the
     * app is idle for anyway, and leaves nothing to do on the tap.
     *
     * Reads [selectedTab] rather than taking a target, so it is safe to re-run at any point to
     * bring the container back in line — which [onStart] does, because a restore reaches
     * [showTab] before the fragments have views to make visible or not.
     */
    private fun applyTabVisibility() {
        val target = AppTabs[selectedTab]
        AppTabs.forEach { tab ->
            val body = supportFragmentManager.findFragmentByTag(tab.route) ?: return@forEach
            body.view?.visibility = if (tab === target) View.VISIBLE else View.INVISIBLE
        }
    }

    /**
     * Builds the tabs the user hasn't opened yet, one per idle turn of the main thread.
     *
     * Only the opening tab is needed for the first frame; building the rest alongside it would
     * add their inflation to launch for no gain, since nothing can be tapped before that frame is
     * up. Building them lazily instead just moves the same cost onto the first tap. Idle time is
     * the third option and the right one: the app is on screen and doing nothing, so the tabs are
     * ready well before the user reaches them, and every switch is then a show/hide with nothing
     * to construct.
     *
     * One per turn rather than all at once, so a stretch of idle is never spent long enough to
     * swallow a tap that arrives mid-way.
     */
    private val tabPrewarmer = MessageQueue.IdleHandler {
        val next = AppTabs.firstOrNull { supportFragmentManager.findFragmentByTag(it.route) == null }
        // Give up the handler when there is nothing left to build, or while transactions are
        // unavailable — onStart re-registers it. Staying registered through a saved state would
        // spin against an idle queue for as long as the app sits in the background.
        if (next == null || supportFragmentManager.isStateSaved) {
            prewarming = false
            return@IdleHandler false
        }
        supportFragmentManager.commitNow {
            setReorderingAllowed(true)
            val body = next.newFragment()
            add(R.id.tab_container, body, next.route)
            setMaxLifecycle(body, Lifecycle.State.STARTED)
        }
        // A body arrives visible and would sit over the tab in front. commitNow already ran the
        // transaction, so this lands before the next frame and nothing is ever drawn on top.
        supportFragmentManager.findFragmentByTag(next.route)?.view?.visibility = View.INVISIBLE
        true
    }

    private fun prewarmTabs() {
        if (prewarming) return
        prewarming = true
        Looper.myQueue().addIdleHandler(tabPrewarmer)
    }

    /** Tells the showing tab its bar item was tapped again; tabs that don't care don't implement it. */
    private fun reselectTab(index: Int) {
        val body = supportFragmentManager.findFragmentByTag(AppTabs[index].route)
        (body as? Reselectable)?.onReselect()
    }

    /**
     * Connectivity-driven update check; populates [UpdateRegistry] (and persists via [UpdateStore])
     * so [UpdateGate] can prompt — now and on any later launch, even offline. Runs whenever the
     * device is online: at launch if already connected, and again the moment connectivity returns
     * for a user who opened the app offline. Skips re-checking while a download is mid-flight or a
     * finished APK is awaiting install. Skipped for debug builds, whose `.debug` applicationId would
     * side-load the release APK as a separate app rather than update in place.
     */
    private fun checkForUpdate() {
        if (BuildConfig.DEBUG) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkMonitor.online(applicationContext).collect { online ->
                    if (!online || UpdateRegistry.isActive) return@collect
                    if (UpdateRegistry.stateOf() is UpdateState.Downloaded) return@collect
                    UpdateChecker.check()?.let {
                        UpdateStore.save(applicationContext, it)
                        UpdateRegistry.setAvailable(it)
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

    /**
     * Puts the selected tab up. Doing it here rather than in `onCreate` covers every route in
     * with one line — a fresh launch, a restored instance, and a deep link that arrived while
     * transactions were unavailable.
     */
    override fun onStart() {
        super.onStart()
        showTab(selectedTab)
        // Again here, and unconditionally: on a restore the bar is set in onCreate, so showTab has
        // already run and returned early by the time the fragments are given their views. Only
        // once those exist can the container be told which one to show. This runs before the first
        // frame, so a restored screen is never drawn with its tabs stacked.
        applyTabVisibility()
        prewarmTabs()
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

        // Force BottomNavigationView to ignore insets completely. By default, it aggressively adds
        // system nav insets to its own paddingBottom. Since its height is fixed at 70dp, adding 
        // 48dp of padding internally leaves only 22dp for icons/text, completely squishing them.
        // We already manually handle the system nav bar with the bottomPadding view below it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }
    }

    /** Platform status-bar height, resolved synchronously (no inset dispatch required). */
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun wireBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val next = item.itemId - 1 // ids are tab index + 1
            if (adhkarVm.uiState.value.selectionMode) adhkarVm.exitSelectionMode()
            selectedTab = next
            showTab(next)
            applyChrome(adhkarSelecting)
            true
        }
        // Re-selection is a separate signal on the platform's own bar, so the tab-change path
        // above never has to ask whether a tap was really a change.
        binding.bottomNav.setOnItemReselectedListener {
            if (!selectingProgrammatically) reselectTab(selectedTab)
        }
    }

    /**
     * Moves the bar to [index] without the change reading as a tap.
     *
     * Restoring the bar after a recreate — a theme or night-mode change — asks it for the tab that
     * is already selected, and the bar answers that with a re-selection. Left unguarded, rotating
     * into dark mode on the Quran tab would count as tapping it twice and open the reader.
     */
    private fun selectTab(index: Int) {
        selectingProgrammatically = true
        binding.bottomNav.selectedItemId = index + 1
        selectingProgrammatically = false
    }

    /**
     * Re-applies the toolbar (title/subtitle/colours/up-arrow) and bottom-nav colours from the
     * active [scheme], plus the Adhkar selection back-callback and menu. Called whenever the tab
     * or the selection state changes.
     */
    private fun applyChrome(selecting: Boolean) {
        val bar = supportActionBar ?: return

        val tabSpec = AppTabs[selectedTab]
        bar.title = if (selecting)
            getString(R.string.n_selected, adhkarVm.uiState.value.selectedIds.size)
        else getString(tabSpec.toolbarTitleRes)
        bar.subtitle = if (!selecting) tabSpec.subtitle(this) else null
        bar.setDisplayHomeAsUpEnabled(selecting)
        selectionBackCallback.isEnabled = selecting
        // Intercept back only to return to the home tab; on the home tab leave it to the system so
        // the native predictive-back exit animation runs.
        tabBackCallback.isEnabled = selectedTab != 0

        // Paint the window background with the active surface colour so the recreate cross-fade
        // (theme / locale change) shows the chrome's colour in the status-bar and nav-strip inset
        // regions from the first frame — instead of the default windowBackground flashing through
        // as a momentarily shorter toolbar or a reset bottom bar.
        window.setBackgroundDrawable(ColorDrawable(scheme.surface.toArgb()))

        // ── Toolbar colours ──
        // surfaceContainer (same as the bottom nav) so the chrome stands out from the surface body.
        val toolbarBg = if (selecting) scheme.primaryContainer else scheme.surfaceContainer
        val onToolbar = if (selecting) scheme.onPrimaryContainer else scheme.onSurface
        chromeIconColor = (if (selecting) scheme.onPrimaryContainer else scheme.onSurface).toArgb()
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
                    .setIcon(actionIcon(action.iconRes))
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

    /**
     * Loads a tab-action drawable normalised to the standard [ACTION_ICON_DP] toolbar size.
     * Tab icons are authored at varying intrinsic sizes (some 48dp for full-bleed bottom-nav
     * use), and a menu item renders its icon at the drawable's intrinsic size — so without this
     * the larger ones look oversized next to the 24dp icons. [SizedDrawable] overrides only the
     * reported size and delegates drawing and tinting to the vector, so the chrome-colour tint
     * pass above still recolours it. [mutate] keeps the tint local to this menu item.
     */
    private fun actionIcon(@DrawableRes res: Int): Drawable? {
        val src = ContextCompat.getDrawable(this, res)?.mutate() ?: return null
        val px  = (ACTION_ICON_DP * resources.displayMetrics.density).toInt()
        return SizedDrawable(src, px)
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
        if (index >= 0) selectTab(index)
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
        private const val ACTION_ICON_DP = 24f // standard toolbar action-icon size

        // Contextual (Adhkar selection) menu item ids; offset to avoid clashing with the
        // per-tab action indices (0, 1, …) used for normal toolbar actions.
        private const val ID_SELECT_ALL = 1001
        private const val ID_DELETE = 1002
    }
}
