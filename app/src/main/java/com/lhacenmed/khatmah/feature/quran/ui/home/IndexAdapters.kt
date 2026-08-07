package com.lhacenmed.khatmah.feature.quran.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.databinding.IndexPageBinding
import com.lhacenmed.khatmah.databinding.IndexRowBinding
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums

/**
 * Writes an index line into a row. One function, because the tabs and the search suggestions show
 * the same thing and there is no reason for two of them to drift.
 */
private fun IndexRowBinding.bind(entry: IndexEntry) {
    number.text = toArNums(entry.num)
    title.text = entry.title
    subtitle.isVisible = !entry.subtitle.isNullOrBlank()
    subtitle.text = entry.subtitle
    page.text = root.context.getString(R.string.today_page, entry.page)
}

// ── Tab lists ─────────────────────────────────────────────────────────────────

/** One tab's rows. Diffed by [IndexEntry.id], so a change of print redraws only what moved. */
class IndexRowAdapter(
    private val onClick: (IndexEntry) -> Unit,
) : ListAdapter<IndexEntry, IndexRowAdapter.Holder>(Diff) {

    class Holder(val binding: IndexRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(IndexRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.binding.bind(entry)
        holder.binding.root.setOnClickListener { onClick(entry) }
    }

    private object Diff : DiffUtil.ItemCallback<IndexEntry>() {
        override fun areItemsTheSame(old: IndexEntry, new: IndexEntry) = old.id == new.id
        override fun areContentsTheSame(old: IndexEntry, new: IndexEntry) = old == new
    }
}

/**
 * The three index tabs, as pages.
 *
 * Each page keeps its own list adapter for the life of the screen, so swiping between tabs rebinds
 * nothing and the lists hold their scroll positions. New data is handed to those adapters directly
 * — the pager itself never changes, which is what keeps a reload from throwing the reader's place
 * away.
 */
class IndexPagerAdapter(
    onClick: (IndexEntry) -> Unit,
) : RecyclerView.Adapter<IndexPagerAdapter.PageHolder>() {

    private val kinds = IndexKind.entries
    private val adapters = kinds.associateWith { IndexRowAdapter(onClick) }

    class PageHolder(val binding: IndexPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemCount() = kinds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PageHolder(IndexPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.binding.list.adapter = adapters.getValue(kinds[position])
    }

    fun submit(data: IndexData) {
        kinds.forEach { adapters.getValue(it).submitList(data.byKind[it].orEmpty()) }
    }
}

// ── Search suggestions ────────────────────────────────────────────────────────

/**
 * The matches under the search field. A [android.widget.ListPopupWindow] wants a plain
 * [BaseAdapter], so this is one — over the same row as the lists, so a suggestion looks like the
 * line it will take you to.
 */
class IndexSuggestionAdapter : BaseAdapter() {

    private var entries: List<IndexEntry> = emptyList()

    fun submit(matches: List<IndexEntry>) {
        entries = matches
        notifyDataSetChanged()
    }

    override fun getCount() = entries.size
    override fun getItem(position: Int) = entries[position]
    override fun getItemId(position: Int) = entries[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = convertView?.tag as? IndexRowBinding
            ?: IndexRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .also { it.root.tag = it }
        binding.bind(entries[position])
        return binding.root
    }
}
