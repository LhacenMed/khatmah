package com.lhacenmed.khatmah.feature.quran.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.nav.NavScreen
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.feature.quran.data.QuranRepository
import com.lhacenmed.khatmah.feature.quran.data.SurahInfo

// Items within this distance from the top animate directly; farther ones jump-then-animate.
private const val SMOOTH_SCROLL_THRESHOLD = 4

// ── Tab registration ──────────────────────────────────────────────────────────

val IndexTab = NavScreen(
    route    = Route.INDEX,
    iconRes  = R.drawable.ic_list,
    labelRes = R.string.index,
) { padding -> IndexScreen(padding) }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun IndexScreen(padding: PaddingValues) {
    val context     = LocalContext.current
    val nav         = LocalNavController.current
    val listState   = rememberLazyListState()
    val scrollToTop = LocalScrollToTop.current

    val repo = remember { QuranRepository(context) }
    var surahs by remember { mutableStateOf<List<SurahInfo>>(emptyList()) }

    LaunchedEffect(Unit) { surahs = repo.surahList() }

    // Two-phase scroll-to-top: instant jump near the top, then smooth animation.
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            if (listState.firstVisibleItemIndex > SMOOTH_SCROLL_THRESHOLD) {
                listState.scrollToItem(SMOOTH_SCROLL_THRESHOLD)
            }
            listState.animateScrollToItem(0)
        }
    }

    if (surahs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        state          = listState,
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top    = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 8.dp,
        ),
    ) {
        // ── Read Quran button ─────────────────────────────────────────────────
        item(key = "read_quran_btn") {
            ReadQuranButton(
                onClick  = { nav.navigate(Route.quranReader()) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ── Surah list ────────────────────────────────────────────────────────
        itemsIndexed(surahs, key = { _, surah -> surah.num }) { index, surah ->
            SurahRow(
                surah   = surah,
                onClick = { nav.navigate(Route.quranReader(suraNum = surah.num)) },
            )
            if (index < surahs.lastIndex) {
                HorizontalDivider(
                    modifier  = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun ReadQuranButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick  = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Outlined.MenuBook,
                contentDescription = null,
            )
            Text(
                text  = stringResource(R.string.index_read_quran),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun SurahRow(surah: SurahInfo, onClick: () -> Unit) {
    ListItem(
        modifier          = Modifier.clickable(onClick = onClick),
        headlineContent   = {
            Text(
                text  = surah.name,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingContent    = {
            Text(
                text  = toArNums(surah.num),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent   = {
            Text(
                text  = stringResource(R.string.index_aya_count, surah.ayaCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ── Utilities ─────────────────────────────────────────────────────────────────

private fun toArNums(n: Int): String =
    n.toString().map { "٠١٢٣٤٥٦٧٨٩"[it - '0'] }.joinToString("")