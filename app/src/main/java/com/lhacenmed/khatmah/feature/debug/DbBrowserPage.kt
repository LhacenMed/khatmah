package com.lhacenmed.khatmah.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbBrowserScreen() {
    val context     = LocalContext.current
    val nav         = LocalNavigator.current
    val vm          = viewModel<DbBrowserViewModel>(factory = DbBrowserViewModel.Factory(context))
    val state       by vm.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Open)
    val scope       = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState   = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 280.dp)) {
                DbDrawerContent(
                    state       = state,
                    onDbSelect  = vm::selectDb,
                    onTableSelect = { table ->
                        vm.selectTable(table)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title      = state.selectedTable ?: "DB Browser",
                    subtitle   = state.selectedDb,
                    isTopLevel = false,
                    onBack     = { nav.back() },
                    actions    = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open tables")
                        }
                    },
                )
            },
        ) { padding ->
            DbContentArea(state = state, padding = padding)
        }
    }
}

// ── Drawer ────────────────────────────────────────────────────────────────────

@Composable
private fun DbDrawerContent(
    state:         DbBrowserState,
    onDbSelect:    (String) -> Unit,
    onTableSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        Text(
            text     = "DB Browser",
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        HorizontalDivider()

        // DB dropdown
        DbDropdown(
            dbNames  = state.dbNames,
            selected = state.selectedDb,
            onSelect = onDbSelect,
        )

        // Tables list
        if (state.tables.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text     = "Tables (${state.tables.size})",
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(state.tables.size) { i ->
                    val table = state.tables[i]
                    NavigationDrawerItem(
                        label    = {
                            Text(
                                text     = table,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style    = MaterialTheme.typography.bodySmall,
                            )
                        },
                        selected = table == state.selectedTable,
                        onClick  = { onTableSelect(table) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
        } else if (state.selectedDb != null && !state.isLoading) {
            Text(
                text     = "No tables found",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun DbDropdown(dbNames: List<String>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth()) {
        OutlinedCard(
            onClick  = { if (dbNames.isNotEmpty()) expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = selected ?: "Select a database…",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector        = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            dbNames.forEach { db ->
                DropdownMenuItem(
                    text    = { Text(db, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSelect(db); expanded = false },
                )
            }
        }
    }
}

// ── Content area ──────────────────────────────────────────────────────────────

@Composable
private fun DbContentArea(state: DbBrowserState, padding: PaddingValues) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading     -> CircularProgressIndicator()
            state.error != null -> ErrorMessage(state.error)
            state.tableData != null -> TableView(
                data      = state.tableData,
                tableName = state.selectedTable ?: "",
            )
            state.selectedDb != null -> HintText("Select a table from the drawer →")
            else -> HintText("Open the drawer to select a database")
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text      = text,
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier  = Modifier.padding(32.dp),
    )
}

@Composable
private fun ErrorMessage(msg: String) {
    Text(
        text      = "Error: $msg",
        style     = MaterialTheme.typography.bodySmall,
        color     = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier  = Modifier.padding(24.dp),
    )
}

// ── Table view ────────────────────────────────────────────────────────────────

/** Width heuristic: scale with the longer of name vs type, clamp between 72–200 dp. */
private fun colWidth(col: ColumnMeta): Dp =
    (maxOf(col.name.length, col.type.length, 5) * 9).dp.coerceIn(72.dp, 200.dp)

private val NUM_COL_W = 44.dp   // row-index column
private val CELL_H    = 36.dp
private val HEADER_H  = 48.dp

@Composable
private fun TableView(data: TableData, tableName: String) {
    if (data.columns.isEmpty()) {
        HintText("Table is empty or has no columns")
        return
    }

    val colWidths  = remember(data.columns) { data.columns.map { colWidth(it) } }
    val totalWidth = remember(colWidths) { NUM_COL_W + colWidths.fold(0.dp) { acc, w -> acc + w } }
    val outline    = MaterialTheme.colorScheme.outlineVariant
    val headerBg   = MaterialTheme.colorScheme.surfaceContainer
    val altRowBg   = MaterialTheme.colorScheme.surfaceContainerLow
    val hScroll    = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Row count banner
        val isCapped = data.totalRows > data.rows.size
        Text(
            text     = if (isCapped) "Showing ${data.rows.size} of ${data.totalRows} rows"
            else "${data.totalRows} row${if (data.totalRows != 1) "s" else ""}",
            style    = MaterialTheme.typography.labelSmall,
            color    = if (isCapped) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScroll),
        ) {
            LazyColumn(modifier = Modifier.width(totalWidth)) {

                // ── Sticky header ─────────────────────────────────────────────
                stickyHeader {
                    Row(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(HEADER_H)
                            .background(headerBg)
                            .border(width = 0.5.dp, color = outline),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Row-number cell
                        HeaderCell(text = "#", subText = "INT", width = NUM_COL_W, dividerColor = outline)
                        data.columns.forEachIndexed { i, col ->
                            HeaderCell(
                                text         = col.name,
                                subText      = col.type,
                                width        = colWidths[i],
                                dividerColor = outline,
                            )
                        }
                    }
                    HorizontalDivider(color = outline, thickness = 1.dp)
                }

                // ── Data rows ─────────────────────────────────────────────────
                itemsIndexed(data.rows) { rowIdx, row ->
                    val bg = if (rowIdx % 2 == 0) MaterialTheme.colorScheme.surface else altRowBg
                    Row(
                        modifier = Modifier
                            .width(totalWidth)
                            .height(CELL_H)
                            .background(bg)
                            .border(width = 0.5.dp, color = outline),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Row index
                        DataCell(
                            text  = (rowIdx + 1).toString(),
                            width = NUM_COL_W,
                            muted = true,
                            dividerColor = outline,
                        )
                        row.forEachIndexed { colIdx, cell ->
                            DataCell(
                                text         = cell,
                                width        = colWidths[colIdx],
                                muted        = cell == null,
                                dividerColor = outline,
                            )
                        }
                    }
                }

                // ── Cap notice ────────────────────────────────────────────────
                if (isCapped) {
                    item {
                        Box(
                            modifier         = Modifier
                                .width(totalWidth)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = "⚠ Showing first ${data.rows.size} of ${data.totalRows} rows",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, subText: String, width: Dp, dividerColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(width = 0.5.dp, color = dividerColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text       = text,
                style      = MaterialTheme.typography.labelSmall.copy(
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text  = subText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataCell(
    text:         String?,
    width:        Dp,
    muted:        Boolean,
    dividerColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(width = 0.5.dp, color = dividerColor)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text       = text ?: "null",
            style      = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize   = 11.sp,
            ),
            color      = if (muted) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}
