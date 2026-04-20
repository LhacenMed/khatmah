package com.lhacenmed.khatmah.data.adhkar

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AdhkarRepository(private val context: Context) {

    private val db get() = AdhkarDatabase.open(context)

    // ── Seeding ───────────────────────────────────────────────────────────────

    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        val isEmpty = db.rawQuery("SELECT COUNT(*) FROM categories", null)
            .use { it.moveToFirst(); it.getInt(0) == 0 }
        if (isEmpty) seedBuiltIns()
    }

    private fun seedBuiltIns() {
        db.beginTransaction()
        try {
            builtInDescriptors.forEachIndexed { idx, desc ->
                db.insert("categories", null, ContentValues().apply {
                    put("id", desc.id)
                    put("title_res", desc.titleResName)
                    putNull("title_text")
                    put("icon_res", desc.iconResName)
                    putNull("icon_uri")
                    put("color_argb", desc.colorArgb)
                    put("span", desc.span)
                    put("sort_order", idx)
                })
            }
            AdhkarData.allCategories().forEach { (categoryId, list) ->
                list.forEachIndexed { idx, dhikr ->
                    db.insert("dhikr", null, ContentValues().apply {
                        put("category_id", categoryId)
                        put("sort_order", idx)
                        put("repetitions", dhikr.repetitions)
                        put("paragraphs", dhikr.paragraphs.toJson())
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Categories ────────────────────────────────────────────────────────────

    suspend fun getCategories(): List<AdhkarCategory> = withContext(Dispatchers.IO) {
        db.rawQuery(
            "SELECT id, title_res, title_text, icon_res, icon_uri, color_argb, span FROM categories ORDER BY sort_order",
            null
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val titleRes  = c.getString(1)
                    val titleText = c.getString(2)
                    val iconRes   = c.getString(3)
                    val iconUri   = c.getString(4)

                    val title = titleText ?: titleRes?.let { resolveString(it) } ?: ""
                    val iconSource: IconSource = when {
                        iconUri != null -> IconSource.Uri(iconUri)
                        iconRes != null -> resolveDrawableId(iconRes)
                            ?.let { IconSource.Res(it) } ?: IconSource.None
                        else            -> IconSource.None
                    }
                    add(
                        AdhkarCategory(
                            id          = c.getString(0),
                            title       = title,
                            iconSource  = iconSource,
                            color       = Color(c.getInt(5)),
                            span        = c.getInt(6),
                        )
                    )
                }
            }
        }
    }

    suspend fun insertCategory(
        category: AdhkarCategory,
        dhikrList: List<Dhikr>,
        sortOrder: Int,
    ) = withContext(Dispatchers.IO) {
        db.beginTransaction()
        try {
            db.insert("categories", null, ContentValues().apply {
                put("id", category.id)
                putNull("title_res")
                put("title_text", category.title)
                put("color_argb", category.color.toArgb())
                put("span", category.span)
                put("sort_order", sortOrder)
                when (val src = category.iconSource) {
                    is IconSource.Res  -> { put("icon_res", context.resources.getResourceEntryName(src.resId)); putNull("icon_uri") }
                    is IconSource.Uri  -> { putNull("icon_res"); put("icon_uri", src.path) }
                    is IconSource.None -> { putNull("icon_res"); putNull("icon_uri") }
                }
            })
            dhikrList.forEachIndexed { idx, dhikr ->
                db.insert("dhikr", null, ContentValues().apply {
                    put("category_id", category.id)
                    put("sort_order", idx)
                    put("repetitions", dhikr.repetitions)
                    put("paragraphs", dhikr.paragraphs.toJson())
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleteCategories(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val placeholders = ids.joinToString(",") { "?" }
        db.execSQL("DELETE FROM categories WHERE id IN ($placeholders)", ids.toTypedArray())
    }

    // ── Dhikr ─────────────────────────────────────────────────────────────────

    suspend fun getDhikrForCategory(categoryId: String): List<Dhikr> =
        withContext(Dispatchers.IO) {
            db.rawQuery(
                "SELECT paragraphs, repetitions FROM dhikr WHERE category_id = ? ORDER BY sort_order",
                arrayOf(categoryId)
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        Dhikr(
                            paragraphs  = c.getString(0).parseParagraphs(),
                            repetitions = c.getInt(1),
                        )
                    )
                }
            }
        }

    // ── Image handling ────────────────────────────────────────────────────────

    /** Copies a content URI image into app-private storage and returns the file path. */
    fun copyImageToInternal(uri: Uri): String? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext  = mime.substringAfterLast("/").let { if (it == "jpeg") "jpg" else it }
        val dir  = File(context.filesDir, "adhkar_icons").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use(input::copyTo)
        }
        dest.absolutePath
    }.getOrNull()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveString(resName: String): String {
        val id = context.resources.getIdentifier(resName, "string", context.packageName)
        return if (id != 0) context.getString(id) else resName
    }

    private fun resolveDrawableId(resName: String): Int? {
        val id = context.resources.getIdentifier(resName, "drawable", context.packageName)
        return if (id != 0) id else null
    }
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

internal fun List<DhikrParagraph>.toJson(): String = JSONArray().also { arr ->
    forEach { p ->
        arr.put(JSONObject().apply {
            put("type", when (p) {
                is DhikrParagraph.Body  -> "BODY"
                is DhikrParagraph.Quran -> "QURAN"
                is DhikrParagraph.Note  -> "NOTE"
            })
            put("text", when (p) {
                is DhikrParagraph.Body  -> p.text
                is DhikrParagraph.Quran -> p.text
                is DhikrParagraph.Note  -> p.text
            })
        })
    }
}.toString()

internal fun String.parseParagraphs(): List<DhikrParagraph> {
    val arr = JSONArray(this)
    return buildList {
        for (i in 0 until arr.length()) {
            val obj  = arr.getJSONObject(i)
            val text = obj.getString("text")
            add(when (obj.getString("type")) {
                "QURAN" -> DhikrParagraph.Quran(text)
                "NOTE"  -> DhikrParagraph.Note(text)
                else    -> DhikrParagraph.Body(text)
            })
        }
    }
}