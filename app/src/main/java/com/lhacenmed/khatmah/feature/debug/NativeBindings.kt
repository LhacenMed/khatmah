package com.lhacenmed.khatmah.feature.debug

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

object NativeBindings {

    external fun renameFileNative(oldPath: String, newName: String): Boolean
    external fun deleteFileOrDirectoryNative(path: String): Boolean
    external fun getFileMetadataNative(path: String): FileMetadata?

    fun rename(old: String, new: String) = renameFileNative(old, new)
    fun delete(path: String)             = deleteFileOrDirectoryNative(path)
    fun metadata(path: String)           = getFileMetadataNative(path)

    init { System.loadLibrary("khatmah_files") }
}

// ── Dynamic color extractor (debug only) ─────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.S)
fun buildDynamicColorJson(context: Context): String {
    val palettes = linkedMapOf(
        "accent1"  to "system_accent1",
        "accent2"  to "system_accent2",
        "accent3"  to "system_accent3",
        "neutral1" to "system_neutral1",
        "neutral2" to "system_neutral2",
    )
    val tones = listOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

    return buildString {
        appendLine("{")
        palettes.entries.forEachIndexed { pi, (key, resPrefix) ->
            appendLine("  \"$key\": {")
            tones.forEachIndexed { ti, tone ->
                val resId = android.R.color::class.java
                    .getField("${resPrefix}_$tone").getInt(null)
                val hex = "#%06X".format(context.getColor(resId) and 0xFFFFFF)
                val comma = if (ti < tones.lastIndex) "," else ""
                appendLine("    \"$tone\": \"$hex\"$comma")
            }
            val comma = if (pi < palettes.size - 1) "," else ""
            appendLine("  }$comma")
        }
        append("}")
    }
}