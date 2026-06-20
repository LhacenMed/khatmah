package com.lhacenmed.khatmah.feature.today

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.motion.initialOffset
import com.lhacenmed.khatmah.core.motion.materialSharedAxisX
import com.lhacenmed.khatmah.core.motion.materialSharedAxisZ
import com.lhacenmed.khatmah.core.nav.AppTab
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.feature.quran.ui.book.currentReaderDest
import com.lhacenmed.khatmah.feature.quran.ui.book.readerDestAt
import com.lhacenmed.khatmah.feature.quran.ui.book.sessionReaderDest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.mushaf.data.db.MushafDb
import com.lhacenmed.khatmah.feature.mushaf.data.db.PageStartEntity
import com.lhacenmed.khatmah.feature.quran.data.QuranRepository
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo
import com.lhacenmed.khatmah.feature.today.components.KhatmahStats
import com.lhacenmed.khatmah.feature.today.components.NoKhatmahCard
import com.lhacenmed.khatmah.feature.today.components.AllReadCard
import com.lhacenmed.khatmah.feature.today.components.QuickIndexSection
import com.lhacenmed.khatmah.feature.today.components.SessionCard
import com.lhacenmed.khatmah.feature.today.components.SkeletonCard
import com.lhacenmed.khatmah.feature.today.components.SkeletonStats
import com.lhacenmed.khatmah.shared.util.RecentSurahsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val QUICK_SURAH_COUNT = 3

object TodayTab : AppTab(
    iconRes  = R.drawable.ic_book,
    titleRes = R.string.today,
    route    = "today",
) {
    @Composable override fun Content(padding: PaddingValues) = TodayScreen(padding)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun TodayScreen(padding: PaddingValues) {
    val nav      = LocalNavigator.current
    val context  = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    val vm: TodayViewModel = viewModel(activity)
    val state    by vm.state.collectAsState()
    val mushaf   by MushafPrefs.selected.collectAsState()

    var showDlDialog  by remember { mutableStateOf(false) }
    var mismatchState by remember { mutableStateOf<TodayViewModel.UiState.Active?>(null) }

    // ── Quick index data ──────────────────────────────────────────────────────
    val quranRepo    = remember { QuranRepository(context) }
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
        // Scrollable body
        LazyColumn(
            modifier            = Modifier.weight(1f).fillMaxWidth(),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "card") {
                AnimatedContent(
                    targetState  = state,
                    modifier     = Modifier.clipToBounds(),
                    contentKey   = { s ->
                        when (s) {
                            is TodayViewModel.UiState.Active -> s.session.entity.id
                            else                             -> s::class
                        }
                    },
                    transitionSpec = {
                        when {
                            initialState is TodayViewModel.UiState.Loading ||
                                    targetState is TodayViewModel.UiState.Loading ->
                                EnterTransition.None togetherWith ExitTransition.None

                            initialState is TodayViewModel.UiState.Active &&
                                    targetState is TodayViewModel.UiState.Active ->
                                materialSharedAxisX(
                                    initialOffsetX = { (it * initialOffset).toInt() },
                                    targetOffsetX  = { -(it * initialOffset).toInt() },
                                )

                            else -> materialSharedAxisZ(forward = true)
                        }
                    },
                    label = "session_card",
                ) { s ->
                    when (s) {
                        is TodayViewModel.UiState.Loading   -> SkeletonCard()
                        is TodayViewModel.UiState.NoKhatmah -> NoKhatmahCard { nav.go(Dest.NewKhatmah) }
                        is TodayViewModel.UiState.AllRead   -> AllReadCard(
                            onDua        = { /* TODO: navigate to dua */ },
                            onNewKhatmah = { nav.go(Dest.NewKhatmah) },
                        )
                        is TodayViewModel.UiState.Active    -> SessionCard(
                            state      = s,
                            onMarkRead = { vm.markRead(s.session.entity.id) },
                            onRead     = {
                                when {
                                    mushaf == MushafPrint.WarshText || mushaf == MushafPrint.HafsText ->
                                        showDlDialog = true
                                    mushaf.riwaya.dbKey != s.khatmah.riwaya ->
                                        mismatchState = s
                                    else ->
                                        nav.go(
                                            sessionReaderDest(
                                                s.session.entity.id,
                                                s.session.entity.startPage,
                                                s.session.entity.endPage,
                                            )
                                        )
                                }
                            },
                        )
                    }
                }
            }

            // Quick index — shown once surah data is loaded
            if (quickSurahs.isNotEmpty()) {
                item(key = "quick_index") {
                    QuickIndexSection(
                        surahs            = quickSurahs,
                        pageFor           = { surahPageMap[it] ?: 1 },
                        onContinueReading = { nav.go(currentReaderDest()) },
                        onSurahClick = { suraNum ->
                            RecentSurahsPrefs.record(context, suraNum)
                            nav.go(readerDestAt(surahPageMap[suraNum] ?: 1, suraNum))
                        },
                        onFullIndex       = { nav.go(Dest.FullIndex) },
                    )
                }
            }
        }

        // Stats strip — fixed to bottom of screen
        AnimatedContent(
            targetState    = state,
            contentKey     = { s -> s::class },
            modifier       = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            transitionSpec = {
                when {
                    initialState is TodayViewModel.UiState.Loading ||
                            targetState is TodayViewModel.UiState.Loading ->
                        EnterTransition.None togetherWith ExitTransition.None
                    else ->
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                }
            },
            label = "stats_strip",
        ) { s ->
            when (s) {
                is TodayViewModel.UiState.Active  -> {
                    SideEffect { vm.markSplashReady() }
                    KhatmahStats(readCount = s.readCount, totalCount = s.khatmah.totalDays)
                }
                is TodayViewModel.UiState.AllRead -> {
                    SideEffect { vm.markSplashReady() }
                    KhatmahStats(readCount = s.totalDays, totalCount = s.totalDays)
                }
                is TodayViewModel.UiState.Loading -> SkeletonStats()
                else                              -> {
                    // NoKhatmah — no stats strip; still release the splash.
                    SideEffect { vm.markSplashReady() }
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun MushafDownloadDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.today_dl_title)) },
        text             = { Text(stringResource(R.string.today_dl_msg)) },
        confirmButton    = {
            TextButton(onClick = onSettings) { Text(stringResource(R.string.today_settings)) }
        },
        dismissButton    = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.today_cancel)) }
        },
    )
}

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