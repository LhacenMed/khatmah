package com.lhacenmed.khatmah.feature.mushaf.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the user's selected [MushafPrint]. Call [init] once from [App.onCreate]. */
object MushafPrefs {

    private const val PREFS_FILE = "mushaf_prefs"
    private const val KEY_PRINT  = "selected_print_id"

    private val _selected = MutableStateFlow<MushafPrint>(MushafRegistry.default)
    val selected: StateFlow<MushafPrint> = _selected.asStateFlow()

    fun init(context: Context) {
        val id = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_PRINT, null)
        _selected.value = id?.let { MushafRegistry.byId(it) } ?: MushafRegistry.default
    }

    fun set(context: Context, print: MushafPrint) {
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_PRINT, print.id) }
        _selected.value = print
    }
}