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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.audio.AyaAudioState
import com.lhacenmed.khatmah.feature.audio.GhReader
import com.lhacenmed.khatmah.feature.audio.GithubAudioRepository
import com.lhacenmed.khatmah.feature.quran.data.BookmarkRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.ui.reader.book.BookPageView
import com.lhacenmed.khatmah.feature.quran.ui.reciter.ReaderAudioBar
import com.lhacenmed.khatmah.feature.quran.ui.reciter.ReaderAudioViewModel
import com.lhacenmed.khatmah.feature.quran.ui.search.ReaderSearchActivity
import com.lhacenmed.khatmah.feature.quran.ui.settings.ReaderSettingsActivity
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Full-screen reader shell — mode-agnostic host for the QCF4 book pages and the native text pages.
 *
 * Hosts a [ViewPager] of page fragments (built by the active [ReaderSource]) with an overlaid
 * surface-coloured toolbar that toggles with the immersive system bars on a page tap. The active
 * print decides the [ReaderSource]; everything mode-specific (page count, aya→page map, per-page
 * meta, page fragment) is routed through it, so this shell never branches on mode.
 *
 * Page-windowed Khatmah sessions are only honoured when the source [ReaderSource.supportsSession]
 * (the page-based QCF4 mushaf); the text reader always opens the full mushaf.
 */
class ReaderActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager
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

        pager = findViewById(R.id.book_pager)
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

        ayaPageCache = source.ayaPageIndex()
        metaMap = source.pageMeta()

        val startPage = resolveStartPage().coerceIn(firstPage, lastPage)

        // Pager stays LTR; the right-to-left feel comes from the reversed mapping (page = lastPage - position).
        pager.adapter = ReaderPagerAdapter(supportFragmentManager, lastPage - firstPage + 1, lastPage, source)
        pager.setCurrentItem(positionForPage(startPage), false)
        setupSlider(startPage)
        savePage(startPage) // opening a page is progress even without a swipe

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val page = pageForPosition(position)
                currentPageNum = page
                updateMeta(page)
                slider.progress = page - firstPage
                savePage(page)
                if (bookmarkable) refreshBookmarkIcon(page)
            }
        })
        updateMeta(startPage)
        // The initial setCurrentItem above doesn't fire onPageSelected, so seed the page + bookmark
        // state here — otherwise reopening on a bookmarked page shows the outline until the first swipe.
        currentPageNum = startPage
        if (bookmarkable) refreshBookmarkIcon(startPage)
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
        // Bookmarks are page-based — book reader only; hide the action for the text reader.
        menuBookmark = menu.findItem(R.id.menu_bookmark)?.apply { isVisible = bookmarkable }
        if (bookmarkable) refreshBookmarkIcon(currentPage())
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
        R.id.menu_search -> { openSearch(); true }
        R.id.menu_settings -> {
            startActivity(Intent(this, ReaderSettingsActivity::class.java))
            true
        }
        R.id.menu_bookmark -> { toggleBookmark(); true }
        // Placeholder mirroring Quran's toolbar — wired later; consume the click for now.
        R.id.menu_help -> true
        else -> super.onOptionsItemSelected(item)
    }

    // ── Bookmarks (book reader only) ─────────────────────────────────────────────

    private val bookmarkRepo by lazy { BookmarkRepository(applicationContext) }
    private val bookmarkable get() = source.mode == ReaderMode.QCF4
    private var menuBookmark: MenuItem? = null

    // The page currently shown — tracked so the bookmark state is correct even before the pager has
    // dispatched a page-selected event (i.e. right after the reader opens).
    private var currentPageNum = 1
    private fun currentPage(): Int = currentPageNum

    /** Toggles the bookmark on the current page for the active riwaya and reflects it in the icon. */
    private fun toggleBookmark() {
        val page = currentPage()
        lifecycleScope.launch { setBookmarkIcon(bookmarkRepo.toggle(source.riwaya.dbKey, page)) }
    }

    private fun refreshBookmarkIcon(page: Int) {
        lifecycleScope.launch { setBookmarkIcon(bookmarkRepo.isBookmarked(source.riwaya.dbKey, page)) }
    }

    private fun setBookmarkIcon(bookmarked: Boolean) {
        menuBookmark?.setIcon(if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border)
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

    override fun onDestroy() {
        barHideHandler.removeCallbacksAndMessages(null)
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

    private val readerPrefs get() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val lastPageKey get() = "$KEY_LAST_PAGE_PREFIX${print.id}"

    private fun readLastPage(): Int = readerPrefs.getInt(lastPageKey, 0)

    private fun saveLastPage(index: Int) = readerPrefs.edit { putInt(lastPageKey, index) }

    /** Single entry point for persisting progress: the per-session store, else the per-print page. */
    private fun savePage(page: Int) {
        if (isSession) saveSessionPage(page) else saveLastPage(page - 1)
    }

    // ── Per-session last-read page (its own key, defaults to the session's first page) ──

    private fun sessionPageKey() = "$KEY_SESSION_PAGE_PREFIX$sessionId"
    private fun readSessionPage(): Int = readerPrefs.getInt(sessionPageKey(), firstPage)
    private fun saveSessionPage(page: Int) = readerPrefs.edit { putInt(sessionPageKey(), page) }

    // ── Background sync ─────────────────────────────────────────────────────────

    /**
     * Keeps the ViewPager background synced with the pages' own canvas background, so the app's base
     * theme never bleeds through during fast horizontal swipes (and the transparent text pages show
     * the right parchment/night surface).
     */
    private fun setupBackgroundSync() {
        val bgDrawable = PagerBackgroundDrawable()
        pager.background = bgDrawable
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

        private const val PREFS = "quran_reader"
        private const val KEY_LAST_PAGE_PREFIX = "last_page_"     // + print id
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
