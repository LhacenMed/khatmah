package com.lhacenmed.khatmah.feature.quran.ui.reader.text

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.lhacenmed.khatmah.feature.quran.ui.reader.PageZoom
import com.lhacenmed.khatmah.feature.quran.ui.reader.toArNums
import kotlin.math.ln1p

/**
 * Native renderer for one text-reader page — the View port of the former Compose `PageContent`.
 * A vertically-centred column of sura headers (name between two rules), the basmala, and justified
 * RTL aya paragraphs. The background is left transparent so the pager's shared parchment/night
 * drawable shows through, keeping the text and book readers visually consistent.
 *
 * A tap anywhere toggles the host chrome via [onTap]; a long-press inside an aya resolves its
 * (sura, aya) via [onAyaLongPress]; [selectedAya] tints the playing verse.
 */
class TextPageView(context: Context) : LinearLayout(context) {

    var onTap: (() -> Unit)? = null
    var onAyaLongPress: ((sura: Int, aya: Int) -> Unit)? = null

    private var bodyFace: Typeface? = null
    private var headerFace: Typeface? = null
    private var basmala: String = ""
    private var centered = false
    private var runs: List<Run> = emptyList()

    // Each rendered aya paragraph, kept so selection highlight updates without a full rebuild.
    private val ayaViews = mutableListOf<AyaView>()

    private val density = resources.displayMetrics.density

    /**
     * Pinch-free zoom/pan for the page (double-tap toggles, drag pans while zoomed). The whole view
     * handles gestures, so a tap anywhere — text or margin — toggles the chrome. Active only in
     * portrait, where the page fits the viewport; landscape keeps the wrapping vertical scroller.
     */
    private val zoom = PageZoom(
        this,
        { resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT },
        { onTap?.invoke() },
        ::longPressAt,
    )

    var nightMode: Boolean = false
        set(value) { if (field != value) { field = value; applyColors() } }

    private var textBrightness = 255
    private var bgBrightness = 0

    fun setBrightness(text: Int, background: Int) {
        if (text == textBrightness && background == bgBrightness) return
        textBrightness = text; bgBrightness = background
        applyColors()
    }

    private var accentArgb = ACCENT_FALLBACK
    fun setAccentColor(argb: Int) { if (accentArgb != argb) { accentArgb = argb; applyColors() } }

