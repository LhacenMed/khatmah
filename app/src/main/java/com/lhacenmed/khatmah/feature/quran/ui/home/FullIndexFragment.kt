package com.lhacenmed.khatmah.feature.quran.ui.home

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.widget.ListPopupWindow
import com.google.android.material.tabs.TabLayoutMediator
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.toIntent
import com.lhacenmed.khatmah.core.ui.collectWhileStarted
import com.lhacenmed.khatmah.databinding.FullIndexFragmentBinding
import com.lhacenmed.khatmah.feature.quran.ui.reader.readerDestAt
import com.lhacenmed.khatmah.shared.util.RecentSurahsPrefs

/** Suggestions shown under the field. Enough to recognise the one you meant, not a second list. */
private const val MAX_SUGGESTIONS = 8

/**
 * The index: surahs, ajza' and ahzab, each a tab, all built for the print currently selected.
 *
 * Search lives in the toolbar as a collapsible action view: expanding it takes the bar from the
 * title, which is the platform's own behaviour and the reason the page below holds only its tabs.
 * It is deliberately not a search of the tab you happen to be on — it reaches every index at once,
 * and each match says which it came from.
 *
 * Matches appear as a popup under the bar rather than replacing the lists, so the tab you were
 * reading is still behind them and dismissing costs nothing.
 *
 * A division is opened at its own page *and* its own first verse, and asks the reader to mark that
 * verse — a juz' or hizb usually begins mid-page, so landing there without a mark leaves you looking
 * for it. A surah is its own landmark and asks for nothing.
 */
class FullIndexFragment : Fragment(R.layout.full_index_fragment), MenuProvider {

    private val model: FullIndexViewModel by viewModels {
        FullIndexViewModel.Factory(requireContext())
    }

    private var binding: FullIndexFragmentBinding? = null

    private val pagerAdapter = IndexPagerAdapter(::open)
    private val suggestions = IndexSuggestionAdapter()
    private var popup: ListPopupWindow? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = FullIndexFragmentBinding.bind(view).also { binding = it }

        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        views.pager.adapter = pagerAdapter
        TabLayoutMediator(views.tabs, views.pager) { tab, position ->
            tab.setText(IndexKind.entries[position].tabRes)
        }.attach()

        collectWhileStarted(model.index) { data ->
            views.loading.isVisible = data.all.isEmpty()
            pagerAdapter.submit(data)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The popup is its own window; it would outlive the bar that anchors it.
        dismissSuggestions()
        popup = null
        binding = null
    }

    // ── Toolbar search ────────────────────────────────────────────────────────

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.full_index_menu, menu)
        val item = menu.findItem(R.id.action_search) ?: return
        val search = item.actionView as? SearchView ?: return

        search.queryHint = getString(R.string.full_index_search_hint)
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(text: String?): Boolean {
                showSuggestions(search, text.orEmpty())
                return true
            }

            // The matches are already below the field; there is nothing further to submit to.
            override fun onQueryTextSubmit(query: String?): Boolean = true
        })

        // Collapsing is the one exit — by the bar's own arrow or by back — so it is where the
        // suggestions are dropped, whichever way it was reached.
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                dismissSuggestions()
                return true
            }
        })
    }

    /** Nothing to select here: the action view opens itself, and the popup follows the query. */
    override fun onMenuItemSelected(item: MenuItem): Boolean = false

    private fun showSuggestions(anchor: SearchView, query: String) {
        val matches = model.index.value.search(query).take(MAX_SUGGESTIONS)
        suggestions.submit(matches)
        if (matches.isEmpty()) return dismissSuggestions()

        val window = popup ?: ListPopupWindow(requireContext()).also {
            popup = it
            // Non-modal, so the keyboard keeps focus and the query can go on being typed.
            it.isModal = false
            it.width = ViewGroup.LayoutParams.MATCH_PARENT
            it.setAdapter(suggestions)
            it.setOnItemClickListener { _, _, position, _ -> open(suggestions.getItem(position)) }
        }
        window.anchorView = anchor
        window.show()
    }

    private fun dismissSuggestions() {
        popup?.dismiss()
        suggestions.submit(emptyList())
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Opens the reader where [entry] begins. The surah is recorded as recently read whichever index
     * the row came from — a hizb is still read as part of the surah it opens in.
     */
    private fun open(entry: IndexEntry) {
        val context = requireContext()
        dismissSuggestions()
        RecentSurahsPrefs.record(context, entry.suraNum)
        val dest = readerDestAt(
            page = entry.page,
            suraNum = entry.suraNum,
            ayaNum = entry.ayaNum,
            highlight = entry.kind != IndexKind.SURAH,
        )
        startActivity(dest.toIntent(context))
    }
}
