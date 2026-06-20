package com.lhacenmed.khatmah.feature.quran.ui.reader.text

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.ui.theme.resolveColorScheme
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderActivity
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderPrefs
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders one text-reader page. Mirrors [com.lhacenmed.khatmah.feature.quran.ui.reader.book.BookPageFragment]:
 * the page data and faces are resolved off-thread, then handed to a [TextPageView]. The view is
 * wrapped in a fill-viewport [ScrollView] so a short page centres and a long page scrolls.
 */
class TextPageFragment : Fragment() {

    private var pageView: TextPageView? = null

    private val pageNumber: Int get() = arguments?.getInt(ARG_PAGE) ?: 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = requireContext()
        val night = ReaderTheme.effectiveNight(ctx)
        val view = TextPageView(ctx).apply {
            nightMode = night
            setAccentColor(accentFor(night))
            setBrightness(ReaderPrefs.textBrightness.value, ReaderPrefs.backgroundBrightness.value)
            onTap = { (activity as? ReaderActivity)?.toggleChrome() }
            onAyaLongPress = { sura, aya -> (activity as? ReaderActivity)?.onAyaLongPress(sura, aya) }
            alpha = 0f
        }
        pageView = view
        loadPage()
        return ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(view, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Keep every live page in sync with the reader's night/brightness state.
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                ReaderTheme.override,
                ReaderPrefs.textBrightness,
                ReaderPrefs.backgroundBrightness,
            ) { _, text, bg -> text to bg }.collect { (text, bg) ->
                val night = ReaderTheme.effectiveNight(requireContext())
                pageView?.apply {
                    nightMode = night
                    setAccentColor(accentFor(night))
                    setBrightness(text, bg)
                }
            }
        }
        // Highlight the playing verse; harmless on pages that don't own it.
        viewLifecycleOwner.lifecycleScope.launch {
            (requireActivity() as ReaderActivity).audioState.collect { st ->
                pageView?.selectedAya =
                    if (st.active && st.suraNum > 0) st.suraNum to st.ayaNum else null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pageView = null
    }

    private fun loadPage() {
        val ctx = requireContext().applicationContext
        val riwaya = MushafPrefs.selected.value.riwaya
        val src = TextReaderSource.get(ctx, riwaya)
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val page = src.pageData(pageNumber) ?: return@runCatching null
                    val bodyRes = if (riwaya == Riwaya.HAFS) R.font.kfgqpc_hafs_uthmanic else R.font.kfgqpc_warsh_uthmanic
                    val headRes = if (riwaya == Riwaya.HAFS) R.font.hafs_sura_name else R.font.warsh_sura_name
                    Loaded(
                        page,
                        ResourcesCompat.getFont(ctx, bodyRes) ?: Typeface.DEFAULT,
                        ResourcesCompat.getFont(ctx, headRes) ?: Typeface.DEFAULT,
                        src.basmala,
                    )
                }.getOrNull()
            } ?: return@launch
            pageView?.apply {
                setPage(loaded.page, loaded.body, loaded.header, loaded.basmala)
                animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    private class Loaded(
        val page: QuranPageData,
        val body: Typeface,
        val header: Typeface,
        val basmala: String,
    )

    private fun accentFor(night: Boolean): Int =
        resolveColorScheme(requireContext(), night).primary.toArgb()

    companion object {
        private const val ARG_PAGE = "page"

        fun newInstance(page: Int) = TextPageFragment().apply {
            arguments = Bundle().apply { putInt(ARG_PAGE, page) }
        }
    }
}
