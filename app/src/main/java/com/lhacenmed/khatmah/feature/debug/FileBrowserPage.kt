package com.lhacenmed.khatmah.feature.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.core.ui.components.AppTopBar

private const val FILE_DISPLAY_LIMIT = 60

@Composable
fun FileBrowserPage() {
    val ctx   = LocalContext.current
    val nav   = LocalNavController.current
    val vm    = viewModel<FileBrowserViewModel>(factory = FileBrowserViewModel.Factory(ctx))
    val state by vm.state.collectAsState()

    var confirmId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title      = "File Browser",
                isTopLevel = false,
                onBack     = { nav.popBackStack() },
                actions    = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text     = "App private storage · ${state.groups.sumOf { it.totalBytes }.toReadable()}",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            items(state.groups, key = { it.id }) { group ->
                GroupCard(
                    group      = group,
                    isDeleting = state.deletingId == group.id,
                    onExpand   = { vm.toggleExpand(group.id) },
                    onDelete   = { confirmId = group.id },
                )
            }
        }
    }

    // Delete confirmation dialog
    confirmId?.let { id ->
        val g = state.groups.find { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("Delete \"${g.label}\"?") },
            text  = {
                Text("${g.entries.size} files · ${g.totalBytes.toReadable()} will be permanently deleted.")
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); confirmId = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmId = null }) { Text("Cancel") }
            },
        )
    }
}

// ── Group card ─────────────────────────────────────────────────────────────────

@Composable
private fun GroupCard(
    group:      FsGroup,
    isDeleting: Boolean,
    onExpand:   () -> Unit,
    onDelete:   () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = group.type.toIcon(),
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text       = group.label,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text  = if (group.entries.isEmpty()) "Not downloaded"
                        else "${group.entries.size} files · ${group.totalBytes.toReadable()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (group.canDelete && group.entries.isNotEmpty()) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector        = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector        = if (group.expanded) Icons.Outlined.ExpandLess
                        else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }

            // Expandable file list
            if (group.expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (group.entries.isEmpty()) {
                    Text(
                        text  = "Directory is empty or not yet downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    group.entries.take(FILE_DISPLAY_LIMIT).forEach { FileRow(it) }
                    if (group.entries.size > FILE_DISPLAY_LIMIT) {
                        Text(
                            text     = "… ${group.entries.size - FILE_DISPLAY_LIMIT} more files not shown",
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FsEntry) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = entry.name,
            style    = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = entry.sizeBytes.toReadable(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun FsGroupType.toIcon(): ImageVector = when (this) {
    FsGroupType.DATABASES    -> Icons.Outlined.Storage
    FsGroupType.HAFS_FONTS   -> Icons.Outlined.Download
    FsGroupType.WARSH_IMAGES -> Icons.Outlined.Image
    FsGroupType.WARSH_SVG    -> Icons.Outlined.Description
}

private fun Long.toReadable(): String = when {
    this >= 1_048_576L -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024L     -> "%.0f KB".format(this / 1_024.0)
    else               -> "$this B"
}