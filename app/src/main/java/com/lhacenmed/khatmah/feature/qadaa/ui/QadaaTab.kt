package com.lhacenmed.khatmah.feature.qadaa.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.AppTab
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.nav.LocalScrollToTop
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.feature.qadaa.data.FastDebt
import com.lhacenmed.khatmah.feature.qadaa.data.PrayerDebt
import com.lhacenmed.khatmah.feature.qadaa.ui.components.AddFastsSheet
import com.lhacenmed.khatmah.feature.qadaa.ui.components.AddPrayersSheet
import com.lhacenmed.khatmah.feature.qadaa.ui.components.MarkDoneSheet

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun QadaaScreen(outerPadding: PaddingValues = PaddingValues()) {
    val activity = LocalActivity.current as ComponentActivity
    val vm: QadaaViewModel = viewModel(activity)
    val state by vm.uiState.collectAsState()
    val nav = LocalNavController.current
    val listState = rememberLazyListState()
    val scrollToTop = LocalScrollToTop.current

    var showAddPrayers by remember { mutableStateOf(false) }
    var showAddFasts by remember { mutableStateOf(false) }
    var showMarkDone by remember { mutableStateOf(false) }
    var markDoneInitialTab by remember { mutableIntStateOf(0) }
    var markDonePreselectedFastId by remember { mutableStateOf<Long?>(null) }
    var showEditGoal by remember { mutableStateOf(false) }

    val monthsLeft = remember(state.totalPrayersRemaining, state.dailyGoal) {
        if (state.dailyGoal > 0 && state.totalPrayersRemaining > 0)
            ((state.totalPrayersRemaining.toFloat() / state.dailyGoal) / 30f).toInt().coerceAtLeast(1)
        else 0
    }

    // Scroll to top when tab is re-selected
    LaunchedEffect(scrollToTop) {
        scrollToTop.collect {
            if (listState.firstVisibleItemIndex > 4) listState.scrollToItem(4)
            listState.animateScrollToItem(0)
        }
    }

    // Show add-prayers sheet when triggered from the top bar
    LaunchedEffect(Unit) {
        vm.addPrayersEvent.collect { showAddPrayers = true }
    }

    LazyColumn(
        state               = listState,
        contentPadding      = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier            = Modifier.fillMaxSize().padding(outerPadding),
    ) {
        // ── Summary card ──────────────────────────────────────────────────
        item {
            SummaryCard(
                prayersRemaining = state.totalPrayersRemaining,
                fastsRemaining   = state.totalFastsRemaining,
                monthsLeft       = monthsLeft,
            )
        }

        // ── Today progress card ───────────────────────────────────────────
        item {
            TodayProgressCard(
                todayDone     = state.todayDone,
                dailyGoal     = state.dailyGoal,
                streak        = state.streak,
                onEditGoal    = { showEditGoal = true },
                onMarkPrayers = { markDoneInitialTab = 0; showMarkDone = true },
                onMarkFasts   = { markDoneInitialTab = 1; markDonePreselectedFastId = null; showMarkDone = true },
            )
        }

        // ── Prayers section ───────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.qadaa_section_prayers),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                )
                TextButton(onClick = { nav.navigate("qadaa_history") }) {
                    Text(stringResource(R.string.qadaa_see_all))
                }
            }
        }
        item {
            CompositionLocalProvider(LocalOverscrollFactory provides null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding        = PaddingValues(horizontal = 0.dp),
                ) {
                    items(state.prayers, key = { it.prayer.name }) { debt ->
                        PrayerDebtCard(
                            debt      = debt,
                            onMarkOne = { vm.markPrayersDone(mapOf(debt.prayer to 1)) },
                        )
                    }
                }
            }
        }

        // ── Fasts section ─────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.qadaa_section_fasts),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                )
                TextButton(onClick = { showAddFasts = true }) {
                    Text(stringResource(R.string.qadaa_add_fasts_btn))
                }
            }
        }
        if (state.fasts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.qadaa_no_fasts_recorded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.fasts, key = { it.id }) { fast ->
                FastDebtRow(
                    fast       = fast,
                    onMarkDone = {
                        markDoneInitialTab        = 1
                        markDonePreselectedFastId = fast.id
                        showMarkDone              = true
                    },
                )
            }
        }
    }

    // ── Bottom sheets ─────────────────────────────────────────────────────

    if (showAddPrayers) {
        AddPrayersSheet(
            onDismiss = { showAddPrayers = false },
            onAdd     = { counts -> vm.addPrayers(counts) },
        )
    }
    if (showAddFasts) {
        AddFastsSheet(
            onDismiss = { showAddFasts = false },
            onAdd     = { count, reason, label, year -> vm.addFasts(count, reason, label, year) },
        )
    }
    if (showMarkDone) {
        MarkDoneSheet(
            prayers           = state.prayers,
            fasts             = state.fasts,
            initialTab        = markDoneInitialTab,
            preselectedFastId = markDonePreselectedFastId,
            onDismiss         = { showMarkDone = false },
            onMarkPrayers     = { counts, fullDay -> vm.markPrayersDone(counts, fullDay = fullDay) },
            onMarkFasts       = { count, id -> vm.markFastsDone(count, id) },
        )
    }
    if (showEditGoal) {
        var newGoal by remember { mutableIntStateOf(state.dailyGoal) }
        AlertDialog(
            onDismissRequest = { showEditGoal = false },
            title            = { Text(stringResource(R.string.qadaa_set_goal_title)) },
            text             = {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = { if (newGoal > 1) newGoal-- }) { Text("-") }
                    Text("$newGoal", style = MaterialTheme.typography.headlineMedium)
                    OutlinedButton(onClick = { newGoal++ }) { Text("+") }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setDailyGoal(newGoal); showEditGoal = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGoal = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(prayersRemaining: Int, fastsRemaining: Int, monthsLeft: Int) {
    Card(
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Prayers stat
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕌", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$prayersRemaining",
                        style      = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.qadaa_prayers_remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VerticalDivider(modifier = Modifier.height(72.dp).padding(horizontal = 8.dp))
                // Fasts stat
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌙", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$fastsRemaining",
                        style      = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        stringResource(R.string.qadaa_fasts_remaining),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (monthsLeft > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    if (monthsLeft == 1) stringResource(R.string.qadaa_pace_months, monthsLeft)
                    else stringResource(R.string.qadaa_pace_months_plural, monthsLeft),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodayProgressCard(
    todayDone: Int,
    dailyGoal: Int,
    streak: Int,
    onEditGoal: () -> Unit,
    onMarkPrayers: () -> Unit,
    onMarkFasts: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Progress ring
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { todayDone.toFloat() / dailyGoal.coerceAtLeast(1) },
                    modifier    = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    trackColor  = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    "$todayDone/$dailyGoal",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Streak + goal
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.qadaa_today_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (streak > 0) {
                    Text(
                        stringResource(R.string.qadaa_streak, streak),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(
                    onClick         = onEditGoal,
                    contentPadding  = PaddingValues(0.dp),
                    modifier        = Modifier.height(24.dp),
                ) {
                    Text(
                        stringResource(R.string.qadaa_goal_label, dailyGoal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick         = onMarkPrayers,
                    modifier        = Modifier.height(36.dp),
                    shape           = RoundedCornerShape(50),
                    contentPadding  = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.qadaa_mark_prayers), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick        = onMarkFasts,
                    modifier       = Modifier.height(36.dp),
                    shape          = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.qadaa_mark_fasts), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PrayerDebtCard(debt: PrayerDebt, onMarkOne: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(148.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(debt.prayer.nameRes),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${debt.remaining}",
                style      = MaterialTheme.typography.headlineMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { debt.progress },
                modifier   = Modifier.fillMaxWidth().height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick        = onMarkOne,
                modifier       = Modifier.fillMaxWidth().height(34.dp),
                shape          = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp),
                enabled        = debt.remaining > 0,
            ) {
                Text(stringResource(R.string.qadaa_mark_one_done), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FastDebtRow(fast: FastDebt, onMarkDone: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fast.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (fast.remaining == 1) stringResource(R.string.qadaa_day_remaining, fast.remaining)
                    else stringResource(R.string.qadaa_days_remaining, fast.remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onMarkDone,
                shape   = RoundedCornerShape(50),
                enabled = fast.remaining > 0,
            ) {
                Text(stringResource(R.string.qadaa_mark_done))
            }
        }
    }
}

// ── Tab entry point ───────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
object QadaaTab : AppTab(
    iconRes  = R.drawable.ic_list,
    labelRes = R.string.more_qadaa,
    order    = 3,
) {
    @Composable override fun Content(padding: PaddingValues) = QadaaScreen(padding)
}