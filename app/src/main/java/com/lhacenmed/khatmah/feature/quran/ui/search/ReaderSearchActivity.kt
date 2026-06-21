package com.lhacenmed.khatmah.feature.quran.ui.search

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen Quran search for the reader — its own activity, consistent with
 * [com.lhacenmed.khatmah.feature.quran.ui.settings.ReaderSettingsActivity]. The native toolbar hosts
 * the query field and a clear action; the platform back arrow exits.
 *
 * Scope follows the reader: a session passes [EXTRA_FIRST_PAGE]/[EXTRA_LAST_PAGE] and results are
 * narrowed to those QCF4 pages. A hit returns [RESULT_SURA]/[RESULT_AYA] to the reader, which jumps
 * to that aya's page.
 */
class ReaderSearchActivity : AppCompatActivity() {

    private val firstPage get() = intent.getIntExtra(EXTRA_FIRST_PAGE, 0)
    private val lastPage  get() = intent.getIntExtra(EXTRA_LAST_PAGE, 0)
    private val isSession get() = firstPage in 1..lastPage

    private val riwaya    by lazy { MushafPrefs.selected.value.riwaya }
    private val textRepo  by lazy { QuranTextRepository(applicationContext) }
    private val qcf4      by lazy { Qcf4Repository.get(applicationContext, riwaya) }
    private var ayaPageCache: Map<Long, Int>? = null

    private lateinit var input: EditText
    private lateinit var results: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var adapter: ResultAdapter
    private var searchJob: Job? = null
    private var clearItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.book_search_activity)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val toolbarArea = findViewById<View>(R.id.toolbar_area)
        input    = findViewById(R.id.search_input)
        results  = findViewById(R.id.search_results)
        progress = findViewById(R.id.search_progress)
        empty    = findViewById(R.id.search_empty)

        setupToolbar(toolbar)
        applyInsets(toolbarArea, results)
        setupResults()

        input.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            clearItem?.isVisible = query.isNotEmpty()
            onQuery(query)
        }
        onBackPressedDispatcher.addCallback(this) {
            if (input.text.isNullOrEmpty()) { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            else input.setText("")
        }

        input.requestFocus()
        input.post { imm().showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun setupToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        val onSurface = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        toolbar.navigationIcon?.setTint(onSurface)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_search_menu, menu)
        clearItem = menu.findItem(R.id.action_clear).apply {
            isVisible = input.text.isNotEmpty()
            icon?.setTint(MaterialColors.getColor(
                findViewById(R.id.toolbar), com.google.android.material.R.attr.colorOnSurface))
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_clear -> { input.setText(""); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupResults() {
        val font = ResourcesCompat.getFont(
            this,
            if (riwaya == Riwaya.HAFS) R.font.kfgqpc_hafs_uthmanic else R.font.kfgqpc_warsh_uthmanic,
        )
        adapter = ResultAdapter(font) { result ->
            setResult(RESULT_OK, Intent()
                .putExtra(RESULT_SURA, result.suraNum)
                .putExtra(RESULT_AYA, result.ayaNum))
            finish()
        }
        results.layoutManager = LinearLayoutManager(this)
        results.adapter = adapter
        results.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
    }

    /** Debounced search; clears results immediately when the query is blank. */
    private fun onQuery(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            adapter.submit(emptyList())
            progress.isVisible = false
            empty.isVisible = false
            return
        }
        searchJob = lifecycleScope.launch {
            delay(300)
            progress.isVisible = true
            empty.isVisible = false
            val hits = searchInScope(query)
            progress.isVisible = false
            adapter.submit(hits)
            results.scrollToPosition(0)
            empty.isVisible = hits.isEmpty()
        }
    }

    /** Active-riwaya search, narrowed to the open session's pages when in session mode. */
    private suspend fun searchInScope(query: String): List<SearchResult> {
        val hits = textRepo.search(query, riwaya.dbKey)
        if (!isSession) return hits
        val map = ayaPageMap()
        return hits.filter { r ->
            val page = map[(r.suraNum.toLong() shl 32) or r.ayaNum.toLong()]?.plus(1)
            page != null && page in firstPage..lastPage
        }
    }

    /** Cached aya→page index (0-based) for the QCF4 pagination — only used to scope sessions. */
    private suspend fun ayaPageMap(): Map<Long, Int> =
        ayaPageCache ?: qcf4.ayaPageIndex().also { ayaPageCache = it }

    /** Top-pad the toolbar below the status bar; bottom-pad the list above the keyboard / nav bar. */
    private fun applyInsets(toolbarArea: View, results: RecyclerView) {
        val listBottom = results.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(toolbarArea) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(results) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.updatePadding(bottom = listBottom + maxOf(ime, nav))
            insets
        }
    }

    private fun hideKeyboard() = imm().hideSoftInputFromWindow(input.windowToken, 0)

    private fun imm() = getSystemService(InputMethodManager::class.java)

    // ── Results adapter ─────────────────────────────────────────────────────────

    private class ResultAdapter(
        private val ayaFont: Typeface?,
        private val onClick: (SearchResult) -> Unit,
    ) : RecyclerView.Adapter<ResultAdapter.Row>() {

        private val items = mutableListOf<SearchResult>()

        fun submit(results: List<SearchResult>) {
            items.clear()
            items.addAll(results)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.book_search_result_item, parent, false)
            return Row(view, ayaFont, onClick)
        }

        override fun onBindViewHolder(holder: Row, position: Int) = holder.bind(items[position])

        override fun getItemCount() = items.size

        class Row(
            view: View,
            private val ayaFont: Typeface?,
            private val onClick: (SearchResult) -> Unit,
        ) : RecyclerView.ViewHolder(view) {
            private val num  = view.findViewById<TextView>(R.id.result_num)
            private val aya  = view.findViewById<TextView>(R.id.result_aya)
            private val meta = view.findViewById<TextView>(R.id.result_meta)

            fun bind(result: SearchResult) {
                num.text  = arNum(result.suraNum)
                aya.text  = result.ayaText
                aya.typeface = ayaFont
                meta.text = "${result.suraName}  ·  آية ${arNum(result.ayaNum)}"
                itemView.setOnClickListener { onClick(result) }
            }
        }
    }

    companion object {
        const val EXTRA_FIRST_PAGE = "search_first_page" // session window, 1-based, inclusive
        const val EXTRA_LAST_PAGE  = "search_last_page"
        const val RESULT_SURA      = "result_sura"
        const val RESULT_AYA       = "result_aya"
    }
}

/** Renders [n] in Eastern Arabic numerals. */
private fun arNum(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
