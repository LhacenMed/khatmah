package com.lhacenmed.khatmah.feature.adhkar.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.adhkar.data.Dhikr
import com.lhacenmed.khatmah.feature.adhkar.data.DhikrParagraph

/**
 * Backs the reader's pager: one page per dhikr, plus a final completion page — so the page
 * count is always `adhkar.size + 1`.
 *
 * Paragraph views are built at bind time because a dhikr's block count and mix of styles are
 * data-driven. Changing [fontSize] rebinds the dhikr pages in place, so the resize action
 * never disturbs the pager position.
 *
 * [onTap] receives a single tap anywhere on a dhikr page — the reader's main "I read it"
 * gesture. It is wired through a [GestureDetector] that lets the scroll view keep the event,
 * so tapping and scrolling coexist.
 */
class DhikrPagerAdapter(
    private val adhkar: List<Dhikr>,
    private val categoryName: String,
    scheme: ColorScheme,
    private val onTap: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val bodyColor    = scheme.onBackground.toArgb()
    private val accentColor  = scheme.primary.toArgb()
    private val noteColor    = ColorUtils.setAlphaComponent(accentColor, NOTE_ALPHA)

    private var bodyTypeface:  Typeface? = null
    private var quranTypeface: Typeface? = null

    /** Reading size for the dhikr pages; setting it rebinds them without moving the pager. */
    var fontSize: DhikrFontSize = DhikrFontSize.MEDIUM
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, adhkar.size)
        }

    override fun getItemCount() = adhkar.size + 1

    override fun getItemViewType(position: Int) =
        if (position < adhkar.size) TYPE_DHIKR else TYPE_COMPLETION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        loadTypefaces(parent.context)
        return if (viewType == TYPE_DHIKR) {
            DhikrHolder(inflater.inflate(R.layout.adhkar_dhikr_page, parent, false), onTap)
        } else {
            CompletionHolder(inflater.inflate(R.layout.adhkar_completion_page, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is DhikrHolder      -> bindDhikr(holder, adhkar[position])
            is CompletionHolder -> bindCompletion(holder)
        }
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindDhikr(holder: DhikrHolder, dhikr: Dhikr) {
        val container = holder.paragraphs
        val context   = container.context
        container.removeAllViews()

        val bodySp  = BODY_SP  * fontSize.bodyScale
        val quranSp = QURAN_SP * fontSize.quranScale
        val noteSp  = NOTE_SP  * fontSize.bodyScale

        dhikr.paragraphs.forEachIndexed { i, paragraph ->
            val view = TextView(context).apply {
                gravity = android.view.Gravity.START
                when (paragraph) {
                    is DhikrParagraph.Body -> {
                        text     = paragraph.text
                        typeface = bodyTypeface
                        applyText(bodySp, BODY_LINE_RATIO, bodyColor)
                    }
                    is DhikrParagraph.Quran -> {
                        typeface = quranTypeface
                        applyText(quranSp, QURAN_LINE_RATIO, bodyColor)
                        text = quranSpanned(
                            text         = paragraph.text,
                            numberColor  = accentColor,
                            numberSizePx = spToPx(context, AYA_NUMBER_SP),
                        )
                    }
                    is DhikrParagraph.Note -> {
                        text     = paragraph.text
                        typeface = bodyTypeface
                        applyText(noteSp, NOTE_LINE_RATIO, noteColor)
                    }
                }
            }
            container.addView(
                view,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { if (i > 0) topMargin = dpToPx(context, PARAGRAPH_GAP_DP) },
            )
        }

        // A new dhikr always starts from its first line.
        holder.itemView.scrollTo(0, 0)
    }

    private fun bindCompletion(holder: CompletionHolder) {
        holder.icon.setColorFilter(accentColor)
        holder.text.setTextColor(bodyColor)
        holder.text.text = holder.text.context.getString(R.string.adhkar_completed, categoryName)
    }

    /** Absolute line height (not a multiplier) keeps Arabic diacritics legible at every size. */
    private fun TextView.applyText(sizeSp: Float, lineRatio: Float, color: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        TextViewCompat.setLineHeight(this, spToPx(context, sizeSp * lineRatio))
    }

    private fun loadTypefaces(context: Context) {
        if (bodyTypeface == null) {
            bodyTypeface  = ResourcesCompat.getFont(context, R.font.noto_kufi_regular)
            quranTypeface = ResourcesCompat.getFont(context, R.font.kfgqpc_warsh_uthmanic)
        }
    }

    // ── Holders ───────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private class DhikrHolder(view: View, onTap: () -> Unit) : RecyclerView.ViewHolder(view) {
        val paragraphs: LinearLayout = view.findViewById(R.id.paragraphs)

        init {
            // Returning false leaves the scroll view in charge of the gesture, so the page
            // still scrolls; the detector only reports genuine taps (no drag, no fling).
            val detector = GestureDetector(
                view.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        onTap()
                        return false
                    }
                },
            ).apply {
                // Counting is a rapid, repeated gesture, so neither of the detector's slower
                // interpretations may swallow a tap: a double tap suppresses onSingleTapUp for
                // the second tap, and a long press suppresses it for a lingering one. Every
                // press that isn't a scroll must count.
                setOnDoubleTapListener(null)
                setIsLongpressEnabled(false)
            }
            view.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
        }
    }

    private class CompletionHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.completion_icon)
        val text: TextView  = view.findViewById(R.id.completion_text)
    }

    private companion object {
        const val TYPE_DHIKR      = 0
        const val TYPE_COMPLETION = 1

        const val BODY_SP        = 24f
        const val QURAN_SP       = 27f
        const val NOTE_SP        = 16f
        const val AYA_NUMBER_SP  = 25f

        const val BODY_LINE_RATIO  = 1.85f
        const val QURAN_LINE_RATIO = 2.0f
        const val NOTE_LINE_RATIO  = 1.65f

        const val PARAGRAPH_GAP_DP = 22f
        const val NOTE_ALPHA       = 191 // 0.75 opacity, matching the muted footnote tone

        fun spToPx(context: Context, sp: Float) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics,
        ).toInt()

        fun dpToPx(context: Context, dp: Float) = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics,
        ).toInt()
    }
}
