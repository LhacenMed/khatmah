package com.lhacenmed.khatmah.feature.adhkar.ui.detail

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.IntentNavigator
import com.lhacenmed.khatmah.core.ui.theme.isAppInDarkTheme
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr
import com.lhacenmed.khatmah.feature.adhkar.ui.AdhkarViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dhikr reader for a single Adhkar category.
 *
 * Pages = `adhkar.size + 1`; the last one is a completion slide shown once every dhikr in the
 * category has been read. The title and the edit / font-size actions live in the host
 * Activity's shared top app bar — this fragment only owns the body and contributes its menu.
 *
 * Key design decisions:
 *  • [isBusy] is the single source of truth for "a finalising tap is in flight": set the moment
 *    the last repetition (or a single-rep advance) is accepted, cleared only when
 *    [onPageSelected] confirms the new page. Together with [isScrolling] it makes it impossible
 *    for a rapid burst of taps to leak counts across a page boundary.
 *  • The arc is driven by a [ValueAnimator] the fragment owns, so a page change can cancel it
 *    and snap to 0 without a frame of the previous page's progress showing.
 *  • The progress bar targets `page / adhkar.size`, so it starts empty and only fills on the
 *    completion page — it tracks adhkar finished, not adhkar seen.
 *  • The dhikr list is re-fetched whenever the ViewModel's version changes, so an edit or a
 *    reset in the editor is picked up automatically on return.
 */
class AdhkarDetailFragment : Fragment(R.layout.adhkar_detail_fragment), MenuProvider {

    private val vm: AdhkarViewModel by activityViewModels()

    private val categoryId    by lazy { requireArguments().getString(ARG_CATEGORY_ID).orEmpty() }
    private val categoryTitle by lazy { requireArguments().getString(ARG_CATEGORY_TITLE).orEmpty() }

    private val nav by lazy { IntentNavigator(requireActivity()) }
    private val scheme: ColorScheme by lazy {
        resolveColorScheme(requireContext(), isAppInDarkTheme(requireContext()))
    }

    private lateinit var content:      LinearLayout
    private lateinit var loading:      CircularProgressIndicator
    private lateinit var header:       LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBar:  LinearProgressIndicator
    private lateinit var scrim:        View
    private lateinit var pager:        ViewPager2
    private lateinit var bottomBar:    LinearLayout
    private lateinit var repRow:       View
    private lateinit var repLabel:     TextView
    private lateinit var repCircle:    RepCircleView
    private lateinit var shareButton:  ImageButton
    private lateinit var actionButton: MaterialButton

    private var adhkar: List<Dhikr> = emptyList()
    private var adapter: DhikrPagerAdapter? = null
    private var arcAnimator: ValueAnimator? = null

    /** Version of the adhkar store the current [adhkar] list was fetched at. */
    private var loadedVersion = -1

    /** Survives configuration changes and adapter swaps so the reader reopens where it was. */
    private var currentPage = 0
    private var fontSize = DhikrFontSize.MEDIUM

    private var isBusy = false
    private var isScrolling = false

    /**
     * True while a finalising tap's ring is still filling over the page slide. The two run
     * together, so the advance is complete only when both have settled — whichever finishes
     * last releases the tap lock.
     */
    private var isRingFilling = false

    private val isCompletionPage get() = currentPage >= adhkar.size
    private val dhikr get() = adhkar.getOrNull(currentPage)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        savedInstanceState?.let {
            currentPage = it.getInt(KEY_PAGE)
            fontSize    = DhikrFontSize.values()[it.getInt(KEY_FONT_SIZE)]
        }

        applyTheme()
        applyNavigationBarInset()
        bindInteractions()

