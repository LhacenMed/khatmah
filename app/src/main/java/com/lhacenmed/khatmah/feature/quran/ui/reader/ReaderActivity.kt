package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.graphics.Canvas
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.core.ui.UiScale
import com.lhacenmed.khatmah.core.ui.fitTitleText
import com.lhacenmed.khatmah.feature.audio.AyaAudioState
import com.lhacenmed.khatmah.feature.audio.GhReader
import com.lhacenmed.khatmah.feature.audio.GithubAudioRepository
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahSessionEntity
import com.lhacenmed.khatmah.feature.quran.data.BookmarkRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.ui.reader.book.BookPageView
import com.lhacenmed.khatmah.feature.quran.ui.reciter.ReaderAudioBar
import com.lhacenmed.khatmah.feature.quran.ui.reciter.ReaderAudioViewModel
import com.lhacenmed.khatmah.feature.quran.ui.search.ReaderSearchActivity
import com.lhacenmed.khatmah.feature.quran.ui.settings.ReaderSettingsActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Full-screen reader shell — mode-agnostic host for the QCF4 book pages and the native text pages.
 *
 * Hosts a [ViewPager2] of page fragments (built by the active [ReaderSource]) with an overlaid
 * surface-coloured toolbar that toggles with the immersive system bars on a page tap. The active
 * print decides the [ReaderSource]; everything mode-specific (page count, aya→page map, per-page
 * meta, page fragment) is routed through it, so this shell never branches on mode.
 *
 * Page-windowed Khatmah sessions are only honoured when the source [ReaderSource.supportsSession]
 * (the page-based QCF4 mushaf); the text reader always opens the full mushaf.
 */
class ReaderActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    private lateinit var rootFrame: FrameLayout
    private lateinit var pager: ViewPager2
    private lateinit var wirdWall: WirdWallView
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarArea: FrameLayout
    private lateinit var bottomBar: FrameLayout
    private lateinit var slider: SeekBar
    private lateinit var popup: TextView

    // Recitation bar (above the slider) — hidden until a verse is long-pressed.
    private lateinit var audioBar: ReaderAudioBar

    // Retained across configuration changes so recitation survives rotation (released in onCleared).
    private val audioVm: ReaderAudioViewModel by viewModels()
    private val audioController get() = audioVm.controller

    private lateinit var print: MushafPrint
    private lateinit var source: ReaderSource

    /** Readers available for the active riwaya; first one is used (single reader for now). */
    private val readers: List<GhReader> by lazy {
        GithubAudioRepository(applicationContext).readersFor(source.riwaya.dbKey)
    }

    /** Exposes the playback state so each page fragment can highlight the playing verse. */
    val audioState get() = audioController.state

    /** Last verse the page-follow acted on (packed sura:aya), so we react only when it changes. */
    private var followedKey = 0L

    private var chromeVisible = true

    // Auto-hides the chrome a short time after the window gains focus (Quran Android's PagerHandler).
    private val barHideHandler = BarHideHandler(this)
    private var metaMap: Map<Int, PageMeta> = emptyMap()

    // Reading window: the full mushaf by default, or a single session's [firstPage]..[lastPage].
    private var firstPage = 1
    private var lastPage = 0

    // Session reading: progress is remembered per [sessionId], independent of the full last-read page.
    private var isSession = false
    private var sessionId = 0L

    // Wird completion: the end wall exists only while an unread session is open, and
    // [wirdCompleting] makes the hand-off a one-shot (it stays set until the next wird is known).
    private var wirdActive = false
    private var wirdCompleting = false

    // The wird waiting behind the wall — prefetched, so a release hands over immediately instead
    // of pausing on a query.
    private var nextWird: KhatmahSessionEntity? = null

    private lateinit var wall: WirdWall


    // Cached aya→page map (0-based, this source's pagination) for jumping to a search/recitation hit.
    private var ayaPageCache: Map<Long, Int>? = null

    private val insetsController: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(window, window.decorView)

    // Search runs in its own activity; a selected hit comes back as sura/aya to jump to.
    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val sura = data.getIntExtra(ReaderSearchActivity.RESULT_SURA, 0)
        val aya  = data.getIntExtra(ReaderSearchActivity.RESULT_AYA, 0)
        if (sura > 0 && aya > 0) jumpToAya(sura, aya)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.book_reader_activity)

        ReaderTheme.init(this)
        ReaderPrefs.init(this)

        print = MushafPrefs.selected.value
        source = readerSourceFor(this, print)

        rootFrame = findViewById(R.id.reader_root)
        pager = findViewById(R.id.book_pager)
        wirdWall = findViewById(R.id.wird_wall)
        toolbar = findViewById(R.id.toolbar)
        toolbarArea = findViewById(R.id.toolbar_area)
        bottomBar = findViewById(R.id.bottom_bar)
        slider = findViewById(R.id.book_slider)
        popup = findViewById(R.id.page_popup)

        audioBar = ReaderAudioBar(
            root = findViewById(R.id.book_audio_bar),
            progress = findViewById(R.id.audio_progress),
            play = findViewById(R.id.audio_play),
            title = findViewById(R.id.audio_title),
            subtitle = findViewById(R.id.audio_subtitle),
        )

        setupToolbar()
        applyTopInset()
        applyBottomInset()
        setupBackgroundSync()
        setupAudioBar()

        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Prepare the content (text pages may build), then wire the pager/slider/meta once ready.
        lifecycleScope.launch { setupReader() }
    }

    /** Builds the source, resolves the window + start page, and wires the pager and slider. */
    private suspend fun setupReader() {
        val pageCount = source.prepare()

        // A Khatmah session restricts the reader to [firstPage]..[lastPage] — page-based sources only.
        val sessionStart = intent.getIntExtra(EXTRA_START_PAGE, 0)
        val sessionEnd = intent.getIntExtra(EXTRA_END_PAGE, 0)
        isSession = source.supportsSession &&
                sessionStart in 1..pageCount && sessionEnd in sessionStart..pageCount
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        firstPage = if (isSession) sessionStart else 1
        lastPage = if (isSession) sessionEnd else pageCount
        wirdActive = isSession && khatmahRepo.isSessionUnread(sessionId)
        if (wirdActive) nextWird = khatmahRepo.nextWirdAfter(sessionId)
        syncWallLabel()
        invalidateOptionsMenu() // the session resolves after the menu is first built

        ayaPageCache = source.ayaPageIndex()
        metaMap = source.pageMeta()
        // Hizb/rub' division toasts are a book-reader-only affordance (see [bookmarkable]); loading
        // the index only for QCF4 avoids an unnecessary query for the text reader.
        if (bookmarkable) hizbMap = HizbIndex.loadForRiwaya(this, source.riwaya.dbKey)

        val startPage = resolveStartPage().coerceIn(firstPage, lastPage)

        // Pager stays LTR; the right-to-left feel comes from the reversed mapping (page = lastPage - position).
        pager.adapter = ReaderPagerAdapter(this, lastPage - firstPage + 1, lastPage, source)
        // ViewPager2 keeps only the current page by default; the reader wants its neighbour ready
        // so a turn never waits on a fragment.
        pager.offscreenPageLimit = 1
        pager.setCurrentItem(positionForPage(startPage), false)
        setupSlider(startPage)
        setupWirdWall()
        savePage(startPage) // opening a page is progress even without a swipe

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (silentHop) return
                val page = pageForPosition(position)
                currentPageNum = page
                updateMeta(page)
                slider.progress = page - firstPage
                savePage(page)
                if (bookmarkable) syncBookmarkIcon()
                showHizbToastFor(page)
            }
        })
        updateMeta(startPage)
        // The initial setCurrentItem above doesn't fire onPageSelected, so seed the page + bookmark
        // state here — otherwise reopening on a bookmarked page shows the outline until the first swipe.
        currentPageNum = startPage
        if (bookmarkable) {
            syncBookmarkIcon()
            lifecycleScope.launch { bookmarkedPages.collect { syncBookmarkIcon() } }
        }
        if (popup.isVisible) showPopupFor(firstPage + slider.progress)
    }

    /** The page to open on: session resume, an explicit page (QCF4), a sura/aya target, or last read. */
    private fun resolveStartPage(): Int {
        if (isSession) return readSessionPage()
        val requestedPage = intent.getIntExtra(EXTRA_PAGE, 0)
        if (requestedPage > 0) return requestedPage
        val sura = intent.getIntExtra(EXTRA_SURA, 0)
        if (sura > 0) {
            val aya = intent.getIntExtra(EXTRA_AYA, 0).coerceAtLeast(1)
            ayaPageCache?.get(ayaKey(sura, aya))?.let { return it + 1 }
        }
        return readLastPage() + 1
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // platform back arrow, mirrored in RTL
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        applyToolbarChrome()
    }

    /** Solid surface-coloured bar so it reads as separate from the page behind it. */
    private fun applyToolbarChrome() {
        val bar = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorSurfaceContainer)
        val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        val onVariant = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurfaceVariant)
        toolbarArea.setBackgroundColor(bar)
        toolbar.setTitleTextColor(onSurface)
        toolbar.setSubtitleTextColor(onVariant)
        toolbar.navigationIcon?.setTint(onSurface)
    }

    /** Refreshes the toolbar title/subtitle for [page]. */
    private fun updateMeta(page: Int) {
        val meta = metaMap[page] ?: return
        supportActionBar?.title = meta.toolbarTitle
        supportActionBar?.subtitle = meta.toolbarSubtitle
        toolbar.fitTitleText() // subtitle view is (re)created per page → keep font padding off
    }

    // ── Toolbar menu: night reading, search, settings ───────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_reader_menu, menu)
        padMenuButton()
        return true
    }

    /** Insets the overflow button from the screen edge so it balances the back button. */
    private fun padMenuButton() {
        val pad = (MENU_EDGE_PAD_DP * resources.displayMetrics.density).toInt()
        toolbar.post {
            for (i in 0 until toolbar.childCount) {
                (toolbar.getChildAt(i) as? androidx.appcompat.widget.ActionMenuView)?.let { menuView ->
                    menuView.setPaddingRelative(
                        menuView.paddingStart, menuView.paddingTop, pad, menuView.paddingBottom,
                    )
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_night_mode)?.isChecked = ReaderTheme.effectiveNight(this)
        // Bookmarks are page-based — book reader only; hide the actions for the text reader.
        menuBookmark = menu.findItem(R.id.menu_bookmark)?.apply { isVisible = bookmarkable }
        menu.findItem(R.id.menu_bookmarks_list)?.isVisible = bookmarkable
        // Starting a khatmah is offered where a wird is being read, not in the open mushaf.
        menu.findItem(R.id.menu_new_khatmah)?.isVisible = isSession
        if (bookmarkable) syncBookmarkIcon()
        toolbar.overflowIcon?.setTint(
            MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_night_mode -> {
            // Reader-only night toggle — pages update live via the theme flow; no recreation.
            ReaderTheme.toggle(this)
            item.isChecked = ReaderTheme.effectiveNight(this)
            true
        }
        R.id.menu_new_khatmah -> { startActivity(Dest.NewKhatmah.toIntent(this)); true }
        R.id.menu_search -> { openSearch(); true }
        R.id.menu_settings -> {
            startActivity(Intent(this, ReaderSettingsActivity::class.java))
            true
        }
        R.id.menu_bookmark -> { toggleBookmark(); true }
        R.id.menu_bookmarks_list -> { openBookmarks(); true }
        // Placeholder mirroring Quran's toolbar — wired later; consume the click for now.
        R.id.menu_help -> true
        else -> super.onOptionsItemSelected(item)
    }

    // ── Bookmarks (book reader only) ─────────────────────────────────────────────

    private val bookmarkRepo by lazy { BookmarkRepository(applicationContext) }

    // The live bookmarked-page set the page ribbons also read — the icon can never drift from them.
    private val bookmarkedPages by lazy {
        BookmarkRepository.pages(applicationContext, source.riwaya.dbKey)
    }
    private val bookmarkable get() = source.mode == ReaderMode.QCF4
    private var menuBookmark: MenuItem? = null

    // The page currently shown — tracked so the bookmark state is correct even before the pager has
    // dispatched a page-selected event (i.e. right after the reader opens).
    private var currentPageNum = 1
    private fun currentPage(): Int = currentPageNum

    /**
     * Bookmark action on the current page: if already bookmarked, removes it; otherwise prompts for
     * a name (defaulting to the sura) and adds it. The icon reflects the resulting state.
     */
    private fun toggleBookmark() {
        val page = currentPage()
        if (page in bookmarkedPages.value) {
            lifecycleScope.launch { bookmarkRepo.remove(source.riwaya.dbKey, page) }
        } else {
            promptBookmarkName(page)
        }
    }

    /** Asks for a bookmark name (pre-filled with the sura title); an unchanged/blank name → default. */
    private fun promptBookmarkName(page: Int) {
        val default = metaMap[page]?.toolbarTitle ?: "صفحة $page"
        val pad = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            setText(default)
            setSelection(text.length)
            isSingleLine = true
        }
        val frame = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }
        MaterialAlertDialogBuilder(this)
            .setTitle("اسم العلامة")
            .setView(frame)
            .setPositiveButton("حفظ") { _, _ ->
                val entered = input.text.toString().trim()
                // Keep null when the user accepts the default, so the row stays synced to the sura.
                val label = entered.takeIf { it.isNotEmpty() && it != default }
                lifecycleScope.launch { bookmarkRepo.add(source.riwaya.dbKey, page, label) }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /** Opens the bookmarks sheet (same source/format as the bookmarks screen); a tap jumps to a page. */
    private fun openBookmarks() {
        lifecycleScope.launch {
            val riwaya = source.riwaya.dbKey
            val list = bookmarkRepo.bookmarks(riwaya).first()
            val meta = ReaderMeta.loadForRiwaya(this@ReaderActivity, riwaya)
            val rows = list.map { b ->
                val m = meta[b.pageNum]
                BookmarksSheet.Row(
                    page = b.pageNum,
                    title = b.label ?: m?.toolbarTitle ?: "صفحة ${b.pageNum}",
                    subtitle = m?.toolbarSubtitle ?: "",
                )
            }
            BookmarksSheet(this@ReaderActivity).show(rows) { page -> goToPage(page) }
        }
    }

    /**
     * Opens [page] with a swipe-like slide, clamped to the active window: the pager first hops
     * silently to the neighbour the jump arrives from, then animates that last step. Animating the
     * raw distance instead would blur through hundreds of never-rendered pages, so this keeps every
     * jump — one page away or three hundred — settling exactly like a finger swipe.
     */
    private fun goToPage(page: Int) {
        val target = page.coerceIn(firstPage, lastPage)
        if (target == currentPage()) return

        // Approach a later page from behind and an earlier one from ahead, so the slide runs in the
        // same direction the reader would have swiped to get there.
        val from = if (target > currentPage()) target - 1 else target + 1
        if (from !in firstPage..lastPage) {
            pager.setCurrentItem(positionForPage(target), true)
            return
        }
        // The hop is a staging move, not a visited page — silence the page-selected side effects
        // (progress save, hizb toast) for it. setCurrentItem dispatches synchronously, so the flag
        // covers exactly this call.
        silentHop = true
        pager.setCurrentItem(positionForPage(from), false)
        silentHop = false
        // One frame later the staged page is laid out, so the slide animates over real content.
        pager.post { pager.setCurrentItem(positionForPage(target), true) }
    }

    /** True only while the pager is being staged for a [goToPage] slide. */
    private var silentHop = false

    /** Points the toolbar icon at the current page's state in [bookmarkedPages]. */
    private fun syncBookmarkIcon() {
        val bookmarked = currentPage() in bookmarkedPages.value
        menuBookmark?.setIcon(if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border)
    }

    // ── Hizb / rub'-al-hizb swipe toast (book reader only) ───────────────────────

    // page → division event for the active riwaya; empty (and inert) for the text reader.
    private var hizbMap: Map<Int, HizbEvent> = emptyMap()

    // The one toast this feature ever shows — cancelled before each re-show so fast swiping
    // through several markers never leaves a queue of stale toasts trailing behind the finger.
    private var hizbToast: Toast? = null

    /** Shows the juz'/hizb/rub' toast for [page], if it starts a division marker. No-op otherwise. */
    private fun showHizbToastFor(page: Int) {
        val event = hizbMap[page] ?: return
        hizbToast?.cancel()
        hizbToast = Toast.makeText(this, event.toToastText(this), Toast.LENGTH_SHORT).also { it.show() }
    }

    /**
     * Toggles the toolbar and system bars together — the immersive "book" tap. Mirrors Quran
     * Android's toggleActionBar: showing just reveals the chrome; hiding also cancels any pending
     * auto-hide so it can't fire again mid-animation.
     */
    fun toggleChrome() {
        if (!chromeVisible) {
            setChrome(true)
        } else {
            barHideHandler.removeMessages(MSG_HIDE_BARS)
            setChrome(false)
        }
    }

    /**
     * Takes back the toggle a page tap just made, because that tap opened a double-tap zoom. The
     * toggle is a pure flip, so replaying it restores the previous state — and since the bars are
     * still mid-slide, the animation simply reverses instead of blinking.
     */
    fun undoChromeToggle() = toggleChrome()

    /** Drives the chrome to [visible] only if it differs from the current state (Quran's toggleActionBarVisibility). */
    private fun toggleChromeVisibility(visible: Boolean) {
        if (visible == !chromeVisible) toggleChrome()
    }

    // Arm the one-shot auto-hide each time the window gains focus — exactly Quran's onWindowFocusChanged.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) barHideHandler.sendEmptyMessageDelayed(MSG_HIDE_BARS, AUTO_HIDE_AFTER_MS)
        else barHideHandler.removeMessages(MSG_HIDE_BARS)
    }

    override fun onResume() {
        super.onResume()
        // A khatmah started from this toolbar replaces the one the open wird belongs to; reading on
        // would finish sessions of a schedule the app has already moved off.
        if (isSession) lifecycleScope.launch {
            if (!khatmahRepo.isSessionCurrent(sessionId)) finish()
        }
    }

    override fun onDestroy() {
        barHideHandler.removeCallbacksAndMessages(null)
        hizbToast?.cancel()
        super.onDestroy()
    }

    /** Posts [MSG_HIDE_BARS] after a delay to hide the chrome; weak ref avoids leaking the activity. */
    private class BarHideHandler(activity: ReaderActivity) : Handler(Looper.getMainLooper()) {
        private val activityRef = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            if (msg.what == MSG_HIDE_BARS) activityRef.get()?.toggleChromeVisibility(false)
            else super.handleMessage(msg)
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    /** Launches the search activity, scoped to the open session's pages when in session mode. */
    private fun openSearch() {
        val intent = Intent(this, ReaderSearchActivity::class.java)
        if (isSession) {
            intent.putExtra(ReaderSearchActivity.EXTRA_FIRST_PAGE, firstPage)
            intent.putExtra(ReaderSearchActivity.EXTRA_LAST_PAGE, lastPage)
        }
        searchLauncher.launch(intent)
    }

    /** Jumps the pager to the page holding [sura]:[aya], clamped to the active window. */
    fun jumpToAya(sura: Int, aya: Int) {
        val page = ayaPageCache?.get(ayaKey(sura, aya))?.plus(1) ?: return
        pager.setCurrentItem(positionForPage(page.coerceIn(firstPage, lastPage)), false)
    }

    private fun ayaKey(sura: Int, aya: Int): Long = (sura.toLong() shl 32) or aya.toLong()

    /** Slides the toolbar/bottom bar in/out via translationY; system bars toggle in sync. */
    private fun setChrome(visible: Boolean) {
        chromeVisible = visible
        if (visible) insetsController.show(WindowInsetsCompat.Type.systemBars())
        else insetsController.hide(WindowInsetsCompat.Type.systemBars())
        toolbarArea.animate()
            .translationY(if (visible) 0f else -toolbarArea.height.toFloat())
            .setDuration(TOOLBAR_ANIM_MS)
            .start()
        bottomBar.animate()
            .translationY(if (visible) 0f else bottomBar.height.toFloat())
            .setDuration(TOOLBAR_ANIM_MS)
            .start()
    }

    // ── Bottom page-jump slider ─────────────────────────────────────────────────

    /** Scrubbing only shows the [popup] (page never moves); the page jumps once on release. */
    private fun setupSlider(startPage: Int) {
        slider.max = lastPage - firstPage
        slider.progress = startPage - firstPage
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) showPopupFor(firstPage + progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = showPopupFor(firstPage + sb.progress)
            override fun onStopTrackingTouch(sb: SeekBar) {
                hidePopup()
                pager.setCurrentItem(positionForPage(firstPage + sb.progress), true)
            }
        })
    }

    /** Shows the scrub popup for [page]: text is set instantly since meta is preloaded. */
    private fun showPopupFor(page: Int) {
        if (!popup.isVisible) {
            (popup.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                bottomBar.height + (POPUP_GAP_DP * resources.displayMetrics.density).toInt()
            popup.visibility = View.VISIBLE
        }
        val meta = metaMap[page]
        popup.text = if (meta != null) "${meta.sliderPage}\n${meta.sliderSuraJuz}"
        else ReaderMeta.pageLabel(page)
    }

    private fun hidePopup() { popup.visibility = View.GONE }

    /** Reversed page↔position mapping (page = lastPage - position). */
    private fun pageForPosition(position: Int): Int = lastPage - position
    private fun positionForPage(page: Int): Int = lastPage - page

    /** Pushes the toolbar below the status bar (edge-to-edge) using stable insets. */
    private fun applyTopInset() {
        ViewCompat.setOnApplyWindowInsetsListener(toolbarArea) { v, insets ->
            val top = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).top
            v.updatePadding(top = top)
            insets
        }
    }

    /** Lifts the bottom bar above the nav bar using stable insets, keeping its design padding. */
    private fun applyBottomInset() {
        val base = (BOTTOM_PAD_DP * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val bottom = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = base + bottom)
            insets
        }
    }

    // ── Recitation ───────────────────────────────────────────────────────────────

    /** Wires the player controls and mirrors playback state into the bar. */
    private fun setupAudioBar() {
        findViewById<ImageButton>(R.id.audio_play).setOnClickListener { audioController.togglePlayPause() }
        findViewById<ImageButton>(R.id.audio_close).setOnClickListener { audioController.stop() }
        lifecycleScope.launch {
            audioController.state.collect {
                audioBar.render(it)
                followRecitation(it)
            }
        }
    }

    /**
     * Keeps the page in step with the recitation: when playback moves to a verse on a different
     * page, slides the pager to it. Acts only on a verse *change*, not every progress tick.
     */
    private fun followRecitation(st: AyaAudioState) {
        if (!st.active || st.ayaNum <= 0) { followedKey = 0L; return }
        val key = ayaKey(st.suraNum, st.ayaNum)
        if (key == followedKey) return
        followedKey = key
        val page = ayaPageCache?.get(key)?.plus(1) ?: return
        // A wird ends where its pages end: recitation that runs past the window stops there rather
        // than reading on into the next session behind a pager that cannot follow it.
        if (isSession && page > lastPage) {
            audioController.stop()
            return
        }
        val pos = positionForPage(page.coerceIn(firstPage, lastPage))
        if (pos != pager.currentItem) pager.setCurrentItem(pos, true)
    }

    /**
     * Long-press on a verse: resolve the active riwaya's reader and stream that surah, seeking to
     * the pressed aya. No reader for the riwaya → nothing happens.
     */
    fun onAyaLongPress(sura: Int, aya: Int) {
        val reader = readers.firstOrNull() ?: return
        audioController.play(source.riwaya.dbKey, reader.id, reader.name, sura, aya)
        // Reveal the chrome so the player bar slides in fully positioned.
        if (!chromeVisible) setChrome(true)
    }

    // ── Last-read page (keyed per print, since text and QCF4 paginate differently) ──

    private val readerPrefs get() = getSharedPreferences(ReaderProgress.PREFS, Context.MODE_PRIVATE)

    private val lastPageKey get() = "$KEY_LAST_PAGE_PREFIX${print.id}"

    private fun readLastPage(): Int = readerPrefs.getInt(lastPageKey, 0)

    private fun saveLastPage(index: Int) = readerPrefs.edit { putInt(lastPageKey, index) }

    /** Single entry point for persisting progress: the per-session store, else the per-print page. */
    private fun savePage(page: Int) {
        if (isSession) {
            saveSessionPage(page)
        } else {
            saveLastPage(page - 1)
            saveResumeAnchor(page)
        }
    }

    /** Mirrors the page into [ReaderProgress] with its first verse, so the Quran tab can resume. */
    private fun saveResumeAnchor(page: Int) {
        val key = firstAyaByPage[page] ?: return
        ReaderProgress.save(
            this,
            print.id,
            ReaderProgress.Anchor(page, (key ushr 32).toInt(), (key and 0xFFFFFFFFL).toInt()),
        )
    }

    /** page (1-based) → its first aya, packed as in [ayaKey]. Inverted once from [ayaPageCache]. */
    private val firstAyaByPage: Map<Int, Long> by lazy {
        val out = HashMap<Int, Long>()
        ayaPageCache?.forEach { (key, index) ->
            val page = index + 1
            val first = out[page]
            if (first == null || key < first) out[page] = key
        }
        out
    }

    // ── Khatmah wird (page-windowed sessions) ───────────────────────────────────

    private val khatmahRepo by lazy { KhatmahRepository(applicationContext) }

    /**
     * Hangs the wall off the pager's leading edge. Nothing here touches the pager's own gestures:
     * the wall is only ever handed the drag the pages could not use, so page turns, taps, zoom and
     * long-presses carry on untouched.
     */
    private fun setupWirdWall() {
        wall = WirdWall(pager, wirdWall)
        wall.onCommit = { completeWird() }
        pagerPages.apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            edgeEffectFactory = WirdWallEdgeEffectFactory(wall)
            addOnItemTouchListener(wall)
        }
        wall.enabled = wirdActive
    }

    /** What the pull promises: the next wird, or the end of the khatmah when none follows. */
    private fun syncWallLabel() {
        wirdWall.endsKhatmah = nextWird == null
    }

    /** ViewPager2's single child is the RecyclerView holding the pages — and its edge effects. */
    private val pagerPages get() = pager.getChildAt(0) as RecyclerView

    /**
     * Completes the open wird. With another wird waiting, the reader hands over to it straight
     * away; with none, the khatmah is finished, so the wall retires. Marking read happens in the
     * background either way — the Quran tab's strip follows through its Room flow, and the gesture
     * never waits on a write.
     *
     * @return true only when the hand-off has taken charge of the page. False means the caller
     *   still owns it and must settle it — including the declined case, which is what used to
     *   leave the page parked aside until the reader was reopened.
     */
    private fun completeWird(): Boolean {
        if (wirdCompleting) return false
        val next = nextWird
        val finished = sessionId
        wirdCompleting = true

        if (next == null) {
            // The khatmah is finished: say so and leave, since there is no next wird to read. The
            // page stays where the pull left it — the reader is on its way out, not settling back.
            Toast.makeText(this, R.string.wird_khatmah_done, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                khatmahRepo.markSessionRead(finished)  // awaited: finishing would cancel the write
                finish()
            }
            return true
        }

        handOverTo(next)
        lifecycleScope.launch {
            khatmahRepo.markSessionRead(finished)
            nextWird = khatmahRepo.nextWirdAfter(next.id)
            syncWallLabel()
            // The wall reopens only once the following wird is known, so a second pull can neither
            // skip a wird nor land mid-hand-off.
            wall.enabled = wirdActive
            wirdCompleting = false
        }
        return true
    }

    /**
     * Carries the release straight into [session]: the screen as the finger left it is frozen into
     * an overlay that keeps travelling out, while the pager — rebound to the new window off-screen —
     * follows it in from the left. Two views moving as one, so the half-pulled end page finishes its
     * slide and the next wird arrives in the same motion, with no adapter swap ever visible.
     */
    private fun handOverTo(session: KhatmahSessionEntity) {
        val width    = pager.width.toFloat()
        // The pages are already held aside, so the snapshot carries that offset with it: the
        // outgoing half starts where it stands and both halves travel the same remaining distance.
        val pulled   = wall.pullPx
        val outgoing = freezePager()

        // The pager itself moves for this one animation, so its own gestures are held off until it
        // is back at rest — a drag over a moving transform is what the wall is careful never to do.
        wall.enabled = false
        pager.isUserInputEnabled = false

        bindSession(session)

        val ease = DecelerateInterpolator(SLIDE_TENSION)
        outgoing?.animate()
            ?.translationX(width - pulled)
            ?.setDuration(WIRD_SLIDE_MS)
            ?.setInterpolator(ease)
            ?.withEndAction { rootFrame.removeView(outgoing) }
            ?.start()

        // Starts where the outgoing page's edge is, so both halves travel the same distance and
        // move as one seam — no gap opens between them, whatever the pull was released at.
        pager.translationX = pulled - width
        pager.animate()
            .translationX(0f)
            .setDuration(WIRD_SLIDE_MS)
            .setInterpolator(ease)
            .withEndAction {
                pager.isUserInputEnabled = true
                showWirdToast(session)
            }
            .start()
    }

    /** Snapshots the pager into an overlay above it, so the rebind underneath is never seen. */
    private fun freezePager(): ImageView? {
        if (pager.width <= 0 || pager.height <= 0) return null
        val shot = createBitmap(pager.width, pager.height)
        pager.draw(Canvas(shot))
        return ImageView(this).also {
            it.setImageBitmap(shot)
            // Directly above the pager — the chrome is elevated, so it still draws on top.
            rootFrame.addView(it, rootFrame.indexOfChild(pager) + 1, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    /** Names the wird that just arrived, then gets out of the way. */
    private fun showWirdToast(session: KhatmahSessionEntity) {
        Toast.makeText(
            this,
            getString(R.string.wird_started, session.dayNumber, session.startPage, session.endPage),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Points the pager at [session]'s window: new adapter, slider range, and its remembered page. */
    private fun bindSession(session: KhatmahSessionEntity) {
        sessionId = session.id
        firstPage = session.startPage
        lastPage = session.endPage
        val startPage = readSessionPage().coerceIn(firstPage, lastPage)

        // The adapter swap re-selects position 0 (the new end page) — stage the whole rebind
        // silently, then land on the real page ourselves.
        silentHop = true
        pager.adapter = ReaderPagerAdapter(this, lastPage - firstPage + 1, lastPage, source)
        pager.setCurrentItem(positionForPage(startPage), false)
        silentHop = false

        setupSlider(startPage)
        currentPageNum = startPage
        savePage(startPage)
        updateMeta(startPage)
        if (bookmarkable) syncBookmarkIcon()
    }

    // ── Per-session last-read page (its own key, defaults to the session's first page) ──

    private fun sessionPageKey() = "$KEY_SESSION_PAGE_PREFIX$sessionId"
    private fun readSessionPage(): Int = readerPrefs.getInt(sessionPageKey(), firstPage)
    private fun saveSessionPage(page: Int) = readerPrefs.edit { putInt(sessionPageKey(), page) }

    // ── Background sync ─────────────────────────────────────────────────────────

    /**
     * Keeps the pager background synced with the pages' own canvas background, so the app's base
     * theme never bleeds through during fast horizontal swipes (and the transparent text pages show
     * the right parchment/night surface).
     */
    private fun setupBackgroundSync() {
        // On the root rather than the pager: the wall lives between the two, and a background on
        // the pager would cover it. This one still backs the gaps between pages, the transparent
        // text pages, and the space the pager vacates during a wird hand-off.
        val bgDrawable = PagerBackgroundDrawable()
        rootFrame.background = bgDrawable
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                ReaderTheme.override,
                ReaderPrefs.backgroundBrightness
            ) { _, bg -> bg }.collect { bg ->
                bgDrawable.nightMode = ReaderTheme.effectiveNight(this@ReaderActivity)
                bgDrawable.bgBrightness = bg
                bgDrawable.invalidateSelf()
            }
        }
    }

    private inner class PagerBackgroundDrawable : android.graphics.drawable.Drawable() {
        var nightMode: Boolean = false
        var bgBrightness: Int = 0

        private val solidPaint = android.graphics.Paint()
        private val gradientPaint = android.graphics.Paint()
        private var lastWidth: Float = 0f

        override fun draw(canvas: android.graphics.Canvas) {
            val w = bounds.width().toFloat()
            val h = bounds.height().toFloat()
            if (nightMode) {
                solidPaint.color = android.graphics.Color.rgb(bgBrightness, bgBrightness, bgBrightness)
                canvas.drawRect(0f, 0f, w, h, solidPaint)
            } else {
                if (w > 0 && w != lastWidth) {
                    lastWidth = w
                    gradientPaint.shader = android.graphics.LinearGradient(
                        0f, 0f, w, 0f,
                        intArrayOf(BookPageView.PARCHMENT_EDGE, BookPageView.PARCHMENT_CENTER, BookPageView.PARCHMENT_EDGE),
                        floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.REPEAT
                    )
                }
                canvas.drawRect(0f, 0f, w, h, gradientPaint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
    }

    companion object {
        const val EXTRA_PAGE = "book_page"           // QCF4 explicit page, 1-based
        const val EXTRA_SURA = "reader_sura"         // text target sura (1-based)
        const val EXTRA_AYA = "reader_aya"           // text target aya (1-based)
        const val EXTRA_START_PAGE = "book_start_page" // session window, 1-based, inclusive
        const val EXTRA_END_PAGE = "book_end_page"
        const val EXTRA_SESSION_ID = "book_session_id"

        private const val KEY_LAST_PAGE_PREFIX = "last_page_"     // + print id
        private const val WIRD_SLIDE_MS = 320L    // next wird sliding in after a completion
        private const val SLIDE_TENSION = 1.6f    // eases the slide out like a settling swipe
        private const val KEY_SESSION_PAGE_PREFIX = "session_page_"
        private const val MENU_EDGE_PAD_DP = 8f
        private const val TOOLBAR_ANIM_MS = 250L
        // Quran Android's DEFAULT_HIDE_AFTER_TIME / MSG_HIDE_ACTIONBAR.
        private const val AUTO_HIDE_AFTER_MS = 2000L
        private const val MSG_HIDE_BARS = 1
        private const val BOTTOM_PAD_DP = 24f
        private const val POPUP_GAP_DP = 12f
    }
}
