package com.lhacenmed.khatmah.feature.mushaf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.feature.mushaf.data.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.mutableIntStateOf

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
                    PrintCardWithLog(
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

// ── Card + detached log shelf ─────────────────────────────────────────────────

/**
 * Wraps [PrintCard] and a log shelf that slides out from underneath it as a unit.
 * [Modifier.layout] measures the shelf at its natural size every frame and reports
 * fraction × height to the Column, so neighboring cards shift in perfect sync.
 */
@Composable
private fun PrintCardWithLog(
    print: MushafPrint,
    state: PrintDownloadState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInProgress = state is PrintDownloadState.Downloading || state == PrintDownloadState.Connecting

    var shelfHeightPx by remember { mutableIntStateOf(0) }
    val fraction by animateFloatAsState(
        targetValue   = if (isInProgress) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "logSlide",
    )

    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrintCard(
            print      = print,
            state      = state,
            isSelected = isSelected,
            onSelect   = onSelect,
            onDownload = onDownload,
            // Card sits visually above the shelf.
            modifier   = Modifier.zIndex(1f),
        )

        // Box clips the shelf while it slides; its height is driven by the
        // layout modifier on the shelf itself, keeping neighbors in sync.
        Box(
            modifier         = Modifier
                .fillMaxWidth(0.95f)
                .clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LogShelf(
                state    = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        // Always measure at natural size — never constrained to 0.
                        val p = measurable.measure(constraints)
                        shelfHeightPx = p.height
                        // Report fraction × height so the Column shifts neighbors each frame.
                        layout(p.width, (fraction * p.height).toInt()) { p.place(0, 0) }
                    }
                    // Slide shelf as a unit from fully behind the card down to resting position.
                    .graphicsLayer { translationY = shelfHeightPx * (fraction - 1f) },
            )
        }
    }
}

// ── Log shelf ─────────────────────────────────────────────────────────────────

/**
 * Detached log panel that renders below the card with only its bottom corners
 * rounded, giving the illusion of sliding out from behind the card.
 */
@Composable
private fun LogShelf(state: PrintDownloadState, modifier: Modifier = Modifier) {
    val logText = when (val s = state) {
        PrintDownloadState.Connecting     -> stringResource(R.string.print_connecting)
        is PrintDownloadState.Downloading -> s.log.ifBlank { stringResource(R.string.print_processing) }
        else                              -> ""
    }
    Surface(
        color    = MaterialTheme.colorScheme.surfaceVariant,
        shape    = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = modifier,
    ) {
        Text(
            text     = "• $logText",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
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
    val isActive     = state is PrintDownloadState.Downloading || state == PrintDownloadState.Connecting
    val borderColor  = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth  = if (isSelected) 2.dp else 1.dp
    val contentAlpha = if (isAvailable || isActive) 1f else 0.6f

    OutlinedCard(
        onClick  = { if (isAvailable && !isSelected) onSelect() },
        enabled  = isAvailable && !isSelected,
        modifier = modifier.fillMaxWidth(),
        border   = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ── Text ──────────────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text       = stringResource(print.nameRes),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurface
                                .copy(alpha = contentAlpha),
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        IconButton(
                            onClick  = { /* TODO */ },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.Info,
                                contentDescription = null,
                                modifier           = Modifier.size(14.dp),
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = contentAlpha),
                            )
                        }
                    }
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
                        } else {
                            OutlinedButton(
                                onClick        = onSelect,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier       = Modifier.height(32.dp),
                            ) {
                                Text(
                                    text  = stringResource(R.string.print_select),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    PrintDownloadState.NotDownloaded,
                    is PrintDownloadState.Error -> {
                        OutlinedButton(
                            onClick        = onDownload,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier       = Modifier.height(32.dp),
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
                        val pct = state.progress
                        if (pct != null) {
                            // Determinate: file download in progress — show percentage.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    progress    = { pct },
                                    modifier    = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text  = "${(pct * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // Indeterminate: extraction / DB import / index rebuild.
                            CircularProgressIndicator(
                                modifier    = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
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