        // Scoped to the view, not to RESUMED: a RESUMED scope drops the provider on ON_PAUSE,
        // which rebuilds the toolbar menu empty for the whole navigation animation. The host
        // renders one body, so nothing else contends for its menu.
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.uiState.map { it.version }.distinctUntilChanged().collect(::reloadIfStale)
                }
                launch {
                    vm.session.collect { repCircle.count = it.count }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, currentPage)
        outState.putInt(KEY_FONT_SIZE, fontSize.ordinal)
    }

    override fun onDestroyView() {
        arcAnimator?.cancel()
        arcAnimator = null
        super.onDestroyView()
    }

    // ── Wiring ────────────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        content      = view.findViewById(R.id.content)
        loading      = view.findViewById(R.id.loading)
        header       = view.findViewById(R.id.progress_header)
        progressText = view.findViewById(R.id.progress_label)
        progressBar  = view.findViewById(R.id.progress_bar)
        scrim        = view.findViewById(R.id.scrim)
        pager        = view.findViewById(R.id.pager)
        bottomBar    = view.findViewById(R.id.bottom_bar)
        repRow       = view.findViewById(R.id.rep_row)
        repLabel     = view.findViewById(R.id.rep_label)
        repCircle    = view.findViewById(R.id.rep_circle)
        shareButton  = view.findViewById(R.id.share_button)
        actionButton = view.findViewById(R.id.action_button)
    }

    /**
     * Paints every surface from the app colour scheme — the same source the host toolbar uses —
     * so a custom palette, dynamic colour or high contrast applies here identically. The two
     * emphasised labels take the real bold font rather than a synthesised one.
     */
    private fun applyTheme() {
        // One colour for the bar and for the scrim's landing point, so they can never drift.
        val barColor = scheme.surface.toArgb()
        val primary  = scheme.primary.toArgb()
        val bold     = ResourcesCompat.getFont(requireContext(), R.font.noto_kufi_bold)

        content.setBackgroundColor(scheme.background.toArgb())
        header.setBackgroundColor(scheme.surfaceContainer.toArgb())
        progressText.setTextColor(scheme.onSurfaceVariant.toArgb())
        progressBar.setIndicatorColor(primary)
        progressBar.trackColor = scheme.surfaceVariant.toArgb()
        scrim.background = scrimGradient(barColor)
        bottomBar.setBackgroundColor(barColor)
        repLabel.setTextColor(primary)
        repLabel.typeface = bold
        repCircle.setColors(
            track    = scheme.surfaceVariant.toArgb(),
            progress = primary,
            text     = scheme.onSurface.toArgb(),
        )
        shareButton.setColorFilter(scheme.onSurfaceVariant.toArgb())
        actionButton.setBackgroundColor(primary)
        actionButton.setTextColor(scheme.onPrimary.toArgb())
        actionButton.typeface = bold
        loading.setIndicatorColor(primary)
    }

    /**
     * Clear to the bar's own colour. Starting fully transparent is what makes the fade correct
     * whatever the body is painted with — only the colour it lands on has to match the bar.
     */
    private fun scrimGradient(barColor: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(ColorUtils.setAlphaComponent(barColor, 0), barColor),
    ).apply { setDither(true) } // smooth the ramp instead of banding it on 8-bit displays

    /** The bar's background runs edge to edge; only its content is lifted above the nav bar. */
    private fun applyNavigationBarInset() {
        val basePadding = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updatePadding(bottom = basePadding + bottom)
            insets
        }
        // The fragment view joins the hierarchy after the activity's first inset pass.
        ViewCompat.requestApplyInsets(bottomBar)
    }

    private fun bindInteractions() {
        // Tapping the chrome counts as a read too, so the whole screen behaves as one target.
        header.setOnClickListener { handleTap() }
        bottomBar.setOnClickListener { handleTap() }
        shareButton.setOnClickListener { share() }
        // The icon carries no label, so give it the same long-press tooltip the toolbar's own
        // actions get. TooltipCompat back-ports it below API 26.
        TooltipCompat.setTooltipText(shareButton, getString(R.string.dhikr_share))
        actionButton.setOnClickListener {
            if (isCompletionPage) nav.back() else handleTap()
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                // A finalising ring keeps filling over the slide and clears itself when it
                // lands, so the page change must not cut it short. Every other page change —
                // a user swipe, the first page — starts the new dhikr from a clean ring.
                if (!isRingFilling) resetDhikrProgress()
                renderPage()
            }

            override fun onPageScrollStateChanged(state: Int) {
                isScrolling = state != ViewPager2.SCROLL_STATE_IDLE
                // Swiping is re-armed once the pager stops moving — not in onPageSelected,
                // which fires mid-animation.
                if (!isScrolling) {
                    pager.isUserInputEnabled = true
                    endAdvanceIfSettled()
                }
            }
        })
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private suspend fun reloadIfStale(version: Int) {
        if (version == loadedVersion) return
        loadedVersion = version

        val list = vm.getDhikrForCategory(categoryId)
        if (list.isEmpty()) return nav.back()

        adhkar = list
        vm.cacheDhikr(categoryId, list)
        vm.startSession(categoryId)

        // A reload can land mid-advance; the jump below is instant and emits no scroll state,
        // so the lock is cleared here instead of leaving the pager permanently un-swipeable.
        isRingFilling = false
        isBusy = false
        pager.isUserInputEnabled = true
        resetDhikrProgress()

        adapter = DhikrPagerAdapter(list, categoryTitle, scheme, ::handleTap)
            .also { it.fontSize = fontSize }
        pager.adapter = adapter
        currentPage = currentPage.coerceIn(0, list.size)
        pager.setCurrentItem(currentPage, false)

        renderPage()
        loading.hide()
        content.isInvisible = false
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Repaints everything that depends on which page is showing. */
    private fun renderPage() {
        val reps = dhikr?.repetitions ?: 1
        // Capped so the counter reads "N/N" on the completion page.
        val read = minOf(currentPage + 1, adhkar.size)

        progressText.text = "${adhkar.size}/$read"
        progressBar.setProgressCompat(
            (100f * if (isCompletionPage) 1f else currentPage.toFloat() / adhkar.size).toInt(),
            true,
        )

        actionButton.setText(
            when {
                isCompletionPage -> R.string.dhikr_done
                reps <= 1        -> R.string.dhikr_next   // nothing to count: the tap advances
                else             -> R.string.dhikr_read
            }
        )

        // The rep row lags a finalising advance: its ring is still filling over the slide, so
        // it keeps the outgoing dhikr's presentation until the ring lands.
        if (!isRingFilling) renderRepRow()
    }

    /** Repaints the repetition row for the current page. */
    private fun renderRepRow() {
        val reps = dhikr?.repetitions ?: 1

        repRow.isInvisible = isCompletionPage
        repLabel.text = repetitionsLabel(requireContext(), reps)
        // Always laid out — only hidden — so the bar height never shifts between adhkar.
        repCircle.isInvisible = reps <= 1
    }

    /** Clears a dhikr's visible progress: empty ring, zero count. */
    private fun resetDhikrProgress() {
        arcAnimator?.cancel()
        repCircle.fraction = 0f
        vm.resetCount()
        renderCount()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * The single entry point for every count and page change, guarded against
     * the completion page, an in-flight finalising tap, and a user swipe in progress.
     */
    private fun handleTap() {
        if (isCompletionPage || isBusy || isScrolling) return

        val reps = dhikr?.repetitions ?: 1

        // Single-rep dhikr: one tap advances immediately.
        if (reps <= 1) {
            beginAdvance()
            goNext()
            return
        }

        val newCount = vm.session.value.count + 1
        vm.recordRead()
        renderCount()

        if (newCount >= reps) {
            // Last rep: lock, then let the ring finish filling over the slide rather than
            // before it — the two read as one motion, and the ring clears the moment it lands.
            beginAdvance()
            isRingFilling = true
            goNext()
            animateArc(1f) {
                isRingFilling = false
                resetDhikrProgress()
                renderRepRow()
                endAdvanceIfSettled()
            }
        } else {
            animateArc(newCount.toFloat() / reps)
        }
    }

    /**
     * Locks the reader for a finalising tap: no further tap is accepted ([isBusy]) and the pager
     * stops taking drags, so nothing can fight the ring fill or the programmatic slide to the
     * next dhikr. Swiping is restored once the pager settles (see [onPageScrollStateChanged]).
     */
    private fun beginAdvance() {
        isBusy = true
        pager.isUserInputEnabled = false
    }

    /**
     * Releases the tap lock once neither half of the advance is still running. Called from both
     * ends — the pager settling and the ring landing — so whichever finishes last unlocks, and
     * no tap can be accepted while the outgoing count is still on screen.
     */
    private fun endAdvanceIfSettled() {
        if (isScrolling || isRingFilling) return
        isBusy = false
    }

    /**
     * Mirrors the session count onto the circle. Called on every mutation so the digit changes
     * in the same frame as the tap — the session [kotlinx.coroutines.flow.StateFlow] conflates
     * and dispatches a frame later, which a rapid burst of taps outruns.
     */
    private fun renderCount() {
        repCircle.count = vm.session.value.count
    }

    /** Advances to the next page, or leaves the reader from the completion page. */
    private fun goNext() {
        if (currentPage < adhkar.size) pager.setCurrentItem(currentPage + 1, true) else nav.back()
        // isBusy stays true until onPageSelected fires on the new page.
    }

    private fun animateArc(target: Float, onEnd: (() -> Unit)? = null) {
        arcAnimator?.cancel()
        arcAnimator = ValueAnimator.ofFloat(repCircle.fraction, target).apply {
            duration = ARC_DURATION_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f) // Material's fast-out-slow-in
            addUpdateListener { repCircle.fraction = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun share() {
        val text = dhikr?.shareText ?: return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                null,
            )
        )
    }

    // ── Toolbar actions ───────────────────────────────────────────────────────

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.adhkar_detail_menu, menu)
        val tint = scheme.onSurfaceVariant.toArgb()
        for (i in 0 until menu.size()) menu.getItem(i).icon?.setTint(tint)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
        R.id.action_edit -> {
            nav.go(Dest.AdhkarEditor(categoryId))
            true
        }
        R.id.action_font_size -> {
            fontSize = fontSize.next()
            adapter?.fontSize = fontSize
            true
        }
        else -> false
    }

    companion object {
        private const val ARG_CATEGORY_ID    = "category_id"
        private const val ARG_CATEGORY_TITLE = "category_title"

        private const val KEY_PAGE      = "page"
        private const val KEY_FONT_SIZE = "font_size"

        private const val ARC_DURATION_MS = 400L

        fun newInstance(categoryId: String, categoryTitle: String) = AdhkarDetailFragment().apply {
            arguments = Bundle(2).apply {
                putString(ARG_CATEGORY_ID, categoryId)
                putString(ARG_CATEGORY_TITLE, categoryTitle)
            }
        }
    }
}
