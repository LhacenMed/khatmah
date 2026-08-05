package com.lhacenmed.khatmah.feature.quran.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.Reselectable
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.databinding.QuranTabBinding
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel.KhatmahState
import com.lhacenmed.khatmah.feature.quran.ui.reader.currentReaderDest
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import com.lhacenmed.khatmah.feature.quran.ui.reader.readerDestAt
import com.lhacenmed.khatmah.feature.quran.ui.reader.sessionReaderDest
import com.lhacenmed.khatmah.shared.util.RecentSurahsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val QUICK_SURAH_COUNT = 3

/**
 * The app's home: reading the Quran. The resume card and the surah shortcuts own the screen, and
 * the khatmah sits in a single strip at the bottom — one tap away, never in the way.
 *
 * The fragment is the seam between state and views: it collects, and [QuranTabViews] draws. Which
 * page a tap leads to depends on state that is still arriving, so the taps read the fields below
 * rather than values captured when the listener was set.
 */
class QuranTabFragment : Fragment(), Reselectable {

    private val vm: QuranHomeViewModel by activityViewModels { QuranHomeViewModel.Factory(requireContext().applicationContext) }

    private var views: QuranTabViews? = null

    /** Mushaf page each surah starts on, for the quick-index tiles. Empty until the print loads. */
    private var surahPages: Map<Int, Int> = emptyMap()

    /** Every surah of the active riwaya, in order — the pool the quick index is drawn from. */
    private var allSurahs: List<SurahInfo> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = QuranTabBinding.inflate(inflater, container, false)
        views = QuranTabViews(binding).apply {
            onContinueReading = { go(currentReaderDest()) }
            onFullIndex       = { go(Dest.FullIndex) }
            onSurahClick      = { suraNum ->
                RecentSurahsPrefs.record(requireContext(), suraNum)
                go(readerDestAt(surahPages[suraNum] ?: 1, suraNum))
            }
            onKhatmahClick = { openKhatmah() }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Where reading stopped, and the khatmah strip. The splash holds until both have real
        // content, so the tab draws in one pass — no placeholders, no shimmer.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.resume.combine(vm.khatmah) { resume, khatmah -> resume to khatmah }
                    .collect { (resume, khatmah) ->
                        views?.renderResume(resume)
                        views?.renderKhatmah(khatmah)
                        if (resume != null && khatmah !is KhatmahState.Loading) vm.markSplashReady()
                    }
            }
        }

        // The surah list is per-riwaya, so it is reloaded when the print changes. Recency is
        // observed too, so a surah read from any screen reorders the quick index on return.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MushafPrefs.selected.map { it.riwaya.dbKey }.collect { riwayaKey ->
                    loadSurahs(riwayaKey)
                    renderQuickIndex(RecentSurahsPrefs.recent.value)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                RecentSurahsPrefs.recent.collect(::renderQuickIndex)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        views = null
    }

    /**
     * Tapping the Quran tab while it is already showing means the same thing its hero button
     * does: back to the mushaf, where reading stopped. There is nothing here worth scrolling to.
     */
    override fun onReselect() {
        go(currentReaderDest())
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private suspend fun loadSurahs(riwayaKey: String) {
        val context = requireContext()
        val surahs = withContext(Dispatchers.IO) { QuranTextRepository(context).surahList(riwayaKey) }
        val pageStarts = withContext(Dispatchers.IO) {
            MushafDb.get(context).dao().allPageStarts(riwayaKey)
        }
        allSurahs = surahs
        surahPages = surahs.associate { it.num to surahStartPage(pageStarts, it.num) }
        RecentSurahsPrefs.get(context) // seed the recency flow from storage
    }

    private fun renderQuickIndex(recent: List<Int>) {
        views?.renderQuickIndex(resolveQuickSurahs(allSurahs, recent)) { surahPages[it] ?: 1 }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun go(dest: Dest) = startActivity(dest.toIntent(requireContext()))

    /**
     * The strip's tap: read the current wird, or start a khatmah when there is none. Sessions are
     * page-windowed, so a print that cannot honour a window is turned back at the door rather
     * than opening on the wrong pages.
     */
    private fun openKhatmah() {
        val active = vm.khatmah.value as? KhatmahState.Active
        val mushaf = MushafPrefs.selected.value
        when {
            active == null -> go(Dest.NewKhatmah)
            !mushaf.isQcf4 -> showDownloadDialog()
            mushaf.riwaya.dbKey != active.khatmah.riwaya -> showRiwayaMismatchDialog(active.khatmah.riwaya)
            else -> go(
                sessionReaderDest(active.session.id, active.session.startPage, active.session.endPage)
            )
        }
    }

    /** Shown when a session is opened on a print that ships no page images to window. */
    private fun showDownloadDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.today_dl_title)
            .setMessage(R.string.today_dl_msg)
            .setPositiveButton(R.string.today_settings) { _, _ -> go(Dest.MushafPrints) }
            .setNegativeButton(R.string.today_cancel, null)
            .show()
    }

    /**
     * Shown when the session's riwaya doesn't match the selected mushaf. Offers switching mushaf
     * or starting a new khatmah in the current one.
     */
    private fun showRiwayaMismatchDialog(khatmahRiwayaKey: String) {
        val context = requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.today_riwaya_mismatch_title)
            .setMessage(
                context.getString(
                    R.string.today_riwaya_mismatch_msg,
                    riwayaDisplayName(khatmahRiwayaKey),
                )
            )
            .setPositiveButton(R.string.today_settings) { _, _ -> go(Dest.MushafPrints) }
            .setNegativeButton(R.string.today_new_khatmah) { _, _ -> go(Dest.NewKhatmah) }
            .show()
    }
}

// ── Utilities ─────────────────────────────────────────────────────────────────

/** Human-readable Arabic riwaya label for the mismatch dialog. */
private fun riwayaDisplayName(key: String) = when (key) {
    "hafs"  -> "حفص"
    "warsh" -> "ورش"
    else    -> key
}

/** Returns the 1-based mushaf page where [suraNum] aya 1 begins. */
private fun surahStartPage(pageStarts: List<PageStartEntity>, suraNum: Int): Int {
    var result = pageStarts.firstOrNull()?.pageNum ?: 1
    for (ps in pageStarts) {
        if (ps.sura < suraNum || (ps.sura == suraNum && ps.aya <= 1)) result = ps.pageNum
        else break
    }
    return result
}

/**
 * Builds the Quick Index list: recently accessed surahs first, then fills
 * remaining slots from the beginning of the Quran (Al-Fatiha, Al-Baqara…),
 * skipping duplicates. Always returns exactly [QUICK_SURAH_COUNT] items
 * (or fewer if [all] has less data).
 */
private fun resolveQuickSurahs(all: List<SurahInfo>, recent: List<Int>): List<SurahInfo> {
    if (all.isEmpty()) return emptyList()
    val byNum       = all.associateBy { it.num }
    val recentItems = recent.mapNotNull { byNum[it] }
    val recentNums  = recentItems.map { it.num }.toSet()
    val fillItems   = all.filter { it.num !in recentNums }
    return (recentItems + fillItems).take(QUICK_SURAH_COUNT)
}
