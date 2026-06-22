package com.lhacenmed.khatmah.feature.khatmah.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.khatmah.data.KhatmahRepository
import com.lhacenmed.khatmah.feature.khatmah.data.SessionRow
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.ui.reader.MushafDownloadDialog
import com.lhacenmed.khatmah.feature.quran.ui.reader.isQcf4
import com.lhacenmed.khatmah.feature.quran.ui.reader.sessionReaderDest

/**
 * Lists the active khatmah's sessions, split by read state — previously read ([showRead] = true)
 * or upcoming ([showRead] = false). Each row re-opens that session's page window in the QCF4 book
 * reader; a text print can't honour page windows, so it prompts via [MushafDownloadDialog] instead.
 */
@Composable
fun SessionsScreen(showRead: Boolean) {
    val context = LocalContext.current
    val nav     = LocalNavigator.current

    val selectedPrint by MushafPrefs.selected.collectAsState()
    val repo = remember { KhatmahRepository(context) }
    val rows by remember(showRead) { repo.sessionRows(showRead) }.collectAsState(initial = null)

    var showDlDialog by remember { mutableStateOf(false) }

    fun open(session: SessionRow) {
        if (!selectedPrint.isQcf4) { showDlDialog = true; return }
        nav.go(sessionReaderDest(session.entity.id, session.entity.startPage, session.entity.endPage))
    }

    val current = rows
    when {
        current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        current.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                text     = stringResource(
                    if (showRead) R.string.sessions_none_previous else R.string.sessions_none_upcoming
                ),
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(current, key = { it.entity.id }) { row ->
                SessionListItem(row = row, onClick = { open(row) })
                HorizontalDivider()
            }
        }
    }

    if (showDlDialog) {
        MushafDownloadDialog(
            onSettings = { showDlDialog = false; nav.go(Dest.MushafPrints) },
            onDismiss  = { showDlDialog = false },
        )
    }
}

@Composable
private fun SessionListItem(row: SessionRow, onClick: () -> Unit) {
    val e = row.entity
    ListItem(
        modifier        = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(stringResource(R.string.khatmah_session_label, e.dayNumber)) },
        headlineContent = { Text(stringResource(R.string.khatmah_from_label, row.startSuraName, e.startAya)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.khatmah_to_label, row.endSuraName, e.endAya))
                Text(
                    text  = stringResource(R.string.khatmah_pages_label, e.startPage, e.endPage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector        = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
            )
        },
    )
}
