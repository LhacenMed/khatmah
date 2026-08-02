package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors

/**
 * Bottom sheet listing the reader's bookmarks — the same source and layout as the bookmarks screen
 * (name over "page N, juz J") — so the user can jump between bookmarks without leaving the reader.
 * Tapping a row opens that page; an empty list shows a short hint.
 *
 * Rows are pre-resolved by the caller ([Row]) so the sheet stays a pure, stateless view.
 */
class BookmarksSheet(private val context: Context) {

    /** One row: its [page] plus the already-resolved [title] (label or sura) and [subtitle]. */
    data class Row(val page: Int, val title: String, val subtitle: String)

    fun show(rows: List<Row>, onOpen: (Int) -> Unit) {
        val d = context.resources.displayMetrics.density
        val onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0)
        val onVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        val dialog = BottomSheetDialog(context)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * d).toInt(), 0, (16 * d).toInt())
            addView(header("العلامات المرجعية", onVariant, d))
            if (rows.isEmpty()) {
                addView(hint("لا توجد علامات مرجعية", onVariant, d))
            } else {
                // Open on dismiss, not on tap: the sheet clears the page first so the reader's jump
                // animation plays in full view instead of behind a closing scrim.
                for (row in rows) addView(
                    rowView(row, onSurface, onVariant, d) {
                        dialog.setOnDismissListener { onOpen(row.page) }
                        dialog.dismiss()
                    }
                )
            }
        }

        dialog.setContentView(ScrollView(context).apply { addView(list) })
        dialog.show()
    }

    private fun header(text: String, color: Int, d: Float) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        includeFontPadding = false
        setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), (8 * d).toInt())
    }

    private fun hint(text: String, color: Int, d: Float) = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding((24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
    }

    private fun rowView(row: Row, titleColor: Int, subColor: Int, d: Float, onClick: () -> Unit): View {
        val title = TextView(context).apply {
            text = row.title
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            includeFontPadding = false
            maxLines = 1
        }
        val subtitle = TextView(context).apply {
            text = row.subtitle
            setTextColor(subColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            maxLines = 1
            visibility = if (row.subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableItemBackground())
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), (12 * d).toInt())
            addView(title)
            addView(subtitle)
            setOnClickListener { onClick() }
        }
    }

    /** Resolves `?attr/selectableItemBackground` so rows keep the platform touch ripple. */
    private fun selectableItemBackground(): Int {
        val out = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        return out.resourceId
    }
}
