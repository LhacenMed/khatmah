package com.lhacenmed.khatmah.feature.quran.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Draws the reader-settings preview aya — the [PreviewRun]s laid out right-to-left and centred,
 * exactly how the book reader places QCF4 words (each glyph carries its own face, and the glyph
 * codepoints are positioned by us rather than by the bidi algorithm). A single text run is drawn
 * as one string, so plain Arabic keeps its native shaping.
 *
 * The runs are measured once at [BASE_SP] and uniformly scaled down to fit the width, so the sample
 * never clips and every draw is a flat loop over pre-placed words.
 */
class AyaPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baseSizePx =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BASE_SP, resources.displayMetrics)

    private var runs: List<PreviewRun> = emptyList()
    private var placed: List<PlacedRun> = emptyList()
    private var baseline = 0f

    /** Colour of the sample text — the night text brightness. */
    var textArgb: Int = Color.WHITE
        set(value) { if (field != value) { field = value; invalidate() } }

    /** Supplies the resolved sample; safe to call before or after the view is measured. */
    fun setRuns(runs: List<PreviewRun>) {
        this.runs = runs
        layoutRuns()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (baseSizePx * LINE_HEIGHT_RATIO).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutRuns()
    }

    /** Measures at the base size, scales to fit, then places each run right-to-left from centre. */
    private fun layoutRuns() {
        val available = width - paddingLeft - paddingRight
        if (available <= 0 || runs.isEmpty()) {
            placed = emptyList()
            return
        }

        paint.textSize = baseSizePx
        val widths = FloatArray(runs.size) { i ->
            paint.typeface = runs[i].typeface
            paint.measureText(runs[i].text)
        }
        val total = widths.sum()
        if (total <= 0f) {
            placed = emptyList()
            return
        }

        val scale = min(1f, available / total)
        val size = baseSizePx * scale
        var x = (width + total * scale) / 2f
        placed = runs.mapIndexed { i, run ->
            x -= widths[i] * scale
            PlacedRun(run.text, run.typeface, x, size)
        }

        paint.textSize = size
        paint.typeface = runs.first().typeface
        val fm = paint.fontMetrics
        baseline = height / 2f - (fm.ascent + fm.descent) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = textArgb
        for (run in placed) {
            paint.typeface = run.typeface
            paint.textSize = run.size
            canvas.drawText(run.text, run.x, baseline, paint)
        }
    }

    /** A run resolved to its final position and size — draw is then a bare loop. */
    private class PlacedRun(
        val text: String,
        val typeface: Typeface,
        val x: Float,
        val size: Float,
    )

    private companion object {
        const val BASE_SP = 26f
        const val LINE_HEIGHT_RATIO = 1.9f
    }
}
