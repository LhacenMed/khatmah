package com.lhacenmed.khatmah.feature.mushaf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.feature.mushaf.data.*

@Composable
fun PrintSelectPage() {
    val vm: PrintSelectViewModel = viewModel()
    val selected by vm.selected.collectAsState()
    val states   by vm.downloadStates.collectAsState()
    val nav      = LocalNavController.current

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.mushaf_print_title),
                isTopLevel = false,
                onBack     = { nav.navigateUp() },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            Riwaya.entries.forEach { riwaya ->
                val prints = MushafRegistry.byRiwaya(riwaya)
                if (prints.isEmpty()) return@forEach

                item(key = "header_${riwaya.name}") {
                    PreferenceSubtitle(text = stringResource(riwaya.nameRes))
                }

                items(prints, key = { it.id }) { print ->
                    val state = states[print.id]
                        ?: if (print.requiresDownload) PrintDownloadState.NotDownloaded
                        else PrintDownloadState.NotRequired
                    PrintCard(
                        print      = print,
                        state      = state,
                        isSelected = print == selected,
                        onSelect   = { vm.select(print) },
                        onDownload = { vm.download(print) },
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ── Print card ────────────────────────────────────────────────────────────────

@Composable
private fun PrintCard(
    print: MushafPrint,
    state: PrintDownloadState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAvailable  = state is PrintDownloadState.NotRequired || state is PrintDownloadState.Downloaded
    val borderColor  = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth  = if (isSelected) 2.dp else 1.dp
    val contentAlpha = if (isAvailable || state is PrintDownloadState.Connecting ||
        state is PrintDownloadState.Downloading) 1f else 0.6f

    OutlinedCard(
        onClick  = { if (isAvailable) onSelect() },
        enabled  = isAvailable,
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(borderWidth, borderColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ── Text ──────────────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = stringResource(print.nameRes),
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onSurface
                            .copy(alpha = contentAlpha),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = stringResource(print.descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = contentAlpha),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // ── Right status ──────────────────────────────────────────────
                when (state) {
                    PrintDownloadState.NotRequired,
                    PrintDownloadState.Downloaded -> {
                        if (isSelected) {
                            Icon(
                                imageVector        = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(24.dp),
                            )
                        }
                    }
                    PrintDownloadState.NotDownloaded,
                    is PrintDownloadState.Error -> {
                        OutlinedButton(
                            onClick      = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier     = Modifier.height(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier           = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = stringResource(R.string.print_download),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    PrintDownloadState.Connecting -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                    is PrintDownloadState.Downloading -> {
                        CircularProgressIndicator(
                            progress    = { state.progress },
                            modifier    = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    }
                }
            }

            // ── Error message ─────────────────────────────────────────────────
            if (state is PrintDownloadState.Error) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = state.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}