package com.lhacenmed.khatmah.feature.today

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.NavTab
import com.lhacenmed.khatmah.core.nav.Route
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrefs
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.today.components.*

val TodayTab = NavTab(
    route    = Route.TODAY,
    iconRes  = R.drawable.ic_book,
    labelRes = R.string.today,
) { padding -> TodayScreen(padding) }

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
private fun TodayScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val nav     = LocalNavController.current
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory(context))
    val state       by vm.state.collectAsState()
    val mushaf      by MushafPrefs.selected.collectAsState()
    var showDlDialog by remember { mutableStateOf(false) }

    if (showDlDialog) {
        MushafDownloadDialog(
            onSettings = { showDlDialog = false; nav.navigate(Route.MUSHAF_PRINTS) },
            onDismiss  = { showDlDialog = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // contentKey on session id ensures Compose always detects Active→Active changes.
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
                            targetState  is TodayViewModel.UiState.Loading ->
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))

                    initialState is TodayViewModel.UiState.Active &&
                            targetState  is TodayViewModel.UiState.Active ->
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
                is TodayViewModel.UiState.NoKhatmah -> NoKhatmahCard { nav.navigate(Route.NEW_KHATMAH) }
                is TodayViewModel.UiState.AllRead   -> AllReadCard(
                    onDua        = { /* TODO: navigate to dua */ },
                    onNewKhatmah = { nav.navigate(Route.NEW_KHATMAH) },
                )
                is TodayViewModel.UiState.Active    -> SessionCard(
                    state      = s,
                    onMarkRead = { vm.markRead(s.session.entity.id) },
                    onRead     = {
                        if (mushaf == MushafPrint.WarshText || mushaf == MushafPrint.HafsText) {
                            showDlDialog = true
                        } else {
                            nav.navigate(
                                Route.quranSessionReader(
                                    s.session.entity.startPage,
                                    s.session.entity.endPage,
                                )
                            )
                        }
                    },
                )
            }
        }

        // Bottom strip — full progress when all read, real stats when active, skeleton when loading.
        AnimatedContent(
            targetState  = state,
            contentKey   = { s -> s::class },
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "stats_strip",
        ) { s ->
            when (s) {
                is TodayViewModel.UiState.Active  ->
                    KhatmahStats(readCount = s.readCount, totalCount = s.khatmah.totalDays)
                is TodayViewModel.UiState.AllRead ->
                    KhatmahStats(readCount = s.totalDays, totalCount = s.totalDays)
                is TodayViewModel.UiState.Loading -> SkeletonStats()
                else                              -> Unit
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
            TextButton(onClick = onDismiss)  { Text(stringResource(R.string.today_cancel)) }
        },
    )
}