    var selectedAya: Pair<Int, Int>? = null
        set(value) { if (field != value) { field = value; applyHighlights() } }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val pad = (16 * density).toInt()
        setPadding(pad)
    }

    /** Supplies the decoded page and the riwaya's faces + basmala text, then builds the views. */
    fun setPage(
        page: QuranPageData,
        bodyFace: Typeface?,
        headerFace: Typeface?,
        basmala: String,
    ) {
        this.bodyFace = bodyFace
        this.headerFace = headerFace
        this.basmala = basmala
        this.centered = page.centered
        this.runs = groupSegments(page.segments)
        zoom.reset() // a (re)used view starts at 1× so recycled pager pages never inherit a zoom
        build()
    }

    // ── Zoom/pan + page-level gestures ──────────────────────────────────────────────

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = zoom.onTouch(event)

    /** Children draw through the zoom transform; at 1× it is the identity. */
    override fun dispatchDraw(canvas: Canvas) {
        val saved = canvas.save()
        zoom.apply(canvas)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        zoom.reset() // bounds changed → any prior pan/zoom is no longer valid
    }

    /** Long-press resolves the pressed aya across paragraphs; coordinates arrive in page space. */
    private fun longPressAt(x: Float, y: Float) {
        for (av in ayaViews) {
            val v = av.view
            if (x >= v.left && x <= v.right && y >= v.top && y <= v.bottom) {
                val aya = av.ayaAt(x - v.left, y - v.top) ?: return
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onAyaLongPress?.invoke(aya.suraNum, aya.ayaNum)
                return
            }
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────────────

    private fun build() {
        removeAllViews()
        ayaViews.clear()
        for (run in runs) when (run) {
            is Run.Header  -> addView(headerRow(run.name))
            is Run.Basmala -> addView(lineText(basmala, header = false, accent = true))
            is Run.Ayas    -> addView(ayaParagraph(run))
        }
        applyColors()
        applyHighlights()
    }

    /** Sura name centred between two thin rules. */
    private fun headerRow(name: String): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val v = (6 * density).toInt()
            setPadding(0, v, 0, v)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        row.addView(rule())
        row.addView(TextView(context).apply {
            text = name
            typeface = headerFace
            setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_SP)
            val h = (12 * density).toInt()
            setPadding(h, 0, h, 0)
            tag = TAG_ACCENT
        })
        row.addView(rule())
        return row
    }

    private fun rule(): View = View(context).apply {
        layoutParams = LayoutParams(0, (0.8f * density).toInt(), 1f)
        tag = TAG_RULE
    }

    /** A single centred line (basmala). */
    private fun lineText(text: String, header: Boolean, accent: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            typeface = if (header) headerFace else bodyFace
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
            gravity = Gravity.CENTER
            val v = (2 * density).toInt()
            setPadding(0, v, 0, v)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            if (accent) tag = TAG_ACCENT
        }

    /** Flowing aya paragraph: justified RTL, with inline accent ayah numbers and per-aya hit test. */
    private fun ayaParagraph(run: Run.Ayas): TextView {
        val tv = TextView(context).apply {
            typeface = bodyFace
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = if (centered) Gravity.CENTER else Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !centered) {
                justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
            }
            setLineSpacing(LINE_SPACING_EXTRA_SP * density, 1f)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val av = AyaView(tv, run.ayas)
        ayaViews += av
        return tv
    }

    // ── Colours / highlight ─────────────────────────────────────────────────────

    private fun nightAlpha(): Int =
        (50f * ln1p(bgBrightness.toDouble()).toFloat() + textBrightness).toInt().coerceAtMost(255)

    private fun applyColors() {
        val alpha = nightAlpha()
        val textColor = if (nightMode) Color.argb(alpha, 255, 255, 255) else DAY_TEXT
        val accent = if (nightMode) scaleAlpha(accentArgb, alpha) else accentArgb
        val ruleColor = if (nightMode) scaleAlpha(0xFFFFFFFF.toInt(), alpha / 4) else RULE_DAY

        tintChildren(this, textColor, accent, ruleColor)
        applyHighlights() // re-render aya spannables with new colours
    }

    private fun tintChildren(group: View, textColor: Int, accent: Int, ruleColor: Int) {
        if (group is LinearLayout) {
            for (i in 0 until group.childCount) tintChildren(group.getChildAt(i), textColor, accent, ruleColor)
            return
        }
        when (group.tag) {
            TAG_RULE   -> group.setBackgroundColor(ruleColor)
            TAG_ACCENT -> (group as? TextView)?.setTextColor(accent)
            else       -> (group as? TextView)?.setTextColor(textColor)
        }
    }

    /** Rebuilds each aya paragraph's spannable so the playing verse is tinted. */
    private fun applyHighlights() {
        val alpha = nightAlpha()
        val textColor = if (nightMode) Color.argb(alpha, 255, 255, 255) else DAY_TEXT
        val accent = if (nightMode) scaleAlpha(accentArgb, alpha) else accentArgb
        val sel = selectedAya
        val numSizePx = (BODY_SP * 0.9f * resources.displayMetrics.scaledDensity).toInt()

        for (av in ayaViews) {
            val sb = SpannableStringBuilder()
            av.ranges.clear()
            av.ayas.forEachIndexed { i, aya ->
                val start = sb.length
                val highlighted = sel?.first == aya.suraNum && sel.second == aya.ayaNum
                sb.append(aya.text)
                sb.setSpan(ForegroundColorSpan(if (highlighted) accent else textColor),
                    start, sb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(" ")
                val numStart = sb.length
                sb.append(toArNums(aya.ayaNum))
                sb.setSpan(ForegroundColorSpan(accent), numStart, sb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(AbsoluteSizeSpan(numSizePx), numStart, sb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                val end = sb.length
                if (highlighted) {
                    sb.setSpan(BackgroundColorSpan(scaleAlpha(accent, 0x1F)), start, end,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                av.ranges += Triple(start, end, aya)
                if (i < av.ayas.lastIndex) sb.append(" ")
            }
            av.view.text = sb
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private fun scaleAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** One aya paragraph view + the char ranges that map a touch offset back to an aya. */
    private class AyaView(val view: TextView, val ayas: List<QuranSegment.Aya>) {
        val ranges = mutableListOf<Triple<Int, Int, QuranSegment.Aya>>()

        fun ayaAt(x: Float, y: Float): QuranSegment.Aya? {
            val layout = view.layout ?: return null
            val line = layout.getLineForVertical((y - view.totalPaddingTop).toInt())
            val off = layout.getOffsetForHorizontal(line, x - view.totalPaddingLeft)
            return ranges.firstOrNull { (s, e, _) -> off in s until e }?.third
        }
    }

    private sealed interface Run {
        data class Header(val name: String) : Run
        data object Basmala : Run
        data class Ayas(val ayas: List<QuranSegment.Aya>) : Run
    }

    /** Collapses segments into runs; consecutive ayas merge into one paragraph. */
    private fun groupSegments(segments: List<QuranSegment>): List<Run> {
        val out = mutableListOf<Run>()
        val buf = mutableListOf<QuranSegment.Aya>()
        fun flush() { if (buf.isNotEmpty()) { out += Run.Ayas(buf.toList()); buf.clear() } }
        for (seg in segments) when (seg) {
            is QuranSegment.SuraHeader -> { flush(); out += Run.Header(seg.name) }
            is QuranSegment.Basmala    -> { flush(); out += Run.Basmala }
            is QuranSegment.Aya        -> buf += seg
        }
        flush()
        return out
    }

    companion object {
        private const val BODY_SP = 23f
        private const val HEADER_SP = 26f
        private const val LINE_SPACING_EXTRA_SP = 6f
        private const val DAY_TEXT = 0xFF1A1A1A.toInt()
        private const val RULE_DAY = 0x33000000
        private const val ACCENT_FALLBACK = 0xFF047857.toInt()

        private const val TAG_ACCENT = "accent"
        private const val TAG_RULE = "rule"
    }
}
