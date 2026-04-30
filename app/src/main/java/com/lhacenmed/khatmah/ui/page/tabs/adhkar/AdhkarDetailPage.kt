package com.lhacenmed.khatmah.ui.page.tabs.adhkar

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.data.adhkar.Dhikr
import com.lhacenmed.khatmah.data.adhkar.DhikrParagraph
import com.lhacenmed.khatmah.ui.common.Route
import com.lhacenmed.khatmah.ui.nav.LocalNavController
import com.lhacenmed.khatmah.ui.theme.WarshFamily
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

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
 * Page count = adhkar.size + 1; the final page is a completion slide that shows
 * once every dhikr in the category has been read.
 *
 * Key design decisions:
 *  • [repCounts] is pre-filled with 0 for every page so there is never a null→value
 *    transition that could cause a spurious arc animation on first composition.
 *  • Arc uses [Animatable] rather than [animateFloatAsState]: [snapTo] on page
 *    change ensures the arc resets instantly (no flash of the previous page's
 *    progress), and [animateTo] fires only on actual count increments.
 *  • Progress bar target = page / adhkar.size, so it starts at 0 and reaches 1
 *    only on the completion page — the bar grows as each dhikr is finished.
 *  • [DhikrBottomBar] is placed in Scaffold's bottomBar slot so Material3 extends
 *    its Surface background behind the navigation bar automatically (edge-to-edge).
 *  • The [RepCircle] is always composed; alpha = 0 when repetitions == 1, keeping
 *    the bottom-bar height stable across all dhikr pages.
 *  • [state.version] is included in the dhikr [LaunchedEffect] key so that after
 *    an edit or reset in [AdhkarEditorPage], the detail page automatically
 *    re-fetches the updated dhikr list when the user returns.
 */
@Composable
fun AdhkarDetailPage(categoryId: String) {
    val nav      = LocalNavController.current
    val context  = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity

    val vm: AdhkarViewModel = viewModel(activity)
    val state by vm.uiState.collectAsState()

    // Category title — sourced from the live ViewModel state
    val category = remember(categoryId, state.categories) {
        state.categories.find { it.id == categoryId }
    }
    // Dhikr list — re-fetched whenever the VM version increments (post-edit/reset)
    var adhkar by remember { mutableStateOf<List<Dhikr>>(emptyList()) }
    var isDhikrLoading by remember { mutableStateOf(true) }

    // Session counts survive configuration changes via the activity-scoped VM.
    // startSession resets counts when the category or its content changes.
    val session by vm.session.collectAsState()

    LaunchedEffect(categoryId, state.version) {
        adhkar = vm.getDhikrForCategory(categoryId)
        vm.startSession(categoryId)
        isDhikrLoading = false
    }

    // Empty / not-found guard
    if (!isDhikrLoading && adhkar.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    if (isDhikrLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // categoryName now comes from the DB-backed model
    val categoryName = category?.title.orEmpty()

    val totalPages = adhkar.size + 1        // last page is the completion slide
    val pagerState = rememberPagerState { totalPages }
    val scope      = rememberCoroutineScope()

    // Font size persists across configuration changes but resets on new process.
    var fontSize by rememberSaveable { mutableStateOf(DhikrFontSize.MEDIUM) }

    val page             = pagerState.currentPage
    val isCompletionPage = page >= adhkar.size
    val dhikr            = if (!isCompletionPage) adhkar[page] else null
    val repCount         = session.count
    // allDone only for single-rep and completion page — multi-rep auto-navigates on last tap.
    val allDone = isCompletionPage || dhikr == null || dhikr.repetitions <= 1

    // On every page entry: snap arc to 0 (no animated carry-over from prior page)
    // and reset that page's counter so revisiting always starts fresh.
    val arcAnim = remember { Animatable(0f) }
    // Guards LaunchedEffect(page) from snapping arc to 0 mid last-rep animation.
    var isAnimatingLastRep by remember { mutableStateOf(false) }
    // Snap arc instantly to this page's existing progress when navigating.
    LaunchedEffect(page) {
        vm.resetCount()
        if (!isAnimatingLastRep) arcAnim.snapTo(0f)
    }

    // Progress bar: 0 at the first dhikr, grows by 1/N per completed dhikr,
    // reaches 1.0 only on the completion page.
    val barFraction by animateFloatAsState(
        targetValue   = if (isCompletionPage) 1f else page.toFloat() / adhkar.size,
        animationSpec = tween(300),
        label         = "dhikr_bar",
    )

    // ── Actions ───────────────────────────────────────────────────────────────

    fun goNext() = scope.launch {
        if (page < totalPages - 1) pagerState.animateScrollToPage(page + 1)
        else nav.popBackStack()
    }

    fun handleTap() {
        if (isCompletionPage) return
        val reps = dhikr?.repetitions ?: 1
        if (reps <= 1) { goNext(); return }
        val newCount = repCount + 1
        val isLast   = newCount >= reps
        vm.recordRead()
        if (isLast) {
            isAnimatingLastRep = true
            scope.launch {
                arcAnim.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
                arcAnim.snapTo(0f)
                isAnimatingLastRep = false
            }
            goNext()
        } else {
            scope.launch {
                arcAnim.animateTo(
                    targetValue   = newCount.toFloat() / reps,
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    fun share() {
        dhikr ?: return
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, dhikr.shareText)
                },
                null,
            )
        )
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            DhikrTopBar(
                title    = categoryName,
                onBack   = { nav.popBackStack() },
                onEdit   = { nav.navigate(Route.adhkarEditor(categoryId)) },
                onResize = { fontSize = fontSize.next() },
            )
        },
        // bottomBar slot: M3 Scaffold places this at the physical screen bottom
        // and extends its background behind the navigation bar (edge-to-edge).
        bottomBar = {
            DhikrBottomBar(
                dhikr            = dhikr,
                repCount         = repCount,
                arcFraction      = arcAnim.value,
                allDone          = allDone,
                isCompletionPage = isCompletionPage,
                onBack           = { nav.popBackStack() },
                onShare          = ::share,
                onAction         = ::handleTap,
                onTap            = ::handleTap,
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
                // Cap at adhkar.size so the counter reads "N/N" on the completion page.
                current  = minOf(page + 1, adhkar.size),
                total    = adhkar.size,
                fraction = barFraction,
                onClick  = ::handleTap,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication        = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { handleTap() },
            ) {
                // All pages stay composed; Compose pager manages lifecycle automatically.
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    if (i < adhkar.size) {
                        DhikrBody(dhikr = adhkar[i], fontSize = fontSize)
                    } else {
                        CompletionBody(categoryName = categoryName)
                    }
                }

                // Gradient scrim: fades body content into the bottom bar's surface color,
                // removing the hard visual cut between the scroll area and the bottom bar.
                val scrimColor = MaterialTheme.colorScheme.surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, scrimColor),
                            )
                        )
                )
            }
        }
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DhikrTopBar(
    title:    String,
    onBack:   () -> Unit,
    onEdit:   () -> Unit,
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
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector        = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit),
                )
            }
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
 * Single-row progress strip directly below the top bar.
 *
 * Counter and [LinearProgressIndicator] share the same horizontal line:
 *   [total/current]  ──────────████████──  (bar fills the remaining width)
 *
 * [surfaceContainer] background visually separates this strip from the
 * main reading area below it.
 */
