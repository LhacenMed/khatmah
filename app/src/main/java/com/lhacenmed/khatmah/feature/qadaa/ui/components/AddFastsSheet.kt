package com.lhacenmed.khatmah.feature.qadaa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.feature.qadaa.data.FastReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFastsSheet(
    onDismiss: () -> Unit,
    onAdd: (count: Int, reason: FastReason, label: String, ramadanYear: Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableStateOf(FastReason.RAMADAN) }
    var ramadanYearText by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }

    val ramadanYear = ramadanYearText.toIntOrNull()
    val autoLabel = when (reason) {
        FastReason.RAMADAN -> if (ramadanYear != null) "Ramadan $ramadanYear" else "Ramadan"
        FastReason.ILLNESS -> "Illness"
        FastReason.TRAVEL  -> "Travel"
        FastReason.OTHER   -> "Other"
    }
    val canAdd = count > 0 && (reason != FastReason.RAMADAN || ramadanYear != null)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Add missed fasts", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("Reason", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FastReason.entries.forEach { r ->
                    FilterChip(
                        selected = reason == r,
                        onClick  = { reason = r },
                        label    = { Text(r.label) },
                    )
                }
            }

            if (reason == FastReason.RAMADAN) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value            = ramadanYearText,
                    onValueChange    = { if (it.length <= 4) ramadanYearText = it },
                    label            = { Text("Hijri year (e.g. 1441)") },
                    keyboardOptions  = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine       = true,
                    modifier         = Modifier.fillMaxWidth(),
                    shape            = RoundedCornerShape(12.dp),
                    isError          = ramadanYearText.isNotEmpty() && ramadanYear == null,
                    supportingText   = if (ramadanYearText.isNotEmpty() && ramadanYear == null) {
                        { Text("Enter a valid Hijri year") }
                    } else null,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("How many days?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CounterButton("-", enabled = count > 1) { count-- }
                Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                CounterButton("+") { count++ }
                Text("days", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            if (showNote) {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("Optional note") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
            } else {
                TextButton(onClick = { showNote = true }) { Text("+ Add note") }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick  = { onAdd(count, reason, autoLabel, ramadanYear); onDismiss() },
                enabled  = canAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(50),
            ) { Text("Add to Qadaa", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(8.dp))
        }
    }
}