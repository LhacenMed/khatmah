package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.audio.AudioLoadState
import com.lhacenmed.khatmah.feature.audio.AyaAudioState
import com.lhacenmed.khatmah.feature.audio.BookAudioController
import com.lhacenmed.khatmah.feature.audio.GhReader
import com.lhacenmed.khatmah.feature.audio.GithubAudioRepository
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.Qcf4PageSource
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import com.lhacenmed.khatmah.feature.quran.ui.QuranViewModel
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen book reader — the QCF4 View port of Quran Android's `PagerActivity`.
 *
 * Hosts a [ViewPager] of [BookPageFragment]s with an overlaid surface-coloured toolbar
 * that toggles together with the immersive system bars on a page tap. The toolbar mirrors
 * Quran Android: title = sura name, subtitle = "page N, juz M". The active riwaya (and thus
 * page count) comes from [MushafPrefs]; the start page is passed as [EXTRA_PAGE] (1-based,
 * 0 = resume the shared last-read page).
 */
class BookReaderActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarArea: FrameLayout
    private lateinit var bottomBar: FrameLayout
    private lateinit var slider: SeekBar
    private lateinit var popup: TextView

    // Audio player bar (above the slider) — hidden until a verse is long-pressed.
    private lateinit var audioBar: View
    private lateinit var audioProgress: ProgressBar
    private lateinit var audioPlay: ImageButton
    private lateinit var audioTitle: TextView
    private lateinit var audioSubtitle: TextView

    private val audioController by lazy { BookAudioController(applicationContext) }
    /** Readers available for the active riwaya; first one is used (single reader for now). */
    private val readers: List<GhReader> by lazy {
        GithubAudioRepository(applicationContext).readersFor(repo.riwaya.dbKey)
    }

    /** Exposes the playback state so each [BookPageFragment] can highlight the playing verse. */
    val audioState get() = audioController.state

    private var chromeVisible = true
    private var metaJob: Job? = null
    private var metaMap: Map<Int, BookMeta> = emptyMap()

    // Reading window: the full mushaf by default, or a single Khatmah session's
    // [firstPage]..[lastPage] when opened from the session card. Every page/position/slider
    // mapping is expressed against this window, so full mode is just firstPage = 1.
    private var firstPage = 1
    private var lastPage = 0

    // Session reading: when [isSession], progress is remembered per [sessionId] (its own last-read
    // page), independent of the full-mushaf last-read page — same idea, separate store.
    private var isSession = false
    private var sessionId = 0L

    private val insetsController: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(window, window.decorView)

    private val repo: Qcf4PageSource by lazy {
        if (MushafPrefs.selected.value.riwaya == Riwaya.WARSH) WarshQcf4Repository.get(applicationContext)
        else HafsQcf4Repository.get(applicationContext)
    }

    private val pageCount: Int by lazy {
        if (repo.riwaya == Riwaya.WARSH) WarshQcf4Repository.PAGE_COUNT else HafsQcf4Repository.PAGE_COUNT
    }

    // Cached aya→page map (same pagination the reader renders) for jumping to a search hit.
    private var ayaPageCache: Map<Long, Int>? = null

    // Search runs in its own [BookSearchActivity]; a selected hit comes back as sura/aya to jump to.
    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val sura = data.getIntExtra(BookSearchActivity.RESULT_SURA, 0)
        val aya  = data.getIntExtra(BookSearchActivity.RESULT_AYA, 0)
        if (sura > 0 && aya > 0) jumpToAya(sura, aya)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.book_reader_activity)

        BookReaderTheme.init(this)
        BookReaderPrefs.init(this)

        pager = findViewById(R.id.book_pager)
        toolbar = findViewById(R.id.toolbar)
        toolbarArea = findViewById(R.id.toolbar_area)
        bottomBar = findViewById(R.id.bottom_bar)
        slider = findViewById(R.id.book_slider)
        popup = findViewById(R.id.page_popup)

        audioBar = findViewById(R.id.book_audio_bar)
        audioProgress = findViewById(R.id.audio_progress)
        audioPlay = findViewById(R.id.audio_play)
        audioTitle = findViewById(R.id.audio_title)
        audioSubtitle = findViewById(R.id.audio_subtitle)

        setupToolbar()
        applyTopInset()
        applyBottomInset()

        // A Khatmah session restricts the reader to [firstPage]..[lastPage]; full mushaf otherwise.
        val sessionStart = intent.getIntExtra(EXTRA_START_PAGE, 0)
        val sessionEnd = intent.getIntExtra(EXTRA_END_PAGE, 0)
        isSession = sessionStart in 1..pageCount && sessionEnd in sessionStart..pageCount
        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        firstPage = if (isSession) sessionStart else 1
        lastPage = if (isSession) sessionEnd else pageCount

        // Pager stays LTR; the right-to-left mushaf feel comes from the reversed
        // position↔page mapping (page = lastPage - position), exactly like Quran Android.
        pager.adapter = BookPagerAdapter(supportFragmentManager, lastPage - firstPage + 1, lastPage)

        val requested = intent.getIntExtra(EXTRA_PAGE, 0)
        val startPage = when {
            isSession     -> readSessionPage()  // resume this session's own last-read page
            requested > 0 -> requested
            else          -> readLastPage() + 1 // resume the shared last-read page
        }.coerceIn(firstPage, lastPage)
        pager.setCurrentItem(positionForPage(startPage), false)
        setupSlider(startPage)
        savePage(startPage) // persist immediately: opening a page is progress even without a swipe

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                val page = pageForPosition(position)
                updateMeta(page)
                slider.progress = page - firstPage // keep the slider in sync (fromUser = false → ignored)
                savePage(page)
            }
        })

        loadMetaThen(startPage)
        setupBackgroundSync()
        setupAudioBar()

        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    /** Loads page metadata once, then resolves the meta for the opening [startPage]. */
    private fun loadMetaThen(startPage: Int) {
        lifecycleScope.launch {
            metaMap = withContext(Dispatchers.IO) {
                runCatching { BookPageMeta.loadMetaForRiwaya(this@BookReaderActivity, repo.riwaya.dbKey) }
                    .getOrDefault(emptyMap())
            }
            updateMeta(startPage)
            // If the user scrubbed before metadata loaded, update the popup if it's visible.
            if (popup.isVisible) {
                showPopupFor(firstPage + slider.progress)
            }
        }
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

    /**
     * Insets the overflow button from the screen edge so it reads as balanced against the
     * navigation (back) button on the opposite side, which already sits inset by the toolbar's
     * content inset. The [androidx.appcompat.widget.ActionMenuView] is created during inflation,
     * so the padding is applied on the next layout pass.
     */
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
        // Night reading reflects the reader's own night state (default: the app theme).
        menu.findItem(R.id.menu_night_mode)?.isChecked = BookReaderTheme.effectiveNight(this)
        toolbar.overflowIcon?.setTint(
            MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_night_mode -> {
            // Reader-only night toggle — pages update live via the theme flow; the toolbar and
            // settings stay on the app theme. No activity recreation.
            BookReaderTheme.toggle(this)
            item.isChecked = BookReaderTheme.effectiveNight(this)
            true
        }
        R.id.menu_search -> { openSearch(); true }
        R.id.menu_settings -> {
            startActivity(Intent(this, BookReaderSettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Toggles the toolbar and system bars together — the immersive "book" tap. */
    fun toggleChrome() = setChrome(!chromeVisible)

    // ── Search ───────────────────────────────────────────────────────────────────

    /** Launches the search activity, scoped to the open session's pages when in session mode. */
    private fun openSearch() {
        val intent = Intent(this, BookSearchActivity::class.java)
        if (isSession) {
            intent.putExtra(BookSearchActivity.EXTRA_FIRST_PAGE, firstPage)
            intent.putExtra(BookSearchActivity.EXTRA_LAST_PAGE, lastPage)
        }
        searchLauncher.launch(intent)
    }

    /** Jumps the pager to the page holding [sura]:[aya], clamped to the active window. */
    fun jumpToAya(sura: Int, aya: Int) {
        lifecycleScope.launch {
            val page = ayaPageMap()[ayaKey(sura, aya)]?.plus(1) ?: return@launch
            pager.setCurrentItem(positionForPage(page.coerceIn(firstPage, lastPage)), false)
        }
    }

    /** Cached aya→page index (0-based) for the active mushaf — the reader's own pagination. */
    private suspend fun ayaPageMap(): Map<Long, Int> =
        ayaPageCache ?: repo.ayaPageIndex().also { ayaPageCache = it }

    private fun ayaKey(sura: Int, aya: Int): Long = (sura.toLong() shl 32) or aya.toLong()

    /**
     * Slides the toolbar in/out via translationY over [TOOLBAR_ANIM_MS] — the exact
     * `animateToolBar` behaviour from Quran Android. The bar stays laid out (never GONE) so
     * it animates smoothly; system bars toggle in sync.
     */
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

    /**
     * Wires the RTL page slider: scrubbing only shows the [popup] (page never moves), and the
     * page jumps once on release — exactly the requested behaviour.
     */
    private fun setupSlider(startPage: Int) {
        // Slider progress is a 0-based offset within the window: page = firstPage + progress.
        slider.max = lastPage - firstPage
        slider.progress = startPage - firstPage
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) showPopupFor(firstPage + progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = showPopupFor(firstPage + sb.progress)
            override fun onStopTrackingTouch(sb: SeekBar) {
                hidePopup()
                // Smooth-scroll to the chosen page so the jump is animated, not abrupt.
                pager.setCurrentItem(positionForPage(firstPage + sb.progress), true)
            }
        })
    }

    /** Shows the scrub popup for [page]: text is set instantly since meta is preloaded. */
    private fun showPopupFor(page: Int) {
        if (!popup.isVisible) {
            // Anchor just above the bottom bar (its height already includes the nav-bar inset).
            (popup.layoutParams as FrameLayout.LayoutParams).bottomMargin =
                bottomBar.height + (POPUP_GAP_DP * resources.displayMetrics.density).toInt()
            popup.visibility = View.VISIBLE
        }
        val meta = metaMap[page]
        if (meta != null) {
            popup.text = "${meta.sliderPage}\n${meta.sliderSuraJuz}"
        } else {
            popup.text = BookPageMeta.pageLabel(page)
        }
    }

    private fun hidePopup() {
        popup.visibility = View.GONE
    }

    /** Reversed page↔position mapping (page = lastPage - position), matching Quran Android. */
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

    // ── Audio player ─────────────────────────────────────────────────────────────

    /** Wires the player controls and mirrors playback state into the bar. */
    private fun setupAudioBar() {
        audioPlay.setOnClickListener { audioController.togglePlayPause() }
        findViewById<ImageButton>(R.id.audio_close).setOnClickListener { audioController.stop() }
        lifecycleScope.launch {
            audioController.state.collect { renderAudioBar(it) }
        }
    }

    /**
     * Long-press on a verse: resolve the active riwaya's reader and stream that surah, seeking
     * to the pressed aya. No reader for the riwaya → nothing happens (e.g. Hafs has none yet).
     */
    fun onAyaLongPress(sura: Int, aya: Int) {
        val reader = readers.firstOrNull() ?: return
        audioController.play(repo.riwaya.dbKey, reader.id, reader.name, sura, aya)
    }

    /** Reflects [st] into the player bar — visibility, progress, controls and labels. */
    private fun renderAudioBar(st: AyaAudioState) {
        if (!st.active) { audioBar.visibility = View.GONE; return }
        audioBar.visibility = View.VISIBLE

        when (val ls = st.loadState) {
            AudioLoadState.Connecting, AudioLoadState.Idle -> audioProgress.isIndeterminate = true
            is AudioLoadState.Downloading ->
                if (ls.progress < 0f) audioProgress.isIndeterminate = true
                else { audioProgress.isIndeterminate = false; audioProgress.progress = (ls.progress * 1000).toInt() }
            AudioLoadState.Ready -> { audioProgress.isIndeterminate = false; audioProgress.progress = (st.progress * 1000).toInt() }
            is AudioLoadState.Error -> { audioProgress.isIndeterminate = false; audioProgress.progress = 1000 }
        }

        val ready = st.loadState is AudioLoadState.Ready
        audioPlay.isEnabled = ready
        audioPlay.alpha = if (ready) 1f else 0.4f
        audioPlay.setImageResource(if (st.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)

        audioTitle.text = when (val ls = st.loadState) {
            AudioLoadState.Connecting     -> "جارٍ الاتصال…"
            is AudioLoadState.Downloading -> if (ls.progress >= 0f) "جارٍ التحميل… ${(ls.progress * 100).toInt()}٪" else "جارٍ التحميل…"
            is AudioLoadState.Error       -> ls.message
            else                          -> st.readerName
        }
        audioSubtitle.text = if (st.ayaNum > 0) "آية ${toArNums(st.ayaNum)}" else ""
        audioSubtitle.visibility = if (st.ayaNum > 0) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        audioController.release()
    }

    // ── Shared last-read page (same store as the Compose reader) ─────────────────

    private val readerPrefs get() =
        getSharedPreferences(QuranViewModel.PREFS, Context.MODE_PRIVATE)

    private fun readLastPage(): Int = readerPrefs.getInt(QuranViewModel.KEY_PAGE, 0)

    private fun saveLastPage(index: Int) = readerPrefs.edit { putInt(QuranViewModel.KEY_PAGE, index) }

    /**
     * Single entry point for persisting reading progress: the per-session store in session mode,
     * else the shared last-read page (kept in sync with the Compose reader). Used both on open and
     * on every page change so the two paths can never drift.
     */
    private fun savePage(page: Int) {
        if (isSession) saveSessionPage(page)
        else saveLastPage(page - 1)
    }

    // ── Per-session last-read page (its own key, defaults to the session's first page) ──

    private fun sessionPageKey() = "$KEY_SESSION_PAGE_PREFIX$sessionId"

    private fun readSessionPage(): Int = readerPrefs.getInt(sessionPageKey(), firstPage)

    private fun saveSessionPage(page: Int) = readerPrefs.edit { putInt(sessionPageKey(), page) }

    // ── Background Sync ─────────────────────────────────────────────────────────

    /**
     * Keeps the ViewPager background perfectly synced with the pages' own canvas background.
     * Prevents the app's base theme from bleeding through during fast horizontal swipes.
     */
    private fun setupBackgroundSync() {
        val bgDrawable = PagerBackgroundDrawable()
        pager.background = bgDrawable

        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                BookReaderTheme.override,
                BookReaderPrefs.backgroundBrightness
            ) { _, bg -> bg }.collect { bg ->
                bgDrawable.nightMode = BookReaderTheme.effectiveNight(this@BookReaderActivity)
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
                        floatArrayOf(0f, 0.5f, 1f), // Centered for the underlying base
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
        const val EXTRA_PAGE = "book_page"
        const val EXTRA_START_PAGE = "book_start_page" // Khatmah session window, 1-based, inclusive
        const val EXTRA_END_PAGE = "book_end_page"
        const val EXTRA_SESSION_ID = "book_session_id" // identifies the session's saved progress
        private const val KEY_SESSION_PAGE_PREFIX = "session_page_"
        private const val MENU_EDGE_PAD_DP = 8f // overflow button's gap from the edge
        private const val TOOLBAR_ANIM_MS = 250L // matches Quran Android's animateToolBar
        private const val BOTTOM_PAD_DP = 24f    // bottom bar padding kept above the nav bar
        private const val POPUP_GAP_DP = 12f     // gap between the scrub popup and the bottom bar
    }
}
