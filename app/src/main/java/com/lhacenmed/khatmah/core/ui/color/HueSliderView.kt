package com.lhacenmed.khatmah.core.ui.color

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Hue strip of an HSV picker: the full 0–360° spectrum, selected by touching along its width.
 *
 * The thumb is a white bar flanked by two thin dark ones, so it reads against both the pale
 * and the saturated ends of the spectrum without needing a shadow.
 */
class HueSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val spectrumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint    = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Reports a drag as a hue in degrees, 0f..360f. */
    var onChange: ((hue: Float) -> Unit)? = null

    var hue: Float = 0f
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        spectrumPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f, SPECTRUM, null, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, spectrumPaint)

        val x = hue / 360f * w
        thumbPaint.color = Color.BLACK
        thumbPaint.strokeWidth = dp(1f)
        canvas.drawLine(x - dp(1.5f), 0f, x - dp(1.5f), h, thumbPaint)
        canvas.drawLine(x + dp(1.5f), 0f, x + dp(1.5f), h, thumbPaint)
        thumbPaint.color = Color.WHITE
        thumbPaint.strokeWidth = dp(3f)
        canvas.drawLine(x, 0f, x, h, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (width == 0) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                onChange?.invoke((event.x / width).coerceIn(0f, 1f) * 360f)
            }
            else -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
    )

    private companion object {
        /** Red through the wheel and back to red, so 0° and 360° meet seamlessly. */
        val SPECTRUM = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
            0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
        )
    }
}
