package com.lhacenmed.khatmah.feature.qadaa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.feature.qadaa.data.FastDebt
import com.lhacenmed.khatmah.feature.qadaa.data.Prayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkDoneSheet(
    fasts: List<FastDebt>,
    initialTab: Int = 0,               // 0=Prayers, 1=Fasts
    preselectedFastId: Long? = null,
    onDismiss: () -> Unit,
    onMarkPrayers: (counts: Map<Prayer, Int>, fullDay: Boolean) -> Unit,
    onMarkFasts: (count: Int, fastId: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text("Mark as made up",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab, divider = {}) {
                listOf("Prayers", "Fasts").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }) {
                        Text(title, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            when (tab) {
                0 -> PrayersTab(onMark = { counts, fullDay ->
                    onMarkPrayers(counts, fullDay)
                    onDismiss()
                })
                1 -> FastsTab(
                    fasts              = fasts,
                    preselectedFastId  = preselectedFastId,
                    onMark             = { count, id ->
                        onMarkFasts(count, id)
                        onDismiss()
                    },
                )
            }
        }
    }
}

// ── Prayers tab ───────────────────────────────────────────────────────────────

@Composable
private fun PrayersTab(onMark: (Map<Prayer, Int>, fullDay: Boolean) -> Unit) {
    val counts = remember { mutableStateMapOf<Prayer, Int>().also { m -> Prayer.entries.forEach { m[it] = 1 } } }
    val total = counts.values.sum()

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        OutlinedButton(
            onClick  = { onMark(Prayer.entries.associateWith { 1 }, true) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(50),
        ) { Text("+ Full day (5 prayers)") }

        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(16.dp)) {
            Prayer.entries.forEachIndexed { i, prayer ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(prayer.displayName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    CounterButton("-", enabled = (counts[prayer] ?: 0) > 0) {
                        counts[prayer] = ((counts[prayer] ?: 0) - 1).coerceAtLeast(0)
                    }
                    Text(
                        "${counts[prayer] ?: 0}",
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CounterButton("+") { counts[prayer] = (counts[prayer] ?: 0) + 1 }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        if (total > 0) {
            Text(
                "Making up $total prayer${if (total != 1) "s" else ""} today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick  = { onMark(counts.filter { it.value > 0 }, false) },
            enabled  = total > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(50),
        ) { Text("Confirm") }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Fasts tab ─────────────────────────────────────────────────────────────────

@Composable
private fun FastsTab(
    fasts: List<FastDebt>,
    preselectedFastId: Long?,
    onMark: (count: Int, fastId: Long) -> Unit,
) {
    var count by rememberSaveable { mutableIntStateOf(1) }
    var selectedId by rememberSaveable {
        mutableStateOf(preselectedFastId ?: fasts.firstOrNull()?.id)
    }
    var showNote by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (fasts.isEmpty()) {
            Text("No fast groups added yet. Use '+ Add fasts' on the main screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        Text("How many days?", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CounterButton("-", enabled = count > 1) { count-- }
            Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            CounterButton("+") { count++ }
            Text("day${if (count != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Text("From which group?", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        // Chips for each fast group
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            fasts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { fast ->
                        FilterChip(
                            selected = selectedId == fast.id,
                            onClick  = { selectedId = fast.id },
                            label    = { Text("${fast.label} (${fast.remaining})") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!showNote) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showNote = true }) { Text("+ Add note") }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = { selectedId?.let { onMark(count, it) } },
            enabled  = selectedId != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(50),
        ) { Text("Confirm") }
        Spacer(Modifier.height(8.dp))
    }
}