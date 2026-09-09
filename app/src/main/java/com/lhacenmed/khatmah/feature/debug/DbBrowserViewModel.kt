package com.lhacenmed.khatmah.feature.debug

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ColumnMeta(val name: String, val type: String)

data class TableData(
    val columns: List<ColumnMeta>,
    val rows: List<List<String?>>,
    val totalRows: Int,
) {
    /** True once every row of the table has been read in. */
    val isFullyLoaded: Boolean get() = rows.size >= totalRows
}

data class DbBrowserState(
    val dbNames: List<String> = emptyList(),
    val selectedDb: String? = null,
    val tables: List<String> = emptyList(),
    val selectedTable: String? = null,
    val tableData: TableData? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)
@SuppressLint("StaticFieldLeak")
class DbBrowserViewModel(private val context: Context) : ViewModel() {

    companion object {
        /**
         * Rows per read. Small enough that a table opens instantly however big it is, and large
         * enough that scrolling stays ahead of the reader.
         */
        private const val PAGE_SIZE = 200

        /** What SQLite keeps beside a database. None of them is one. */
        private val SIDECAR_SUFFIXES = listOf("-journal", "-wal", "-shm")
    }

    private val _state = MutableStateFlow(DbBrowserState())
    val state: StateFlow<DbBrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(dbNames = installedDatabases()) }
        }
    }

    fun selectDb(name: String) {
        if (_state.value.selectedDb == name) return
        _state.update {
            it.copy(
                selectedDb    = name,
                tables        = emptyList(),
                selectedTable = null,
                tableData     = null,
                isLoading     = true,
                error         = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = resolveDbFile(name) ?: error("Not found: $name")
                openReadOnly(file) { db ->
                    db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
                        null,
                    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
                }
            }.fold(
                onSuccess = { tables -> _state.update { it.copy(tables = tables, isLoading = false) } },
                onFailure = { e    -> _state.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    fun selectTable(name: String) {
        val dbName = _state.value.selectedDb ?: return
        _state.update {
            it.copy(
                selectedTable = name,
                tableData     = null,
                isLoading     = true,
                isLoadingMore = false,
                error         = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = resolveDbFile(dbName) ?: error("Not found: $dbName")
                openReadOnly(file) { db ->
                    val cols = mutableListOf<ColumnMeta>()
                    db.rawQuery("PRAGMA table_info(\"$name\")", null).use { c ->
                        while (c.moveToNext())
                            cols += ColumnMeta(c.getString(1), c.getString(2).ifBlank { "—" })
                    }
                    val total = db.rawQuery("SELECT COUNT(*) FROM \"$name\"", null)
                        .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                    TableData(cols, readRows(db, name, offset = 0), total)
                }
            }.fold(
                onSuccess = { data -> _state.update { it.copy(tableData = data, isLoading = false) } },
                onFailure = { e   -> _state.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    /**
     * Reads the next page onto the end of the table on screen.
     *
     * Called as the last rows come into view, so it is asked far more often than it has work to
     * do: a page already on its way, or a table with nothing left, answers by doing nothing.
     */
    fun loadMoreRows() {
        val current = _state.value
        val shown   = current.tableData ?: return
        val dbName  = current.selectedDb ?: return
        val table   = current.selectedTable ?: return
        if (current.isLoadingMore || shown.isFullyLoaded) return

        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = resolveDbFile(dbName) ?: error("Not found: $dbName")
                openReadOnly(file) { db -> readRows(db, table, offset = shown.rows.size) }
            }.fold(
                onSuccess = { more ->
                    _state.update { s ->
                        val onScreen = s.tableData
                        // The reader may have moved to another table while this page was in
                        // flight. Its rows belong to a table no longer on screen, and so does its
                        // claim on the guard: whoever is loading now still holds that.
                        if (s.selectedTable != table || onScreen == null) s
                        else s.copy(
                            tableData     = onScreen.copy(rows = onScreen.rows + more),
                            isLoadingMore = false,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { s ->
                        if (s.selectedTable != table) s
                        else s.copy(isLoadingMore = false, error = e.message)
                    }
                },
            )
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Every database this app has actually created.
     *
     * Read from disk rather than named in a list here, because a list here is a list someone has
     * to remember to extend — and the database added last is exactly the one worth looking at.
     */
    private fun installedDatabases(): List<String> =
        context.databaseList()
            .filterNot { name -> SIDECAR_SUFFIXES.any(name::endsWith) }
            .sorted()

    /**
     * One page of rows, in the order the table itself keeps them.
     *
     * Unordered on purpose: an ORDER BY would change which rows a page contains, and there is no
     * column every table has to order by.
     */
    private fun readRows(db: SQLiteDatabase, table: String, offset: Int): List<List<String?>> =
        db.rawQuery("SELECT * FROM \"$table\" LIMIT $PAGE_SIZE OFFSET $offset", null).use { c ->
            buildList {
                val n = c.columnCount
                while (c.moveToNext()) add(List(n) { i -> if (c.isNull(i)) null else c.getString(i) })
            }
        }

    private fun resolveDbFile(name: String): File? {
        val file = context.getDatabasePath(name)
        return if (file.exists()) file else null
    }

    private inline fun <T> openReadOnly(file: File, block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .use(block)

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DbBrowserViewModel(context) as T
    }
}
