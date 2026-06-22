package com.lhacenmed.khatmah.feature.quran.ui.reader.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.lhacenmed.khatmah.feature.quran.data.Riwaya
import com.lhacenmed.khatmah.feature.quran.data.Qcf4Page
import com.lhacenmed.khatmah.feature.quran.ui.reader.PageZoom
import kotlin.math.ln1p

/**
 * Custom view that draws one QCF4 mushaf page on the parchment "book" gradient whose binding side
 * flips by page parity (even → right, odd → left). A single tap is forwarded via [onTap] so the
 * host activity can toggle its immersive chrome; a long-press is resolved to its (sura, aya) via
 * [onAyaLongPress], and [selectedAya] highlights the playing verse.
 *
 * Double-tap zooms into the tapped spot (and again to zoom back out); while zoomed a drag pans the
 * page and horizontal paging is suspended ([Zoom]). The single-tap chrome toggle stays immediate —
 * it never waits to disambiguate a double-tap.
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

    // ── Night-mode brightness (0..255) ───────────────────────────────────────────

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
    // QCF4_QBSML running-head glyphs; empty → fall back to the plain-text header above.
    private var headerSuraGlyph = ""
    private var headerJuzGlyph = ""

    /**
     * Supplies the page-info band content: the QCF4_QBSML sura/juz glyphs (preferred) plus their
     * plain-text equivalents used as a fallback when a glyph is unavailable, and the page number.
     */
    fun setPageInfo(
        suraGlyph: String,
        juzGlyph: String,
        suraText: String,
        juzText: String,
        page: String,
    ) {
        headerSuraGlyph = suraGlyph
        headerJuzGlyph = juzGlyph
        headerSura = suraText
        headerJuz = juzText
        footerPage = page
        invalidate()
    }

    // ── Colors (resolved once) ────────────────────────────────────────────────────

    private var accentArgb: Int = ACCENT_FALLBACK

    private val density = resources.displayMetrics.density
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val solidPaint = Paint().apply { color = NIGHT_BG }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Synthetic "stacked pages" fore-edge — depth shadow + sheet hairlines.
    private val edgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density }
    private val bindPaint = Paint() // spine gutter line (filled, flush to the edge)
    /** Fore-edge side: even pages → right, odd pages → left (real-book stacking). */
    private var stackOnRight = true

    /**
     * Pinch-free zoom/pan for the page (double-tap toggles, drag pans while zoomed). Active only in
     * portrait (fill-parent); landscape uses its own vertical scroller, so the gate is [pageAspect].
     */
    private val zoom = PageZoom(this, { pageAspect == 0f }, { onTap?.invoke() }, ::longPressAt)

    /** Long-press resolves the pressed word's (sura, aya); coordinates arrive in page space. */
    private fun longPressAt(x: Float, y: Float) {
        val cb    = onAyaLongPress ?: return
        val key   = hitWord(x, y)?.verseKey ?: return
        val parts = key.split(":")
        if (parts.size != 2) return
        val sura = parts[0].toIntOrNull() ?: return
        val aya  = parts[1].toIntOrNull() ?: return
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cb(sura, aya)
    }

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
        zoom.reset() // a (re)used view starts at 1× so recycled pager pages never inherit a zoom
        rebuildLayout()
        invalidate()
    }

    /**
     * When > 0, the page measures to width × this ratio instead of filling its parent — used in
     * landscape, where the view is placed in a vertical scroller so the full-width page keeps its
     * portrait proportions and is revealed by scrolling. 0 = fill the parent (portrait).
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
        zoom.reset() // bounds changed → any prior pan/zoom is no longer valid
        rebuildLayout()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = zoom.onTouch(event)

    // ── Drawing ───────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Zoom the whole page as one unit (background, text, overlay) so it reads like zooming a
        // photo of the sheet; at 1× the transform is the identity.
        val saved = canvas.save()
        zoom.apply(canvas)

        // Background: solid dark in night mode, otherwise the parity parchment gradient.
        if (nightMode) {
            canvas.drawRect(0f, 0f, w, h, solidPaint)
        } else {
            canvas.drawRect(0f, 0f, w, h, gradientPaint)
        }

        drawStackedEdge(canvas, w, h)

        val sel = selectedAya
        // Pre-compute highlight color once per draw (avoid repeated object creation per word).
        if (sel != null) {
            highlightPaint.color = Color.argb(
                0x33,
                Color.red(accentArgb),
                Color.green(accentArgb),
                Color.blue(accentArgb),
            )
        }

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

            // Draw one highlight rect per line (not per word) so highlights on multi-word and
            // multi-line verses never overlap each other. The rect is anchored to the slot's
            // lineTop/lineHeight so the band is always tight and consistent across line types.
            if (sel != null) {
                var spanLeft = Float.MAX_VALUE
                var spanRight = Float.MIN_VALUE
                for (word in line.words) {
                    val key = word.verseKey ?: continue
                    val parts = key.split(":")
                    if (parts.size == 2 &&
                        parts[0].toIntOrNull() == sel.first &&
                        parts[1].toIntOrNull() == sel.second
                    ) {
                        if (word.x < spanLeft) spanLeft = word.x
                        val right = word.x + word.width
                        if (right > spanRight) spanRight = right
                    }
                }
                if (spanLeft < spanRight) {
                    val vInset = line.lineHeight * HIGHLIGHT_V_INSET
                    canvas.drawRect(
                        spanLeft,
                        line.lineTop + vInset,
                        spanRight,
                        line.lineTop + line.lineHeight - vInset,
                        highlightPaint,
                    )
                }
            }

            for (word in line.words) {
                canvas.drawText(word.char, word.x, word.baseline, word.paint)
            }
        }

        if (showPageInfo) drawPageInfo(canvas, w, h)

        canvas.restoreToCount(saved)
    }

    /**
     * Draws the page-info overlay: a top band with the sura name (start) and juz (end) — rendered
     * with the calligraphic QCF4_QBSML running-head glyphs, falling back to plain text — and a
     * bottom band with the centered page number.
     */
    private fun drawPageInfo(canvas: Canvas, w: Float, h: Float) {
        val color = if (nightMode) scaleAlpha(OVERLAY_NIGHT, nightAlpha) else OVERLAY_DAY
        overlayPaint.color = color
        overlayPaint.textSize = minOf(w, 480f * density) * 0.0345f
        val margin = w * 0.04f
        val headerY = h * 0.07f
        val footerY = h * 0.96f

        // Sura (start edge) and juz (end edge). Glyphs are wider than text, so each side is capped.
        drawHeaderSide(canvas, headerSuraGlyph, headerSura, margin, Paint.Align.LEFT, w * 0.52f, color, headerY, w)
        drawHeaderSide(canvas, headerJuzGlyph, headerJuz, w - margin, Paint.Align.RIGHT, w * 0.34f, color, headerY, w)

        if (footerPage.isNotEmpty()) {
            overlayPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(footerPage, w / 2f, footerY, overlayPaint)
        }
    }

    /**
     * Draws one side of the page-info band: the QCF4_QBSML [glyph] when present and its face is
     * loaded (scaled down to fit [maxW]), otherwise the plain-text [fallback].
     */
    private fun drawHeaderSide(
        canvas: Canvas,
        glyph: String,
        fallback: String,
        x: Float,
        align: Paint.Align,
        maxW: Float,
        color: Int,
        y: Float,
        w: Float,
    ) {
        val face = faces["QCF4_QBSML"]
        if (glyph.isNotEmpty() && face != null && face != Typeface.DEFAULT) {
            headerGlyphPaint.typeface = face
            headerGlyphPaint.color = color
            headerGlyphPaint.textAlign = align
            val base = minOf(w, 480f * density) * 0.055f
            headerGlyphPaint.textSize = base
            val measured = headerGlyphPaint.measureText(glyph)
            if (measured > maxW && measured > 0f) headerGlyphPaint.textSize = base * (maxW / measured)
            canvas.drawText(glyph, x, y, headerGlyphPaint)
        } else {
            overlayPaint.textAlign = align
            canvas.drawText(fallback, x, y, overlayPaint)
        }
    }

    /** Returns [color] with its alpha replaced by [alpha] (0..255), keeping the RGB. */
    private fun scaleAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Draws the synthetic stacked-pages fore-edge: a soft depth shadow fading inward plus a few
     * sheet hairlines, hugging the right edge on even pages and the left on odd pages.
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

        // Binding gutter: a thin line flush to the spine edge (opposite the fore-edge stack), so
        // the lines of two paired pages meet seamlessly during a swipe instead of showing a gap.
        val bindW = BIND_LINE_W_DP * density
        val bx = if (stackOnRight) 0f else w - bindW
        bindPaint.color = if (nightMode) BIND_LINE_NIGHT else BIND_LINE_DAY
        canvas.drawRect(bx, 0f, bx + bindW, h, bindPaint)
    }

    // ── Layout ──────────────────────────────────────────────────────────────────

    private fun rebuildLayout() {
        val p = page
        if (p == null || width == 0 || height == 0) { layout = emptyList(); return }

        // Page pairing: odd pages are right-hand pages (page 1 is the opening right page), so the
        // fore-edge stack hugs the right and the spine/gutter sits on the left.
        val rightHand = p.page % 2 == 1
        stackOnRight = rightHand

        // Night-mode colours from brightness: the background is a solid grey at [bgBrightness]; the
        // text is white whose alpha is the text brightness lifted slightly by the background
        // brightness so it never disappears on a dark page.
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

        // Page-info overlay text colour.
        private const val OVERLAY_DAY = 0xFF686E7D.toInt()
        private const val OVERLAY_NIGHT = 0xFF848A91.toInt()

        // Verse highlight band: fraction of the line slot height inset from top and bottom.
        private const val HIGHLIGHT_V_INSET = 0.02f
    }
}
