package com.lhacenmed.khatmah.feature.qadaa.ui

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.feature.qadaa.data.FastDebt
import com.lhacenmed.khatmah.feature.qadaa.data.PrayerDebt
import com.lhacenmed.khatmah.feature.qadaa.ui.components.AddFastsSheet
import com.lhacenmed.khatmah.feature.qadaa.ui.components.AddPrayersSheet
import com.lhacenmed.khatmah.feature.qadaa.ui.components.MarkDoneSheet
import com.lhacenmed.khatmah.core.nav.Route

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun QadaaPage() {
    val nav = LocalNavController.current
    val vm: QadaaViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    // Sheet visibility state
    var showAddPrayers by remember { mutableStateOf(false) }
    var showAddFasts by remember { mutableStateOf(false) }
    var showMarkDone by remember { mutableStateOf(false) }
    var markDoneInitialTab by remember { mutableIntStateOf(0) }
    var markDonePreselectedFastId by remember { mutableStateOf<Long?>(null) }

    val monthsLeft = remember(state.totalPrayersRemaining, state.dailyGoal) {
        if (state.dailyGoal > 0 && state.totalPrayersRemaining > 0)
            ((state.totalPrayersRemaining.toFloat() / state.dailyGoal) / 30f).toInt().coerceAtLeast(1)
        else 0
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = "Qadaa",
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
                actions    = {
                    IconButton(onClick = { nav.navigate(Route.QADAA_HISTORY) }, tooltipText = "History") {
                        Icon(Icons.Outlined.History, contentDescription = "History")
                    }
                    IconButton(onClick = { showAddPrayers = true }, tooltipText = "Add missed prayers") {
                        Icon(Icons.Outlined.Add, contentDescription = "Add")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
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
                    todayDone  = state.todayDone,
                    dailyGoal  = state.dailyGoal,
                    streak     = state.streak,
                    onMarkPrayers = { markDoneInitialTab = 0; showMarkDone = true },
                    onMarkFasts   = { markDoneInitialTab = 1; markDonePreselectedFastId = null; showMarkDone = true },
                )
            }

            // ── Prayers section ───────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prayers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate(Route.QADAA_HISTORY) }) { Text("See all") }
                }
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    items(state.prayers, key = { it.prayer.name }) { debt ->
                        PrayerDebtCard(
                            debt      = debt,
                            onMarkOne = { vm.markPrayersDone(mapOf(debt.prayer to 1)) },
                        )
                    }
                }
            }

            // ── Fasts section ─────────────────────────────────────────────────
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fasts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddFasts = true }) { Text("+ Add fasts") }
                }
            }
            if (state.fasts.isEmpty()) {
                item {
                    Text("No missed fasts recorded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.fasts, key = { it.id }) { fast ->
                    FastDebtRow(
                        fast = fast,
                        onMarkDone = {
                            markDoneInitialTab = 1
                            markDonePreselectedFastId = fast.id
                            showMarkDone = true
                        },
                    )
                }
            }
        }
    }

    // ── Bottom sheets ─────────────────────────────────────────────────────────

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
            fasts              = state.fasts,
            initialTab         = markDoneInitialTab,
            preselectedFastId  = markDonePreselectedFastId,
            onDismiss          = { showMarkDone = false },
            onMarkPrayers      = { counts, fullDay -> vm.markPrayersDone(counts, fullDay = fullDay) },
            onMarkFasts        = { count, id -> vm.markFastsDone(count, id) },
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(prayersRemaining: Int, fastsRemaining: Int, monthsLeft: Int) {
    Card(shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Prayers stat
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🕌", style = MaterialTheme.typography.titleLarge)
                    Text("$prayersRemaining",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("prayers remaining", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VerticalDivider(modifier = Modifier.height(72.dp).padding(horizontal = 8.dp))
                // Fasts stat
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌙", style = MaterialTheme.typography.titleLarge)
                    Text("$fastsRemaining",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text("fasts remaining", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (monthsLeft > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    "At your current pace — done in approx. $monthsLeft month${if (monthsLeft != 1) "s" else ""}",
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
    onMarkPrayers: () -> Unit,
    onMarkFasts: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                Text("$todayDone/$dailyGoal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }
            // Streak
            Column(modifier = Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (streak > 0) {
                    Text("🔥 $streak-day streak", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                Text("Daily goal", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMarkPrayers, modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text("Mark prayers", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onMarkFasts, modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text("Mark fasts", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PrayerDebtCard(debt: PrayerDebt, onMarkOne: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(148.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(debt.prayer.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("${debt.remaining}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { debt.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onMarkOne,
                modifier = Modifier.fillMaxWidth().height(34.dp),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 8.dp),
                enabled = debt.remaining > 0,
            ) {
                Text("+ Mark one done", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FastDebtRow(fast: FastDebt, onMarkDone: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fast.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("${fast.remaining} day${if (fast.remaining != 1) "s" else ""} remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onMarkDone,
                shape = RoundedCornerShape(50),
                enabled = fast.remaining > 0,
            ) { Text("Mark done") }
        }
    }
}