package com.lhacenmed.khatmah.feature.quran.ui.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.lhacenmed.khatmah.feature.mushaf.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page
import kotlin.math.ln1p

/**
 * Custom view that draws one QCF4 mushaf page — the View/Canvas port of the
 * Compose `QuranQcf4Page` renderer, dropped into Quran Android's page-fragment
 * shell in place of `HighlightingImageView`.
 *
 * The page is painted on the parchment "book" gradient whose binding side flips
 * by page parity (even → right, odd → left), exactly like Quran Android's
 * line-by-line `pageGradient`. A single tap is forwarded via [onTap] so the host
 * activity can toggle its immersive chrome; a long-press is resolved to its (sura, aya)
 * via [onAyaLongPress], and [selectedAya] highlights the playing verse.
 */
class BookPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // ── Page state ──────────────────────────────────────────────────────────────

    private var page: Qcf4Page? = null
    private var faces: Map<String, Typeface> = emptyMap()
    private var riwaya: Riwaya = Riwaya.HAFS
    private var calligraphicFace: Typeface = Typeface.DEFAULT
    private var layout: List<LineRender> = emptyList()

    /** Toggled by the host so night mode swaps the parchment for a solid dark page. */
    var nightMode: Boolean = false
        set(value) { if (field != value) { field = value; rebuildLayout(); invalidate() } }

    /** Tap callback for immersive-chrome toggling. */
    var onTap: (() -> Unit)? = null

    /** Long-press callback carrying the (sura, aya) of the pressed word — drives audio playback. */
    var onAyaLongPress: ((sura: Int, aya: Int) -> Unit)? = null

    /** The currently playing/selected verse to highlight, or null. Only the page that owns it draws. */
    var selectedAya: Pair<Int, Int>? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ── Night-mode brightness (0..255), Quran Android semantics ───────────────────

    private var textBrightness = 255
    private var bgBrightness = 0
    private var nightTextArgb = NIGHT_TEXT
    private var nightAlpha = 255 // night-mode text-brightness alpha, shared by all page text

    /** Applies night-mode text/background brightness (only visible in night mode). */
    fun setBrightness(text: Int, background: Int) {
        if (text == textBrightness && background == bgBrightness) return
        textBrightness = text
        bgBrightness = background
        rebuildLayout()
        invalidate()
    }

    // ── Page-info overlay (sura name + juz header, page-number footer) ─────────────

    var showPageInfo: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    private var headerSura = ""
    private var headerJuz = ""
    private var footerPage = ""

    /** Supplies the overlay text for this page. */
    fun setPageInfo(sura: String, juz: String, page: String) {
        headerSura = sura
        headerJuz = juz
        footerPage = page
        invalidate()
    }

    // ── Colors (resolved once) ────────────────────────────────────────────────────

    private var accentArgb: Int = ACCENT_FALLBACK

    private val density = resources.displayMetrics.density
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val solidPaint = Paint().apply { color = NIGHT_BG }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Synthetic "stacked pages" fore-edge — depth shadow + sheet hairlines.
    private val edgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density }
    private val bindPaint = Paint() // spine gutter line (filled, flush to the edge)
    /** Fore-edge side: even pages → right, odd pages → left (real-book stacking). */
    private var stackOnRight = true

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) {
            val cb    = onAyaLongPress ?: return
            val key   = hitWord(e.x, e.y)?.verseKey ?: return
            val parts = key.split(":")
            if (parts.size != 2) return
            val sura = parts[0].toIntOrNull() ?: return
            val aya  = parts[1].toIntOrNull() ?: return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            cb(sura, aya)
        }
    })

    /** Returns the word whose glyph box contains ([x], [y]) in view coordinates, or null. */
    private fun hitWord(x: Float, y: Float): WordRender? {
        for (line in layout) {
            for (word in line.words) {
                val top    = word.baseline + word.paint.ascent()
                val bottom = word.baseline + word.paint.descent()
                if (x in word.x..(word.x + word.width) && y in top..bottom) return word
            }
        }
        return null
    }

    /** Sets the accent (sura headers / ayah markers) colour from the host theme. */
    fun setAccentColor(argb: Int) {
        if (accentArgb != argb) { accentArgb = argb; rebuildLayout(); invalidate() }
    }

    /**
     * Supplies the decoded page, its required typefaces and the calligraphic face
     * (KFGQPC, used for the sura-info labels inside the header glyph circles).
     */
    fun setPage(page: Qcf4Page, faces: Map<String, Typeface>, riwaya: Riwaya, calligraphicFace: Typeface) {
        this.page = page
        this.faces = faces
        this.riwaya = riwaya
        this.calligraphicFace = calligraphicFace
        rebuildLayout()
        invalidate()
    }

    /**
     * When > 0, the page measures to width × this ratio instead of filling its parent — used
     * in landscape, where the view is placed in a vertical scroller so the full-width page keeps
     * its portrait proportions and is revealed by scrolling. 0 = fill the parent (portrait).
     */
    var pageAspect: Float = 0f
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pageAspect > 0f) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, (w * pageAspect).toInt())
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)

    // ── Drawing ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Background: solid dark in night mode, otherwise the parity parchment gradient.
        if (nightMode) {
            canvas.drawRect(0f, 0f, w, h, solidPaint)
        } else {
            canvas.drawRect(0f, 0f, w, h, gradientPaint)
        }

        drawStackedEdge(canvas, w, h)

        for (line in layout) {
            line.container?.let { ctr ->
                canvas.drawText(CONTAINER_CHAR, ctr.x, ctr.baseline, ctr.paint)
                ctr.circles?.let { c ->
                    canvas.drawText("ترتيبها", c.orderCx, c.labelY, c.labelPaint)
                    canvas.drawText(c.orderStr, c.orderCx, c.numY, c.numPaint)
                    canvas.drawText("آياتها", c.ayaCx, c.labelY, c.labelPaint)
                    canvas.drawText(c.ayaStr, c.ayaCx, c.numY, c.numPaint)
                }
            }
            for (word in line.words) {
                val sel = selectedAya
                val key = word.verseKey
                if (sel != null && key != null) {
                    val parts = key.split(":")
                    if (parts.size == 2 &&
                        parts[0].toIntOrNull() == sel.first &&
                        parts[1].toIntOrNull() == sel.second
                    ) {
                        highlightPaint.color =
                            Color.argb(0x33, Color.red(accentArgb), Color.green(accentArgb), Color.blue(accentArgb))
                        val top    = word.baseline + word.paint.ascent() - 4f
                        val bottom = word.baseline + word.paint.descent() + 4f
                        canvas.drawRect(word.x, top, word.x + word.width, bottom, highlightPaint)
                    }
                }
                canvas.drawText(word.char, word.x, word.baseline, word.paint)
            }
        }

        if (showPageInfo) drawPageInfo(canvas, w, h)
    }

    /**
     * Draws the page-info overlay matching Quran Android's `QuranHeaderFooter`: a top band
     * with the sura name (start) and juz (end), and a bottom band with the centered page number.
     */
    private fun drawPageInfo(canvas: Canvas, w: Float, h: Float) {
        overlayPaint.color = if (nightMode) scaleAlpha(OVERLAY_NIGHT, nightAlpha) else OVERLAY_DAY
        overlayPaint.textSize = minOf(w, 480f * density) * 0.0345f
        val margin = w * 0.04f
        val headerY = h * 0.07f
        val footerY = h * 0.96f

        overlayPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(headerSura, margin, headerY, overlayPaint)
        overlayPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(headerJuz, w - margin, headerY, overlayPaint)

        if (footerPage.isNotEmpty()) {
            overlayPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(footerPage, w / 2f, footerY, overlayPaint)
        }
    }

    /** Returns [color] with its alpha replaced by [alpha] (0..255), keeping the RGB. */
    private fun scaleAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Draws the synthetic stacked-pages fore-edge: a soft depth shadow fading inward
     * plus a few sheet hairlines, hugging the right edge on even pages and the left
     * edge on odd pages — the real-book stacking direction, drawn from page data alone.
     */
    private fun drawStackedEdge(canvas: Canvas, w: Float, h: Float) {
        val stripW = EDGE_STRIP_DP * density
        val left = if (stackOnRight) w - stripW else 0f
        canvas.drawRect(left, 0f, left + stripW, h, edgeShadowPaint)

        edgeLinePaint.color = if (nightMode) EDGE_LINE_NIGHT else EDGE_LINE_DAY
        for (i in 1..EDGE_SHEETS) {
            val x = if (stackOnRight) w - i * EDGE_SHEET_GAP_DP * density
            else i * EDGE_SHEET_GAP_DP * density
            canvas.drawLine(x, 0f, x, h, edgeLinePaint)
        }

        // Binding gutter: a thin line flush to the spine edge (opposite the fore-edge
        // stack), so the lines of two paired pages meet seamlessly during a swipe
        // instead of showing a gap between them.
        val bindW = BIND_LINE_W_DP * density
        val bx = if (stackOnRight) 0f else w - bindW
        bindPaint.color = if (nightMode) BIND_LINE_NIGHT else BIND_LINE_DAY
        canvas.drawRect(bx, 0f, bx + bindW, h, bindPaint)
    }

    // ── Layout ──────────────────────────────────────────────────────────────────

    private fun rebuildLayout() {
        val p = page
        if (p == null || width == 0 || height == 0) { layout = emptyList(); return }

        // Page pairing: odd pages are right-hand pages (page 1 is the opening right page),
        // so the fore-edge stack hugs the right and the spine/gutter sits on the left.
        val rightHand = p.page % 2 == 1
        stackOnRight = rightHand

        // Night-mode colours from brightness (Quran Android formula): the background is a
        // solid grey at [bgBrightness]; the text is white whose alpha is the text brightness
        // lifted slightly by the background brightness so it never disappears on a dark page.
        if (nightMode) {
            solidPaint.color = Color.rgb(bgBrightness, bgBrightness, bgBrightness)
            nightAlpha = (50f * ln1p(bgBrightness.toDouble()).toFloat() + textBrightness)
                .toInt().coerceAtMost(255)
            nightTextArgb = Color.argb(nightAlpha, 255, 255, 255)
        }

        // Parchment gradient: the lighter centre leans toward the fore-edge (stack side).
        if (!nightMode) {
            val startWithWidth = rightHand
            val x0 = if (startWithWidth) width.toFloat() else 0f
            val x1 = if (startWithWidth) 0f else width.toFloat()
            gradientPaint.shader = LinearGradient(
                x0, 0f, x1, 0f,
                intArrayOf(PARCHMENT_EDGE, PARCHMENT_CENTER, PARCHMENT_EDGE),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.REPEAT,
            )
        }

        // Fore-edge depth shadow: darkest at the outer edge, fading toward the text.
        val stripW = EDGE_STRIP_DP * density
        val shadow = if (nightMode) EDGE_SHADOW_NIGHT else EDGE_SHADOW_DAY
        edgeShadowPaint.shader = if (stackOnRight) {
            LinearGradient(width - stripW, 0f, width.toFloat(), 0f, TRANSPARENT, shadow, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, 0f, stripW, 0f, shadow, TRANSPARENT, Shader.TileMode.CLAMP)
        }

        // Text brightness governs ALL page text in night mode: plain words, the accent-coloured
        // ligatures (sura headers / basmala / ayah markers) and the page-info overlay alike.
        val textArgb = if (nightMode) nightTextArgb else DAY_TEXT
        val accent = if (nightMode) scaleAlpha(accentArgb, nightAlpha) else accentArgb
        layout = computeLayout(
            page = p,
            faces = faces,
            w = width.toFloat(),
            h = height.toFloat(),
            textArgb = textArgb,
            accentArgb = accent,
            riwaya = riwaya,
            kfgqpcFace = calligraphicFace,
        )
    }

    companion object {
        const val PARCHMENT_EDGE = 0xFFF0EADF.toInt()
        const val PARCHMENT_CENTER = 0xFFFFFEFA.toInt()
        const val DAY_TEXT = 0xFF000000.toInt()
        const val NIGHT_BG = 0xFF101010.toInt()
        const val NIGHT_TEXT = 0xFFEDEDED.toInt()
        const val ACCENT_FALLBACK = 0xFF047857.toInt()

        // Synthetic stacked-pages fore-edge.
        private const val TRANSPARENT = 0x00000000
        private const val EDGE_STRIP_DP = 12f      // depth-shadow strip width
        private const val EDGE_SHEETS = 3          // visible sheet hairlines
        private const val EDGE_SHEET_GAP_DP = 2.5f // spacing between sheets
        private const val EDGE_SHADOW_DAY = 0x22332211
        private const val EDGE_SHADOW_NIGHT = 0x33000000
        private const val EDGE_LINE_DAY = 0x14000000
        private const val EDGE_LINE_NIGHT = 0x1AFFFFFF
        private const val BIND_LINE_W_DP = 0.3f      // gutter line width, flush to the spine edge
        private const val BIND_LINE_DAY = 0x33000000
        private const val BIND_LINE_NIGHT = 0x33FFFFFF

        // Page-info overlay text colour (Quran Android's overlayColor).
        private const val OVERLAY_DAY = 0xFF686E7D.toInt()
        private const val OVERLAY_NIGHT = 0xFF848A91.toInt()
    }
}
