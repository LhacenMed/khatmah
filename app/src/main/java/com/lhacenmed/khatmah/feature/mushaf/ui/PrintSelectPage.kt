package com.lhacenmed.khatmah.feature.mushaf.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Bundle
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.BaseComposeActivity
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.PreferenceSubtitle
import com.lhacenmed.khatmah.feature.mushaf.data.MushafPrint
import com.lhacenmed.khatmah.feature.mushaf.data.MushafRegistry
import com.lhacenmed.khatmah.feature.mushaf.data.PrintDownloadState
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya

// ── Button state key ──────────────────────────────────────────────────────────

/**
 * Drives [ActionButton]'s [AnimatedContent] — keyed by state *type* only,
 * so progress-percentage recompositions update the text in place without
 * re-triggering the slide/fade transition.
 */
private enum class BtnKey { SELECTED, SELECT, DOWNLOAD, INACTIVE, PROGRESS }

private fun PrintDownloadState.toBtnKey(isSelected: Boolean): BtnKey = when {
    (this is PrintDownloadState.NotRequired || this is PrintDownloadState.Downloaded) && isSelected  -> BtnKey.SELECTED
    (this is PrintDownloadState.NotRequired || this is PrintDownloadState.Downloaded) && !isSelected -> BtnKey.SELECT
    this is PrintDownloadState.NotDownloaded || this is PrintDownloadState.Error                      -> BtnKey.DOWNLOAD
    this == PrintDownloadState.Connecting                                                              -> BtnKey.INACTIVE
    this is PrintDownloadState.Downloading -> {
        // If progress is null (processing) or 100%, show the spinner (INACTIVE).
        if (progress == null || progress >= 1f) BtnKey.INACTIVE else BtnKey.PROGRESS
    }
    else                                                                                              -> BtnKey.DOWNLOAD
}

// ── Page ──────────────────────────────────────────────────────────────────────

