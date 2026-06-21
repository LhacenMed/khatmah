package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Reusable double-tap zoom + drag-pan for a reader page, shared by the book and text readers so both
 * behave identically.
 *
 * The page draws itself in its own (content) coordinates and calls [apply] inside its draw pass;
 * [PageZoom] maps screen↔content via [toContentX]/[toContentY] for hit-testing, and suspends pager
 * paging while zoomed. The single-tap [onTap] stays immediate — it never waits to disambiguate a
 * double-tap — while a long-press is reported through [onLongPressAt] in content coordinates.
 *
 * Feed every touch from the host view's `onTouchEvent` into [onTouch]. Zoom only engages while
 * [enabled] (e.g. portrait, where the page fills the viewport); otherwise the host's own scroller
 * keeps its normal behaviour. Translation is always clamped so the zoomed page keeps covering the
 * view (no empty margins).
 */
class PageZoom(
    private val target: View,
    private val enabled: () -> Boolean,
    private val onTap: () -> Unit,
    private val onLongPressAt: (x: Float, y: Float) -> Unit,
) {
    var scale = 1f
        private set
    private var transX = 0f
    private var transY = 0f

    /** True once meaningfully zoomed in — gates panning and the pager hand-off. */
    val isZoomed: Boolean get() = scale > 1f + EPS

    private var animator: ValueAnimator? = null

    private val detector = GestureDetector(target.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { onTap(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!enabled()) return false
            toggle(e.x, e.y)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            if (!isZoomed) return false // not zoomed → let the host scroller / pager handle the drag
            pan(dX, dY)
            return true
        }
        override fun onLongPress(e: MotionEvent) = onLongPressAt(toContentX(e.x), toContentY(e.y))
    })

    /** Route the host view's touches here; holds the pager/scroller off while zoomed. */
    fun onTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            target.parent?.requestDisallowInterceptTouchEvent(isZoomed)
        }
        return detector.onTouchEvent(event)
    }

    fun toContentX(screenX: Float): Float = (screenX - transX) / scale
    fun toContentY(screenY: Float): Float = (screenY - transY) / scale

    /** Apply the current transform inside the host's draw pass; the identity at 1×. */
    fun apply(canvas: Canvas) {
        canvas.translate(transX, transY)
        canvas.scale(scale, scale)
    }

    fun reset() {
        animator?.cancel()
        if (scale == 1f && transX == 0f && transY == 0f) return
        scale = 1f; transX = 0f; transY = 0f
        target.invalidate()
    }

    /** Double-tap: zoom into ([fx],[fy]) keeping that spot fixed, or animate back to 1×. */
    private fun toggle(fx: Float, fy: Float) {
        if (isZoomed) {
            animateTo(1f, 0f, 0f)
        } else {
            val cx = toContentX(fx)
            val cy = toContentY(fy)
            val goal = MAX_SCALE
            clampTo(goal, fx - cx * goal, fy - cy * goal) { tx, ty -> animateTo(goal, tx, ty) }
        }
    }

    /** Drag-to-pan while zoomed; [dX]/[dY] are gesture distances (old − new). */
    private fun pan(dX: Float, dY: Float) {
        animator?.cancel()
        clampTo(scale, transX - dX, transY - dY) { tx, ty ->
            transX = tx; transY = ty; target.invalidate()
        }
    }

    /** Clamps ([tx],[ty]) for [s] so the page edges never pull inside the view, then emits it. */
    private inline fun clampTo(s: Float, tx: Float, ty: Float, out: (Float, Float) -> Unit) {
        val minX = target.width - target.width * s
        val minY = target.height - target.height * s
        out(tx.coerceIn(minX, 0f), ty.coerceIn(minY, 0f))
    }

    private fun animateTo(s: Float, tx: Float, ty: Float) {
        animator?.cancel()
        val s0 = scale; val x0 = transX; val y0 = transY
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                scale = s0 + (s - s0) * f
                transX = x0 + (tx - x0) * f
                transY = y0 + (ty - y0) * f
                target.invalidate()
            }
            start()
        }
    }

    private companion object {
        const val MAX_SCALE = 2.6f // double-tap zoom level
        const val ANIM_MS = 220L
        const val EPS = 0.01f
    }
}
