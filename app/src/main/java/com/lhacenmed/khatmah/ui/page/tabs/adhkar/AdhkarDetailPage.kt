package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.adhkar.AdhkarData
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.data.adhkar.DhikrParagraph
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import kotlinx.coroutines.launch

// ── Font size cycle ───────────────────────────────────────────────────────────

/**
 * Three-step reading font size that cycles on each tap of the resize button.
 * [bodyScale] scales general text; [quranScale] scales Quranic verse text,
 * which starts slightly larger to preserve the traditional Uthmanic appearance.
 */
private enum class DhikrFontSize(val bodyScale: Float, val quranScale: Float) {
    SMALL(0.82f, 0.88f),
    MEDIUM(1f,   1f),
    LARGE(1.22f, 1.16f),
}

private fun DhikrFontSize.next() = when (this) {
    DhikrFontSize.SMALL  -> DhikrFontSize.MEDIUM
    DhikrFontSize.MEDIUM -> DhikrFontSize.LARGE
    DhikrFontSize.LARGE  -> DhikrFontSize.SMALL
}

// ── Repetition label ──────────────────────────────────────────────────────────

/**
 * Maps [count] to a human-readable repetition string drawn from string resources
 * so that Arabic and English values are resolved by the active locale automatically.
 */
@Composable
private fun repLabel(count: Int): String = when (count) {
    1    -> stringResource(R.string.rep_once)
    2    -> stringResource(R.string.rep_twice)
    3    -> stringResource(R.string.rep_three)
    7    -> stringResource(R.string.rep_seven)
    10   -> stringResource(R.string.rep_ten)
    33   -> stringResource(R.string.rep_thirty_three)
    100  -> stringResource(R.string.rep_hundred)
    else -> stringResource(R.string.rep_n_times, count)
}

// ── Root composable ───────────────────────────────────────────────────────────

/**
 * Full-screen dhikr reader for a single [AdhkarCategory].
 *
 * Replaces the stub in [AdhkarDetailPage] and wires up:
 *  • Swipeable [HorizontalPager] — one page per [Dhikr] in the category.
 *  • Animated linear progress bar and position counter at the top.
 *  • Styled body that differentiates [DhikrParagraph] types visually.
 *  • Bottom bar with a repetition counter circle, rep-count label, share
 *    action, and a primary button that counts reads or advances to the next dhikr.
 */
