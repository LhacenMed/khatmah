package com.lhacenmed.khatmah.feature.mushaf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.feature.mushaf.data.*

// Matches Material3 shapes.medium corner radius used by Card.
private val CARD_CORNER_RADIUS = 12.dp
// How far the outline peeks out from behind the card when selected.
private val OUTLINE_MAX_INSET  = 5.dp

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
        LazyVerticalGrid(
            columns               = GridCells.Adaptive(240.dp),
            modifier              = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding        = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
        ) {
            Riwaya.entries.forEachIndexed { idx, riwaya ->
                val prints = MushafRegistry.byRiwaya(riwaya)
                if (prints.isEmpty()) return@forEachIndexed

                item(
                    key  = "header_${riwaya.name}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    RiwayaHeader(
                        text     = stringResource(riwaya.nameRes),
                        modifier = Modifier.padding(top = if (idx == 0) 8.dp else 20.dp),
                    )
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
                    )
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun RiwayaHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = text.uppercase(),
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
            modifier   = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        HorizontalDivider(
            color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            thickness = 2.dp,
            modifier  = Modifier.padding(top = 4.dp, bottom = 4.dp),
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
    val isInProgress = state is PrintDownloadState.Downloading || state == PrintDownloadState.Connecting

    // Outline layer: animates from 0dp inset (hidden behind card) → OUTLINE_MAX_INSET (peeks out).
    val outlineInset by animateDpAsState(
        targetValue   = if (isSelected) OUTLINE_MAX_INSET else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium,
        ),
        label = "outlineInset",
    )

    // Badge: pops in/out with a bouncy scale.
    val badgeScale by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "badgeScale",
    )

    val outlineColor = MaterialTheme.colorScheme.primary

    Card(
        onClick  = { if (isAvailable && !isSelected) onSelect() },
        enabled  = isAvailable && !isSelected,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Draw a rounded rect that extends outlineInset beyond the card on all sides.
                // When inset == 0 it's exactly card-sized and hidden behind it; as it grows
                // it becomes a visible colored outline frame.
                val insetPx  = outlineInset.toPx()
                val cornerPx = CARD_CORNER_RADIUS.toPx() + insetPx
                drawRoundRect(
                    color        = outlineColor,
                    topLeft      = Offset(-insetPx, -insetPx),
                    size         = Size(size.width + insetPx * 2, size.height + insetPx * 2),
                    cornerRadius = CornerRadius(cornerPx),
                )
            },
        shape  = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // ── Square preview ─────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier           = Modifier.size(52.dp),
                )

                // Subtle primary tint when selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                    )
                }

                // Check badge — scales in/out with a spring
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                        .graphicsLayer { scaleX = badgeScale; scaleY = badgeScale }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Check,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.size(14.dp),
                    )
                }
            }

            // ── Card body ─────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text       = stringResource(print.nameRes),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(print.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // ── Action / progress area ─────────────────────────────────────
                when {
                    isSelected -> ActiveRow()

                    isAvailable -> {
                        OutlinedButton(
                            onClick        = onSelect,
                            shape          = MaterialTheme.shapes.extraLarge,
                            modifier       = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text  = stringResource(R.string.print_select),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    state == PrintDownloadState.Connecting -> {
                        Box(
                            modifier         = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }

                    state is PrintDownloadState.Downloading -> DownloadingProgress(state)

                    else -> {
                        // NotDownloaded or Error
                        Button(
                            onClick        = onDownload,
                            shape          = MaterialTheme.shapes.extraLarge,
                            modifier       = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.CloudDownload,
                                contentDescription = null,
                                modifier           = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text  = stringResource(R.string.print_download),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // ── Log box ────────────────────────────────────────────────────
                AnimatedVisibility(
                    visible = isInProgress,
                    enter   = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit    = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    val logLine = when (val s = state) {
                        PrintDownloadState.Connecting     -> stringResource(R.string.print_connecting)
                        is PrintDownloadState.Downloading -> s.log.ifBlank { stringResource(R.string.print_processing) }
                        else                              -> ""
                    }
                    Surface(
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        shape    = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Text(
                            text     = "• $logLine",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }

                // ── Error message ──────────────────────────────────────────────
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
}

// ── Shared sub-composables ────────────────────────────────────────────────────

@Composable
private fun ActiveRow() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier           = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text  = stringResource(R.string.print_active),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun DownloadingProgress(state: PrintDownloadState.Downloading) {
    val pct = state.progress
    Column {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = stringResource(R.string.print_downloading),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (pct != null) {
                Text(
                    text       = "${(pct * 100).toInt()}%",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (pct != null) {
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
    }
}