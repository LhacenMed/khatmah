package com.lhacenmed.khatmah.feature.quran.ui.bookmarks

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.Dest
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.quran.data.BookmarkRepository
import com.lhacenmed.khatmah.feature.quran.data.MushafPrefs
import com.lhacenmed.khatmah.feature.quran.ui.reader.PageMeta
import com.lhacenmed.khatmah.feature.quran.ui.reader.ReaderMeta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One bookmark row: its page plus the page's resolved sura/juz meta (null until meta loads). */
data class BookmarkRow(val page: Int, val meta: PageMeta?)

/**
 * Bookmarks for the currently selected riwaya, newest first, enriched with each page's sura/juz.
 * Bookmarks are created in the QCF4 book reader, so the list is scoped to the selected riwaya.
 */
class BookmarksViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BookmarkRepository(app)
    private val riwaya = MushafPrefs.selected.value.riwaya

    /** null = still loading. */
    val rows: StateFlow<List<BookmarkRow>?> = repo.bookmarks(riwaya.dbKey)
        .map { list ->
            val meta = ReaderMeta.loadForRiwaya(getApplication(), riwaya.dbKey)
            list.map { BookmarkRow(it.pageNum, meta[it.pageNum]) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun remove(page: Int) {
        viewModelScope.launch { repo.remove(riwaya.dbKey, page) }
    }
}

@Composable
fun BookmarksScreen() {
    val vm: BookmarksViewModel = viewModel()
    val nav = LocalNavigator.current
    val rows by vm.rows.collectAsState()

    val current = rows
    when {
        current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        current.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                text = stringResource(R.string.bookmarks_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(current, key = { it.page }) { row ->
                BookmarkItem(
                    row = row,
                    onOpen = { nav.go(Dest.Reader(page = row.page)) },
                    onRemove = { vm.remove(row.page) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BookmarkItem(row: BookmarkRow, onOpen: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = {
            Text(row.meta?.toolbarTitle ?: "صفحة ${row.page}")
        },
        supportingContent = row.meta?.let { { Text(it.toolbarSubtitle) } },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
