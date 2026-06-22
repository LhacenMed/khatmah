package com.lhacenmed.khatmah.feature.khatmah.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavigator

@Composable
fun NewKhatmahScreen() {
    val context = LocalContext.current
    val nav     = LocalNavigator.current
    val vm: NewKhatmahViewModel = viewModel(factory = NewKhatmahViewModel.Factory(context))
    val state   by vm.state.collectAsState()

    // Step 2's back returns to step 1; the host routes the toolbar up arrow through this too.
    BackHandler(enabled = state.step == 2) { vm.goBack() }

    // Body only — the title + back arrow come from ScreenHostActivity (see Dest.NewKhatmah.titleRes).
    when (state.step) {
        1    -> Step1(state, vm, PaddingValues(0.dp))
        else -> Step2(state, vm, PaddingValues(0.dp))
    }

    // Once the khatmah is persisted, leave the page — no confirmation dialog.
    LaunchedEffect(state.savedResult) {
        if (state.savedResult != null) nav.back()
    }
}

// ── Step 1: Starting position ─────────────────────────────────────────────────

@Composable
private fun Step1(state: NewKhatmahState, vm: NewKhatmahViewModel, padding: PaddingValues) {
    val juzOptions = stringArrayResource(R.array.new_khatmah_juz_options).toList()

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier         = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = stringResource(R.string.new_khatmah_step1_desc),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
        }

        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Label at content-start (right in RTL)
            Text(
                text  = stringResource(R.string.new_khatmah_start_from),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(12.dp))
            KhatmahDropdown(
                modifier      = Modifier.weight(1f),
                options       = juzOptions,
                selectedIndex = state.startJuz - 1,
                onSelect      = { vm.setStartJuz(it + 1) },
            )
        }

        Button(
            onClick  = vm::goToStep2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text  = stringResource(R.string.new_khatmah_continue),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// ── Step 2: Duration & daily amount ──────────────────────────────────────────

@Composable
private fun Step2(state: NewKhatmahState, vm: NewKhatmahViewModel, padding: PaddingValues) {
    val ajzaOptions = stringArrayResource(R.array.new_khatmah_ajza_options).toList()
    val arbaOptions = stringArrayResource(R.array.new_khatmah_arba_options).toList()

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier         = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = stringResource(R.string.new_khatmah_step2_desc),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
            )
        }

        Column(
            modifier            = Modifier.padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Duration row: [label] [value card] [stepper]  (RTL: stepper | value | label)
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.new_khatmah_duration_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(12.dp))
                OutlinedCard(modifier = Modifier.width(110.dp)) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.new_khatmah_days, state.durationDays),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                DurationStepper(
                    onIncrement = { vm.setDuration(state.durationDays + 1) },
                    onDecrement = { vm.setDuration(state.durationDays - 1) },
                )
            }

            // Daily amount row: [label] [ajza dropdown] [and] [arba dropdown]
            // RTL display: arba | and | ajza | label
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.new_khatmah_daily_amount_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(12.dp))
                KhatmahDropdown(
                    options       = ajzaOptions,
                    selectedIndex = state.dailyAjza,
                    onSelect      = { vm.setDailyAmount(it, state.dailyArba) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = stringResource(R.string.new_khatmah_and),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                KhatmahDropdown(
                    options       = arbaOptions,
                    selectedIndex = state.dailyArba,
                    onSelect      = { vm.setDailyAmount(state.dailyAjza, it) },
                )
            }

            Button(
                onClick  = vm::save,
                enabled  = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text  = stringResource(R.string.new_khatmah_continue),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
private fun KhatmahDropdown(
    options:       List<String>,
    selectedIndex: Int,
    onSelect:      (Int) -> Unit,
    modifier:      Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedCard(onClick = { expanded = true }) {
            Row(
                modifier          = Modifier
                    .defaultMinSize(minWidth = 90.dp)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector        = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text     = options.getOrElse(selectedIndex) { options.firstOrNull().orEmpty() },
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
        }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { i, option ->
                DropdownMenuItem(
                    text    = { Text(option) },
                    onClick = { onSelect(i); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DurationStepper(onIncrement: () -> Unit, onDecrement: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(50.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onIncrement, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            VerticalDivider(modifier = Modifier.height(24.dp))
            IconButton(onClick = onDecrement, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
