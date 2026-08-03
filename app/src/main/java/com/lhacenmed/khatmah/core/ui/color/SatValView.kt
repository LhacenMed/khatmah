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
 * Saturation × value field of an HSV picker: saturation runs left to right, value bottom to top.
 *
 * Drawn as three stacked layers — the flat hue, a white-to-clear horizontal wash, and a
 * clear-to-black vertical wash — which is the standard way to get the full SV plane from two
 * gradients instead of shading every pixel by hand.
 *
 * Purely an input surface: it reports drags through [onChange] and paints whatever [hue],
 * [saturation] and [value] are set to, so the dialog stays the single owner of the colour.
 */
class SatValView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val huePaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    /** Reports a drag as saturation and value, both 0f..1f. */
    var onChange: ((saturation: Float, value: Float) -> Unit)? = null

    var hue: Float = 0f
        set(v) {
            if (field == v) return
            field = v
            huePaint.color = Color.HSVToColor(floatArrayOf(v, 1f, 1f))
            invalidate()
        }

    var saturation: Float = 1f
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    var value: Float = 1f
        set(v) {
            if (field == v) return
            field = v
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Shaders are bound to pixel bounds, so they are rebuilt only when those change.
        satPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        valPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, huePaint)
        canvas.drawRect(0f, 0f, w, h, satPaint)
        canvas.drawRect(0f, 0f, w, h, valPaint)

        // Two rings — dark outside, light inside — so the thumb stays visible on any colour.
        val cx = saturation * w
        val cy = (1f - value) * h
        val radius = dp(10f)
        thumbPaint.color = Color.BLACK
        canvas.drawCircle(cx, cy, radius + dp(2f), thumbPaint)
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (width == 0 || height == 0) return true
                // The dialog's scroll container must not steal the drag mid-gesture.
                parent?.requestDisallowInterceptTouchEvent(true)
                onChange?.invoke(
                    (event.x / width).coerceIn(0f, 1f),
                    1f - (event.y / height).coerceIn(0f, 1f),
                )
            }
            else -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics,
    )
}
