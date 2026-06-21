package com.lhacenmed.khatmah.feature.today

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.quran.data.DivType
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.MushafPrint
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Repository
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import com.lhacenmed.khatmah.feature.quran.ui.reader.readerDestAt
import com.lhacenmed.khatmah.shared.util.RecentSurahsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Data ──────────────────────────────────────────────────────────────────────

data class JuzInfo(
    val num:           Int,
    val startSuraNum:  Int,
    val startSuraName: String,
    val startAya:      Int,
    val page:          Int,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class FullIndexViewModel(context: Context) : ViewModel() {

    private val _surahs       = MutableStateFlow<List<SurahInfo>>(emptyList())
    val surahs: StateFlow<List<SurahInfo>> = _surahs.asStateFlow()

    private val _surahPageMap = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val surahPageMap: StateFlow<Map<Int, Int>> = _surahPageMap.asStateFlow()

    private val _juzList      = MutableStateFlow<List<JuzInfo>>(emptyList())
    val juzList: StateFlow<List<JuzInfo>> = _juzList.asStateFlow()

    init {
        val appContext = context.applicationContext
        // Rebuild whenever the selected print changes — Warsh and Hafs differ in surah pages and
        // juz' starts, and each QCF4 print paginates independently, so the index always matches the
        // reader it opens. StateFlow only emits on change, so this fires once per real switch.
        viewModelScope.launch {
            MushafPrefs.selected.collect { print -> loadIndex(appContext, print) }
        }
    }

    private suspend fun loadIndex(appContext: Context, print: MushafPrint) =
        withContext(Dispatchers.IO) {
            val riwayaKey  = print.riwaya.dbKey
            val dao        = MushafDb.get(appContext).dao()
            val all        = QuranTextRepository(appContext).surahList(riwayaKey)
            val pageStarts = dao.allPageStarts(riwayaKey)
            val divisions  = dao.divisions(riwayaKey, DivType.JUZ)
            val nameMap    = dao.surahs(riwayaKey).associate { it.num to it.name }

            // Resolve pages from the same pagination the reader uses (see [pageResolver]).
            val pageOf = pageResolver(appContext, print, pageStarts)

            _surahs.value       = all
            _surahPageMap.value = all.associate { s -> s.num to pageOf(s.num, 1) }
            _juzList.value      = divisions.map { d ->
                JuzInfo(
                    num           = d.num,
                    startSuraNum  = d.sura,
                    startSuraName = nameMap[d.sura].orEmpty(),
                    startAya      = d.aya,
                    page          = pageOf(d.sura, d.aya),
                )
            }
        }

    /**
     * Page lookup for an aya, matching the reader the [print] opens in. QCF4 prints render their own
     * downloaded layout (e.g. Warsh QCF4 is paginated independently of the generic 604-page Warsh),
     * so their surah/juz' pages come from the QCF4 verse→page index; other prints use
     * `mushaf_page_start`. Falls back to page starts when the QCF4 index isn't populated yet.
     */
    private suspend fun pageResolver(
        appContext: Context,
        print: MushafPrint,
        pageStarts: List<PageStartEntity>,
    ): (sura: Int, aya: Int) -> Int {
        val byStart: (Int, Int) -> Int = { sura, aya -> pageFor(pageStarts, sura, aya) }
        if (!print.isQcf4) return byStart

        val source = Qcf4Repository.get(appContext, print.riwaya)
        val ayaPage = runCatching { source.ayaPageIndex() }.getOrDefault(emptyMap())
        if (ayaPage.isEmpty()) return byStart
        return { sura, aya ->
            ayaPage[(sura.toLong() shl 32) or aya.toLong()]?.plus(1) ?: byStart(sura, aya)
        }
    }

    class Factory(private val ctx: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FullIndexViewModel(ctx.applicationContext) as T
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
internal fun FullIndexScreen() {
    val nav     = LocalNavigator.current
    val context = LocalContext.current
    val vm: FullIndexViewModel = viewModel(factory = FullIndexViewModel.Factory(context))

    val surahs       by vm.surahs.collectAsState()
    val surahPageMap by vm.surahPageMap.collectAsState()
    val juzList      by vm.juzList.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState  = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) pagerState.scrollToPage(selectedTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTab) selectedTab = pagerState.currentPage
    }

    // Body only — the title + back arrow come from ScreenHostActivity (see Dest.FullIndex.titleRes).
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick  = { selectedTab = 0 },
                text     = { Text(stringResource(R.string.full_index_tab_surahs)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick  = { selectedTab = 1 },
                text     = { Text(stringResource(R.string.full_index_tab_ajza)) },
            )
        }
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> SurahsContent(surahs, surahPageMap) { suraNum ->
                    RecentSurahsPrefs.record(context, suraNum)
                    nav.go(readerDestAt(surahPageMap[suraNum] ?: 1, suraNum))
                }
                // A juz' opens at its own start page (it can begin mid-surah), targeting the
                // exact sura/aya so the Compose reader lands there too — not the surah's first page.
                else -> AjzaContent(juzList) { juz ->
                    RecentSurahsPrefs.record(context, juz.startSuraNum)
                    nav.go(readerDestAt(juz.page, juz.startSuraNum, juz.startAya))
                }
            }
        }
    }
}

// ── Tab content ───────────────────────────────────────────────────────────────

@Composable
private fun SurahsContent(
    surahs:       List<SurahInfo>,
    surahPageMap: Map<Int, Int>,
    onSurahClick: (Int) -> Unit,
) {
    if (surahs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(surahs, key = { _, s -> s.num }) { i, surah ->
            ListItem(
                modifier          = Modifier.clickable { onSurahClick(surah.num) },
                headlineContent   = { Text(surah.name, style = MaterialTheme.typography.bodyLarge) },
                leadingContent    = {
                    Text(
                        text  = toArNums(surah.num),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent   = {
                    Text(
                        text  = stringResource(R.string.today_page, surahPageMap[surah.num] ?: 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (i < surahs.lastIndex) IndexDivider()
        }
    }
}

@Composable
private fun AjzaContent(juzList: List<JuzInfo>, onJuzClick: (JuzInfo) -> Unit) {
    if (juzList.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(juzList, key = { _, j -> j.num }) { i, juz ->
            ListItem(
                modifier          = Modifier.clickable { onJuzClick(juz) },
                headlineContent   = {
                    Text(
                        text       = stringResource(R.string.full_index_juz, juz.num),
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                supportingContent = {
                    Text(
                        text  = juz.startSuraName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent    = {
                    Text(
                        text  = toArNums(juz.num),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent   = {
                    Text(
                        text  = stringResource(R.string.today_page, juz.page),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (i < juzList.lastIndex) IndexDivider()
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun IndexDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun pageFor(pageStarts: List<PageStartEntity>, sura: Int, aya: Int): Int {
    var result = pageStarts.firstOrNull()?.pageNum ?: 1
    for (ps in pageStarts) {
        if (ps.sura < sura || (ps.sura == sura && ps.aya <= aya)) result = ps.pageNum
        else break
    }
    return result
}

private fun toArNums(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")