@Composable
fun PrintSelectScreen() {
    val vm: PrintSelectViewModel = viewModel()
    val selected by vm.selected.collectAsState()
    val states   by vm.downloadStates.collectAsState()
    val nav      = LocalNavigator.current

    Scaffold(
        topBar = {
            AppTopBar(
                title      = stringResource(R.string.mushaf_print_title),
                isTopLevel = false,
                onBack     = { nav.back() },
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
                        onCancel   = { vm.cancelDownload(print) },
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
    print:      MushafPrint,
    state:      PrintDownloadState,
    isSelected: Boolean,
    onSelect:   () -> Unit,
    onDownload: () -> Unit,
    onCancel:   () -> Unit,
    modifier:   Modifier = Modifier,
) {
    val isInProgress = state is PrintDownloadState.Downloading || state == PrintDownloadState.Connecting

    var shelfHeightPx    by remember { mutableIntStateOf(0) }
    var showCancelDialog by remember { mutableStateOf(false) }

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
            onCancel   = { showCancelDialog = true },
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

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title   = { Text(stringResource(R.string.print_cancel_download_title)) },
            text    = { Text(stringResource(R.string.print_cancel_download_msg)) },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text(stringResource(R.string.print_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
}

// ── Print card ────────────────────────────────────────────────────────────────

@Composable
private fun PrintCard(
    print:      MushafPrint,
    state:      PrintDownloadState,
    isSelected: Boolean,
    onSelect:   () -> Unit,
    onDownload: () -> Unit,
    onCancel:   () -> Unit,
    modifier:   Modifier = Modifier,
) {
    val isAvailable  = state is PrintDownloadState.NotRequired || state is PrintDownloadState.Downloaded
    val isActive     = state is PrintDownloadState.Downloading || state == PrintDownloadState.Connecting
    val borderColor  = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth  = if (isSelected) 2.dp else 1.dp
    val contentAlpha = if (isAvailable || isActive) 1f else 0.6f

    val showProgress     = state == PrintDownloadState.Connecting || state is PrintDownloadState.Downloading
    val downloadProgress = (state as? PrintDownloadState.Downloading)?.progress

    OutlinedCard(
        onClick   = { if (isAvailable && !isSelected) onSelect() },
        enabled   = isAvailable && !isSelected,
        modifier  = modifier.fillMaxWidth(),
        border    = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness    = Spring.StiffnessMediumLow,
                    )
                )
        ) {
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

                // ── Action button ─────────────────────────────────────────────
                ActionButton(
                    state      = state,
                    isSelected = isSelected,
                    onSelect   = onSelect,
                    onDownload = onDownload,
                    onCancel   = onCancel,
                )
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

        // ── Bottom progress bar ───────────────────────────────────────────────
        // OutlinedCard's Surface clips children to its shape, so the bar's
        // bottom corners follow the card's rounded corners automatically.
        AnimatedVisibility(
            visible = showProgress,
            enter   = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            if (downloadProgress != null) {
                LinearProgressIndicator(
                    progress   = { downloadProgress },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color      = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ── Action button ─────────────────────────────────────────────────────────────

/**
 * Outlined container button whose **inner content** (icon / text / both) slides
 * in from top + fades in on enter, and slides down + fades out on exit.
 * The button border and shape remain stable — [AnimatedContent] is placed
 * *inside* the button so only the content transitions, not the container.
 *
 * Two transition modes, chosen per state pair:
 *  • Non-DOWNLOAD pairs: button is at min-width in both states — [sizeTransform] is
 *    null so the AnimatedContent box never shifts width during the slide, keeping
 *    each item's center fixed → pure vertical motion, zero diagonal drift.
 *  • DOWNLOAD pairs: button width changes significantly — [SizeTransform] animates
 *    it smoothly, but the slide is replaced with a cross-fade so there is no
 *    vertical component to compound with the horizontal size change.
 *
 * Keyed by [BtnKey] (state type), so progress-percentage recompositions
 * update the inner [Text] without re-firing the transition.
 *
 * [BtnKey.INACTIVE] and [BtnKey.PROGRESS] are clickable and call [onCancel],
 * which surfaces a confirmation dialog one level up.
 */
@Composable
private fun ActionButton(
    state:      PrintDownloadState,
    isSelected: Boolean,
    onSelect:   () -> Unit,
    onDownload: () -> Unit,
    onCancel:   () -> Unit,
    modifier:   Modifier = Modifier,
) {
    val key          = state.toBtnKey(isSelected)
    val primary      = MaterialTheme.colorScheme.primary
    val outlineVar   = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

    // INACTIVE and PROGRESS are now enabled so the user can tap to cancel.
    val isClickable = key == BtnKey.SELECT || key == BtnKey.DOWNLOAD
            || key == BtnKey.INACTIVE || key == BtnKey.PROGRESS

    val borderColor = if (key == BtnKey.SELECTED) primary else outlineVar

    // Content color for the enabled state; disabled color covers SELECTED's checkmark.
    val contentColor = when (key) {
        BtnKey.INACTIVE, BtnKey.PROGRESS -> onSurfaceVar
        else                             -> primary
    }

    OutlinedButton(
        onClick        = when (key) {
            BtnKey.SELECT             -> onSelect
            BtnKey.DOWNLOAD           -> onDownload
            BtnKey.INACTIVE,
            BtnKey.PROGRESS           -> onCancel
            else                      -> ({})
        },
        enabled        = isClickable,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier       = modifier.height(32.dp),
        border         = BorderStroke(1.dp, borderColor),
        colors         = ButtonDefaults.outlinedButtonColors(
            contentColor           = contentColor,
            disabledContainerColor = Color.Transparent,
            // SELECTED (disabled) keeps the checkmark in primary; others use onSurface.
            disabledContentColor   = if (key == BtnKey.SELECTED) primary
            else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        // AnimatedContent is *inside* the button — only the content transitions,
        // the button container (border, shape) stays completely stable.
        AnimatedContent(
            targetState      = key,
            contentAlignment = Alignment.Center,
            transitionSpec   = {
                // DOWNLOAD pairs change the button width — animate it via SizeTransform
                // and cross-fade (no slide) so there is no diagonal from combining
                // a vertical slide with a shifting horizontal center.
                // All other pairs are at the same min-width — slide purely in Y with
                // no size animation so the box center never moves during the transition.
                val isDlTransition = (initialState == BtnKey.DOWNLOAD) != (targetState == BtnKey.DOWNLOAD)
                if (isDlTransition) {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(200)),
                        initialContentExit = fadeOut(tween(160)),
                        sizeTransform      = SizeTransform(clip = true),
                    )
                } else {
                    ContentTransform(
                        targetContentEnter = slideInVertically(tween(200)) { -it } + fadeIn(tween(200)),
                        initialContentExit = slideOutVertically(tween(160)) { it } + fadeOut(tween(160)),
                        sizeTransform      = null,
                    )
                }
            },
            label = "btnContent",
        ) { target ->
            when (target) {
                BtnKey.SELECTED -> Icon(
                    imageVector        = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp),
                )
                BtnKey.SELECT   -> Text(
                    text  = stringResource(R.string.print_select),
                    style = MaterialTheme.typography.labelSmall,
                )
                BtnKey.DOWNLOAD -> Row(verticalAlignment = Alignment.CenterVertically) {
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
                BtnKey.INACTIVE -> CircularProgressIndicator(
                    modifier    = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color       = onSurfaceVar,
                )
                BtnKey.PROGRESS -> {
                    // Reads live progress from outer state — recomposes without re-animating.
                    val pct = (state as? PrintDownloadState.Downloading)?.progress ?: 0f
                    Text(
                        text  = "${(pct * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// ── Navigation destination ────────────────────────────────────────────────────

class PrintSelectActivity : BaseComposeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { PrintSelectScreen() }
    }
}