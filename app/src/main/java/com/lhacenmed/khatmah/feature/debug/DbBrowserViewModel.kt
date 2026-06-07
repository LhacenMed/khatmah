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
)

data class DbBrowserState(
    val dbNames: List<String> = emptyList(),
    val selectedDb: String? = null,
    val tables: List<String> = emptyList(),
    val selectedTable: String? = null,
    val tableData: TableData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
@SuppressLint("StaticFieldLeak")
class DbBrowserViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val MAX_ROWS   = 500
        private val KNOWN_DBS        = listOf("khatmah.db", "quran.db", "mushaf.db", "qadaa.db")
    }

    private val _state = MutableStateFlow(DbBrowserState())
    val state: StateFlow<DbBrowserState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // quran.db is always available (copies from assets on demand)
            val available = KNOWN_DBS.filter { it == "quran.db" || resolveDbFile(it) != null }
            _state.update { it.copy(dbNames = available) }
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
        _state.update { it.copy(selectedTable = name, tableData = null, isLoading = true, error = null) }
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
                    val rows = mutableListOf<List<String?>>()
                    db.rawQuery("SELECT * FROM \"$name\" LIMIT $MAX_ROWS", null).use { c ->
                        val n = c.columnCount
                        while (c.moveToNext())
                            rows += List(n) { i -> if (c.isNull(i)) null else c.getString(i) }
                    }
                    TableData(cols, rows, total)
                }
            }.fold(
                onSuccess = { data -> _state.update { it.copy(tableData = data, isLoading = false) } },
                onFailure = { e   -> _state.update { it.copy(isLoading = false, error = e.message) } },
            )
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun resolveDbFile(name: String): File? {
        val file = context.getDatabasePath(name)
        if (name == "quran.db" && !file.exists()) {
            file.parentFile?.mkdirs()
            context.assets.open("databases/quran.db").use { it.copyTo(file.outputStream()) }
        }
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