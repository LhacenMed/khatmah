package com.lhacenmed.khatmah.feature.adhkar.ui.detail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import com.lhacenmed.khatmah.R

/**
 * Circular repetition counter: a thin full track, a heavy progress arc starting at
 * 12 o'clock and sweeping clockwise, and the completed read count in the centre.
 *
 * Purely visual — [fraction] and [count] are pushed in by the reader, which owns the
 * animation and the counting rules.
 */
class RepCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackStroke    = dp(2.5f)
    private val progressStroke = dp(8f)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = trackStroke
        strokeCap   = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = progressStroke
        strokeCap   = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize  = sp(22f)
        typeface  = ResourcesCompat.getFont(context, R.font.noto_kufi_bold) ?: Typeface.DEFAULT_BOLD
    }

    private val arcBounds = RectF()

    /** Sweep of the progress arc, 0f..1f. */
    var fraction: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Reads completed for the current dhikr, shown in the centre. */
    var count: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Applies the app colour scheme; called once when the reader binds its chrome. */
    fun setColors(@ColorInt track: Int, @ColorInt progress: Int, @ColorInt text: Int) {
        trackPaint.color    = track
        progressPaint.color = progress
        textPaint.color     = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Both arcs share one circle, inset by the heavier stroke so nothing clips at the edges.
        val diameter = minOf(width, height) - progressStroke
        val left     = (width - diameter) / 2f
        val top      = (height - diameter) / 2f
        arcBounds.set(left, top, left + diameter, top + diameter)

        canvas.drawArc(arcBounds, START_ANGLE, 360f, false, trackPaint)
        if (fraction > 0f) {
            canvas.drawArc(arcBounds, START_ANGLE, 360f * fraction, false, progressPaint)
        }

        // Optical centre: shift the baseline by half the text's visual height.
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(count.toString(), width / 2f, baseline, textPaint)
    }

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
    )

    private fun sp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics,
    )

    private companion object {
        /** 12 o'clock, so the arc grows the way a stopwatch does. */
        const val START_ANGLE = -90f
    }
}
