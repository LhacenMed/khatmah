package com.lhacenmed.khatmah.feature.trips.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import com.lhacenmed.khatmah.core.ui.components.IconButton
import com.lhacenmed.khatmah.feature.trips.data.StatusColor
import com.lhacenmed.khatmah.feature.trips.data.TripRequest
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripRequestsPage(vm: TripRequestsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val nav = LocalNavController.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.more_trip_requests),
                isTopLevel = false,
                actions = {
                    IconButton(onClick = vm::refresh, tooltipText = "Refresh") {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
                onBack = { nav.navigateUp() },
            )
        },
    ) { padding ->
        when (val s = state) {
            is TripRequestsState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is TripRequestsState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::refresh) { Text("Retry") }
                }
            }

            is TripRequestsState.Success -> {
                if (s.items.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) { Text("No trip requests yet.") }
                } else {
                    LazyColumn(
                        modifier       = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(s.items, key = { it.id }) { TripRequestCard(it) }
                    }
                }
            }
        }
    }
}

// ── Card ──────────────────────────────────────────────────────────────────────

@Composable
private fun TripRequestCard(req: TripRequest) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: name + status badge
            Row(
                modifier       = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(req.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                StatusBadge(req.status, req.statusColor)
            }

            Spacer(Modifier.height(4.dp))
            Text(req.destinationLabel, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(req.phone, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            req.establishment?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            req.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            req.adminNote?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("Note: $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                formatDate(req.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: StatusColor) {
    val containerColor = when (color) {
        StatusColor.GREEN  -> Color(0xFF2E7D32)
        StatusColor.RED    -> Color(0xFFC62828)
        StatusColor.BLUE   -> Color(0xFF1565C0)
        StatusColor.ORANGE -> Color(0xFFE65100)
    }
    Surface(shape = MaterialTheme.shapes.small, color = containerColor) {
        Text(
            text      = label.replaceFirstChar { it.uppercaseChar() },
            modifier  = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style     = MaterialTheme.typography.labelSmall,
            color     = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatDate(iso: String): String = runCatching {
    val parser    = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    formatter.format(parser.parse(iso)!!)
}.getOrElse { iso }