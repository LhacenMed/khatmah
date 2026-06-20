package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ScrollView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.HafsQcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.Qcf4PageSource
import com.lhacenmed.khatmah.feature.quran.data.WarshQcf4Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders one page of the book reader — Quran Android's `QuranPageFragment` analog.
 *
 * The QCF4 page and its typefaces are decoded off the main thread, then handed to
 * a [BookPageView]. The active riwaya's QCF4 repository is resolved from
 * [MushafPrefs], so the fragment needs no construction-time dependencies.
 */
class BookPageFragment : Fragment() {

    private var pageView: BookPageView? = null

    private val pageNumber: Int get() = arguments?.getInt(ARG_PAGE) ?: 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val night = BookReaderTheme.effectiveNight(ctx)
        val view = BookPageView(ctx).apply {
            // Seed from current values so the first frame is correct…
            nightMode = night
            setAccentColor(accentFor(night))
            setBrightness(BookReaderPrefs.textBrightness.value, BookReaderPrefs.backgroundBrightness.value)
            showPageInfo = BookReaderPrefs.showPageInfo.value
            onTap = { (activity as? BookReaderActivity)?.toggleChrome() }
            alpha = 0f // Start transparent for native smooth fade-in
        }
        pageView = view
        loadPage()

        // Landscape: keep the page's portrait proportions at full width and scroll vertically
        // to reveal it. The page info stays at the page's own top/bottom, so it scrolls along.
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            view.pageAspect = portraitAspect()
            ScrollView(ctx).apply {
                isVerticalScrollBarEnabled = false
                addView(view, ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }
        } else {
            view
        }
    }

    /** Portrait aspect ratio (long side / short side) — the page's locked proportions. */
    private fun portraitAspect(): Float {
        val dm = resources.displayMetrics
        val maxD = maxOf(dm.widthPixels, dm.heightPixels).toFloat()
        val minD = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        return if (minD > 0f) maxD / minD else 0f
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // …then stay in sync: any display change (night/brightness/page-info) updates every
        // live page at once — including offscreen neighbours — so swiping never shows a stale
        // frame. The collection runs while the view exists (even when the settings screen is on
        // top), so pages are already correct by the time you return and slide.
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                BookReaderTheme.override,
                BookReaderPrefs.textBrightness,
                BookReaderPrefs.backgroundBrightness,
                BookReaderPrefs.showPageInfo,
            ) { _, text, bg, info -> Display(text, bg, info) }.collect { d ->
                val night = BookReaderTheme.effectiveNight(requireContext())
                pageView?.apply {
                    nightMode = night
                    // Accent (Material dynamic primary) must follow the reader's own theme, so a
                    // dark reader over a light app still uses the dark scheme's colours.
                    setAccentColor(accentFor(night))
                    setBrightness(d.text, d.bg)
                    showPageInfo = d.info
                }
            }
        }
    }

    private data class Display(val text: Int, val bg: Int, val info: Boolean)

    override fun onDestroyView() {
        super.onDestroyView()
        pageView = null
    }

    private fun loadPage() {
        val ctx = requireContext().applicationContext
        val repo: Qcf4PageSource =
            if (MushafPrefs.selected.value.riwaya == Riwaya.WARSH) WarshQcf4Repository.get(ctx)
            else HafsQcf4Repository.get(ctx)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val page = repo.pageData(pageNumber)
                    val fontNames = page.lines.asSequence()
                        .flatMap { it.words.asSequence() }
                        .map { it.font }
                        .toMutableSet()
                        .apply { add("QCF2_QBSML"); add("QCF4_QBSML") }
                    val faces = fontNames.associateWith { repo.typefaceFor(it) }
                    val fontRes =
                        if (repo.riwaya == Riwaya.HAFS) R.font.kfgqpc_hafs_uthmanic
                        else R.font.kfgqpc_warsh_uthmanic
                    val calligraphic = ResourcesCompat.getFont(ctx, fontRes) ?: Typeface.DEFAULT
                    val allMeta = BookPageMeta.loadMetaForRiwaya(ctx, repo.riwaya.dbKey)
                    val meta = allMeta[pageNumber] ?: BookMeta("", 1, pageNumber)
                    Loaded(page, faces, calligraphic, meta)
                }
            }
            val loaded = result.getOrNull() ?: return@launch
            pageView?.apply {
                setPage(loaded.page, loaded.faces, repo.riwaya, loaded.calligraphic)
                setPageInfo(loaded.meta.headerSura, loaded.meta.headerJuz, loaded.meta.footerPage)
                // Smooth native fade-in after content is set
                animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    /** Everything decoded off-thread for one page. */
    private class Loaded(
        val page: com.lhacenmed.khatmah.feature.quran.data.Qcf4Page,
        val faces: Map<String, Typeface>,
        val calligraphic: Typeface,
        val meta: BookMeta,
    )

    /**
     * The accent (primary) for the reader's effective theme — the dynamic-colour scheme built
     * for [night], independent of the app's current light/dark state. [BookPageView] dims it by
     * the night text-brightness; here it is the full-strength scheme primary.
     */
    private fun accentFor(night: Boolean): Int =
        resolveColorScheme(requireContext(), night).primary.toArgb()

    companion object {
        private const val ARG_PAGE = "page"

        fun newInstance(page: Int) = BookPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_PAGE, page) }
        }
    }
}