@Composable
private fun DhikrProgressHeader(
    current: Int,
    total: Int,
    fraction: Float,
    onClick: () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick           = onClick,
            )
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "$total/$current",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress   = { fraction },
            modifier   = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
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
 *  [DhikrParagraph.Quran] — Quranic verse rendered in [WarshFamily], slightly
 *                           larger, preserving in-text glyph markers (①②… etc.) as Unicode.
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
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 80.dp),
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
                    fontFamily = WarshFamily,
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

// ── Completion body ───────────────────────────────────────────────────────────

/**
 * Final slide shown after all adhkar have been read.
 * No count label, no circular counter, no share button — just a confirmation
 * message with the category name.
 */
@Composable
private fun CompletionBody(categoryName: String) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(72.dp),
            )
            Text(
                text       = stringResource(R.string.adhkar_completed, categoryName),
                style      = MaterialTheme.typography.headlineSmall,
                textAlign  = TextAlign.Center,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ── Bottom bar ────────────────────────────────────────────────────────────────

/**
 * Sticky bottom bar placed in Scaffold's bottomBar slot so M3 extends its
 * Surface background behind the navigation bar (edge-to-edge).
 *
 * The soft fade between the scrollable body and this bar is handled externally
 * by a gradient scrim Box positioned at the bottom of the content area, so
 * this Surface presents no hard visual border at its top edge.
 *
 * Regular dhikr page:
 *  • Rep label at logical-start, circle at absolute screen center, share at logical-end.
 *    Centering is achieved with a [Box] overlay (CenterStart / Center / CenterEnd)
 *    so the circle position is independent of label and button widths.
 *  • [RepCircle] is always composed — alpha = 0 when repetitions == 1 — preventing
 *    any height shift as the user moves between dhikr with different rep counts.
 *
 * Completion page:
 *  • Rep row is hidden entirely.
 */
@Composable
private fun DhikrBottomBar(
    dhikr: Dhikr?,
    repCount: Int,
    arcFraction: Float,
    allDone: Boolean,
    isCompletionPage: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onAction: () -> Unit,
    onTap: () -> Unit,
) {
    Surface(
        shadowElevation = 8.dp,
        color           = MaterialTheme.colorScheme.surface,
        modifier        = Modifier.clickable(
            indication        = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick           = onTap,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Rep row ───────────────────────────────────────────────────
            // Box overlay: rep label pinned to CenterStart, circle to Center,
            // share button to CenterEnd — the circle is always at the exact
            // horizontal midpoint of the screen regardless of sibling widths.
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = if (isCompletionPage) 0f else 1f
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Repetition label — logical start (physical right in RTL).
                Text(
                    text     = repLabel(dhikr?.repetitions ?: 1),
                    style    = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                // Animated arc — always present; invisible when rep count is 1
                // so the bar height never changes between dhikr pages.
                RepCircle(
                    fraction = arcFraction,
                    count    = repCount,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = if ((dhikr?.repetitions ?: 1) > 1) 1f else 0f
                        },
                )

                // Share button — logical end (physical left in RTL).
                IconButton(
                    onClick  = onShare,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.dhikr_share),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Primary action ────────────────────────────────────────────────
            Button(
                onClick  = when {
                    isCompletionPage -> onBack
                    else             -> onAction
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(45.dp),
                shape    = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text       = stringResource(
                        when {
                            isCompletionPage -> R.string.dhikr_done
                            allDone          -> R.string.dhikr_next
                            else             -> R.string.dhikr_read
                        }
                    ),
                    style      = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
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
 * [fraction] is a pre-animated value supplied by the caller; this composable
 * is purely visual and performs no animation logic of its own.
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
        modifier         = modifier.size(70.dp),
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
            text  = count.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = textColor,
        )
    }
}