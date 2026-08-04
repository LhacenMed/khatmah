package com.lhacenmed.khatmah.feature.quran.ui.home

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.data.QuranTextRepository
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo
import com.lhacenmed.khatmah.feature.quran.data.db.MushafDb
import com.lhacenmed.khatmah.feature.quran.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.ui.home.QuranHomeViewModel.KhatmahState
import com.lhacenmed.khatmah.feature.quran.ui.home.components.KhatmahStrip
import com.lhacenmed.khatmah.feature.quran.ui.home.components.QuickIndexSection
import com.lhacenmed.khatmah.feature.quran.ui.home.components.ResumeCard
import com.lhacenmed.khatmah.feature.quran.ui.reader.MushafDownloadDialog
import com.lhacenmed.khatmah.feature.quran.ui.reader.currentReaderDest
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import com.lhacenmed.khatmah.feature.quran.ui.reader.readerDestAt
import com.lhacenmed.khatmah.feature.quran.ui.reader.sessionReaderDest
import com.lhacenmed.khatmah.shared.util.RecentSurahsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QUICK_SURAH_COUNT = 3

/**
 * The app's home: reading the Quran. The resume card and the surah shortcuts own the screen,
 * and the khatmah sits in a single strip at the bottom — one tap away, never in the way.
 */
object QuranTab : AppTab(
    iconRes  = R.drawable.ic_book,
    titleRes = R.string.quran,
    route    = "quran",
) {
    @Composable override fun Content(padding: PaddingValues) = QuranScreen(padding)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun QuranScreen(padding: PaddingValues) {
    val nav      = LocalNavigator.current
    val context  = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    val vm: QuranHomeViewModel = viewModel(activity)
    val resume   by vm.resume.collectAsState()
    val khatmah  by vm.khatmah.collectAsState()
    val mushaf   by MushafPrefs.selected.collectAsState()

    var showDlDialog  by remember { mutableStateOf(false) }
    var mismatchState by remember { mutableStateOf<KhatmahState.Active?>(null) }

    // ── Quick index data ──────────────────────────────────────────────────────
    val quranRepo    = remember { QuranTextRepository(context) }
    var allSurahs    by remember { mutableStateOf<List<SurahInfo>>(emptyList()) }
    var surahPageMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    // Recency is observed, so a surah read from any screen reorders the Quick Index on return.
    val recent       by RecentSurahsPrefs.recent.collectAsState()
    val quickSurahs  = remember(allSurahs, recent) { resolveQuickSurahs(allSurahs, recent) }

    LaunchedEffect(mushaf.riwaya.dbKey) {
        val riwayaKey = mushaf.riwaya.dbKey
        val all = withContext(Dispatchers.IO) { quranRepo.surahList(riwayaKey) }
        allSurahs = all
        val pageStarts = withContext(Dispatchers.IO) {
            MushafDb.get(context).dao().allPageStarts(riwayaKey)
        }
        surahPageMap = all.associate { s -> s.num to surahStartPage(pageStarts, s.num) }
        RecentSurahsPrefs.get(context) // seed the recency flow from storage
    }

    // The splash holds until both halves have real content, so the tab draws in one pass —
    // no placeholders, no shimmer.
    if (resume != null && khatmah !is KhatmahState.Loading) {
        SideEffect { vm.markSplashReady() }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showDlDialog) {
        MushafDownloadDialog(
            onSettings = { showDlDialog = false; nav.go(Dest.MushafPrints) },
            onDismiss  = { showDlDialog = false },
        )
    }

    mismatchState?.let { active ->
        RiwayaMismatchDialog(
            khatmahRiwayaKey = active.khatmah.riwaya,
            onSettings       = { mismatchState = null; nav.go(Dest.MushafPrints) },
            onNewKhatmah     = { mismatchState = null; nav.go(Dest.NewKhatmah) },
            onDismiss        = { mismatchState = null },
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            resume?.let { r ->
                item(key = "resume") {
                    ResumeCard(resume = r, onContinue = { nav.go(currentReaderDest()) })
                }
            }

            if (quickSurahs.isNotEmpty()) {
                item(key = "quick_index") {
                    QuickIndexSection(
                        surahs       = quickSurahs,
                        pageFor      = { surahPageMap[it] ?: 1 },
                        onSurahClick = { suraNum ->
                            RecentSurahsPrefs.record(context, suraNum)
                            nav.go(readerDestAt(surahPageMap[suraNum] ?: 1, suraNum))
                        },
                        onFullIndex  = { nav.go(Dest.FullIndex) },
                    )
                }
            }
        }

        // Khatmah strip — pinned above the bottom bar, hidden until the khatmah resolves.
        if (khatmah !is KhatmahState.Loading) {
            Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                KhatmahStrip(
                    state   = khatmah,
                    onClick = {
                        val active = khatmah as? KhatmahState.Active
                        when {
                            active == null -> nav.go(Dest.NewKhatmah)
                            // Sessions are page-windowed — only the QCF4 mushaf can honour them.
                            !mushaf.isQcf4 -> showDlDialog = true
                            mushaf.riwaya.dbKey != active.khatmah.riwaya -> mismatchState = active
                            else -> nav.go(
                                sessionReaderDest(
                                    active.session.id,
                                    active.session.startPage,
                                    active.session.endPage,
                                )
                            )
                        }
                    },
                )
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/**
 * Shown when the user tries to read a session whose riwaya doesn't match
 * the currently selected mushaf. Offers switching mushaf or starting a new khatmah.
 */
@Composable
private fun RiwayaMismatchDialog(
    khatmahRiwayaKey: String,
    onSettings:       () -> Unit,
    onNewKhatmah:     () -> Unit,
    onDismiss:        () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_riwaya_mismatch_title)) },
        text  = {
            Text(stringResource(R.string.today_riwaya_mismatch_msg, riwayaDisplayName(khatmahRiwayaKey)))
        },
        confirmButton = {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.today_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onNewKhatmah) { Text(stringResource(R.string.today_new_khatmah)) }
        },
    )
}

/** Human-readable Arabic riwaya label for the mismatch dialog. */
private fun riwayaDisplayName(key: String) = when (key) {
    "hafs"  -> "حفص"
    "warsh" -> "ورش"
    else    -> key
}

// ── Utilities ─────────────────────────────────────────────────────────────────

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
    val byNum    = all.associateBy { it.num }
    val recentItems = recent.mapNotNull { byNum[it] }
    val recentNums  = recentItems.map { it.num }.toSet()
    val fillItems   = all.filter { it.num !in recentNums }
    return (recentItems + fillItems).take(QUICK_SURAH_COUNT)
}