@Composable
fun AdhkarDetailPage(categoryId: String) {
    val nav     = LocalNavController.current
    val context = LocalContext.current

    val category = remember(categoryId) { adhkarCategories.find { it.id == categoryId } }
    val adhkar   = remember(categoryId) { AdhkarData.forCategory(categoryId) }

    // Nothing to show — navigate back immediately.
    if (adhkar.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    val pagerState = rememberPagerState { adhkar.size }
    val scope      = rememberCoroutineScope()

    // Font size persists across configuration changes but resets on new process.
    var fontSize by rememberSaveable { mutableStateOf(DhikrFontSize.MEDIUM) }

    // Per-page repetition counts — keyed by page index, survive recomposition.
    val repCounts: SnapshotStateMap<Int, Int> = remember { mutableStateMapOf() }

    val page     = pagerState.currentPage
    val dhikr    = adhkar[page]
    val repCount = repCounts[page] ?: 0
    // A dhikr with repetitions == 1 never shows a counter; its button is always "Next".
    val allDone  = dhikr.repetitions <= 1 || repCount >= dhikr.repetitions

    // ── Animations ────────────────────────────────────────────────────────────

    val barFraction by animateFloatAsState(
        targetValue  = (page + 1).toFloat() / adhkar.size,
        animationSpec = tween(300),
        label        = "dhikr_bar",
    )
    val arcFraction by animateFloatAsState(
        targetValue  = if (dhikr.repetitions <= 1) 1f
        else repCount.toFloat() / dhikr.repetitions,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label        = "dhikr_arc",
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    fun goNext() = scope.launch {
        if (page < adhkar.size - 1) pagerState.animateScrollToPage(page + 1)
        else nav.popBackStack()
    }

    fun countRead() {
        if (repCount < dhikr.repetitions) repCounts[page] = repCount + 1
    }

    fun share() = context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, dhikr.shareText)
            },
            null,
        )
    )

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            DhikrTopBar(
                title    = category?.let { stringResource(it.titleRes) }.orEmpty(),
                onBack   = { nav.popBackStack() },
                onResize = { fontSize = fontSize.next() },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DhikrProgressHeader(
                current  = page + 1,
                total    = adhkar.size,
                fraction = barFraction,
            )

            // All pages stay composed; Compose pager manages lifecycle automatically.
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f),
            ) { i ->
                DhikrBody(dhikr = adhkar[i], fontSize = fontSize)
            }

            DhikrBottomBar(
                dhikr       = dhikr,
                repCount    = repCount,
                arcFraction = arcFraction,
                allDone     = allDone,
                onShare     = ::share,
                onAction    = { if (allDone) goNext() else countRead() },
            )
        }
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DhikrTopBar(
    title: String,
    onBack: () -> Unit,
    onResize: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_up),
                )
            }
        },
        actions = {
            IconButton(onClick = onResize) {
                Icon(
                    imageVector        = Icons.Outlined.FormatSize,
                    contentDescription = stringResource(R.string.dhikr_font_size),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor             = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor          = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor     = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ── Progress header ───────────────────────────────────────────────────────────

/**
 * Thin progress strip directly below the top bar.
 *
 * Counter format: "[total]/[current]" — matches the design in the screenshots
 * (e.g. "30/3" = 30 adhkar total, currently viewing #3).
 * [LinearProgressIndicator] fills as the user advances through the list.
 */
@Composable
private fun DhikrProgressHeader(
    current: Int,
    total: Int,
    fraction: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text  = "$total/$current",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = { fraction },
            modifier   = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color      = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ── Dhikr body ────────────────────────────────────────────────────────────────

/**
 * Scrollable content area for one [Dhikr].
 *
 * Paragraph rendering by type:
 *  [DhikrParagraph.Body]  — standard body text, centered.
 *  [DhikrParagraph.Quran] — Quranic verse, slightly larger, preserving
 *                           in-text glyph markers (①②… etc.) as Unicode.
 *  [DhikrParagraph.Note]  — small muted footnote in primary tint.
 *
 * Line heights are proportional to font size to keep Arabic diacritics legible
 * at all three [DhikrFontSize] steps.
 */
@Composable
private fun DhikrBody(dhikr: Dhikr, fontSize: DhikrFontSize) {
    val bodySp  = (20f * fontSize.bodyScale).sp
    val quranSp = (23f * fontSize.quranScale).sp
    val noteSp  = (14f * fontSize.bodyScale).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        dhikr.paragraphs.forEachIndexed { i, paragraph ->
            if (i > 0) Spacer(Modifier.height(22.dp))
            when (paragraph) {
                is DhikrParagraph.Body -> Text(
                    text       = paragraph.text,
                    fontSize   = bodySp,
                    lineHeight = (bodySp.value * 1.85f).sp,
                    textAlign  = TextAlign.Center,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Normal,
                )
                is DhikrParagraph.Quran -> Text(
                    text       = paragraph.text,
                    fontSize   = quranSp,
                    lineHeight = (quranSp.value * 2.0f).sp,
                    textAlign  = TextAlign.Center,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Normal,
                )
                is DhikrParagraph.Note -> Text(
                    text       = paragraph.text,
                    fontSize   = noteSp,
                    lineHeight = (noteSp.value * 1.65f).sp,
                    textAlign  = TextAlign.Center,
                    color      = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

/**
 * Sticky bottom bar with three elements:
 *
 *  1. Repetition count label (logical start — physical right in RTL).
 *  2. Circular progress counter (only when [dhikr.repetitions] > 1, centered).
 *  3. Share icon (logical end — physical left in RTL).
 *  4. Full-width primary action button.
 *
 * The layout is direction-aware: Compose resolves "start" and "end" to the
 * correct physical side for both LTR and RTL locales automatically.
 *
 * Button label alternates between [R.string.dhikr_read] (counting mode) and
 * [R.string.dhikr_next] (advance mode) based on [allDone].
 */
@Composable
private fun DhikrBottomBar(
    dhikr: Dhikr,
    repCount: Int,
    arcFraction: Float,
    allDone: Boolean,
    onShare: () -> Unit,
    onAction: () -> Unit,
) {
    val showCircle = dhikr.repetitions > 1

    Surface(
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Rep row ───────────────────────────────────────────────────────
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Logical start (physical right in RTL): repetition label
                Text(
                    text       = repLabel(dhikr.repetitions),
                    style      = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color      = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.weight(1f))

                // Centre: animated arc counter (multi-rep dhikr only)
                if (showCircle) {
                    RepCircle(
                        fraction = arcFraction,
                        count    = repCount,
                    )
                    Spacer(Modifier.weight(1f))
                }

                // Logical end (physical left in RTL): share button
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector        = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.dhikr_share),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Primary action ────────────────────────────────────────────────
            Button(
                onClick  = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape    = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text       = stringResource(
                        if (allDone) R.string.dhikr_next else R.string.dhikr_read,
                    ),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Circular repetition counter ───────────────────────────────────────────────

/**
 * Animated arc drawn with [Canvas].
 *
 * The arc starts at the 12-o'clock position (-90°) and sweeps clockwise.
 * [fraction] drives the sweep angle (0f → empty, 1f → full circle) and is
 * expected to be a pre-animated value supplied by the caller.
 * [count] is the completed read count displayed in the centre.
 */
@Composable
private fun RepCircle(
    fraction: Float,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val primary   = MaterialTheme.colorScheme.primary
    val track     = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier         = modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke   = 5.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft  = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize  = Size(diameter, diameter)

            // Background track (full circle)
            drawArc(
                color      = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress arc
            if (fraction > 0f) {
                drawArc(
                    color      = primary,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text       = count.toString(),
            style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color      = textColor,
        )
    }
}