package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    /** Target (scale, transX, transY) of the running animation, committed when it ends. */
    private var animEnd: Triple<Float, Float, Float>? = null

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
        cancelAnim()
        if (scale == 1f && transX == 0f && transY == 0f && target.scaleX == 1f && target.translationX == 0f) return
        commit(1f, 0f, 0f)
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
        cancelAnim()
        // Panning keeps the scale constant, so the canvas path stays smooth (glyphs stay cached).
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

    /**
     * Animates to ([s],[tx],[ty]). The page is heavy to rasterise (a full mushaf of custom glyphs),
     * so instead of re-running the canvas transform every frame — which re-rasterises every glyph at
     * a new size and drops frames — we freeze the canvas at the start state and animate the view's
     * own transform on a hardware layer, letting the GPU composite the scale for free. When the
     * animation ends we commit the final values to the canvas transform and redraw once, crisply.
     */
    private fun animateTo(s: Float, tx: Float, ty: Float) {
        cancelAnim()
        val s0 = scale; val x0 = transX; val y0 = transY
        if (s0 == s && x0 == tx && y0 == ty) { commit(s, tx, ty); return }

        animEnd = Triple(s, tx, ty)
        // Pivot at the top-left so the view transform matches the canvas (translate, then scale).
        target.pivotX = 0f; target.pivotY = 0f
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                // View transform layered over the frozen canvas state (s0,x0,y0); see commit().
                val sv = (s0 + (s - s0) * f) / s0
                target.scaleX = sv
                target.scaleY = sv
                target.translationX = (x0 + (tx - x0) * f) - sv * x0
                target.translationY = (y0 + (ty - y0) * f) - sv * y0
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val end = animEnd ?: return
                    animEnd = null; animator = null
                    commit(end.first, end.second, end.third)
                }
            })
            start()
        }
    }

    /** Stops any running animation, jumping straight to its committed end state. */
    private fun cancelAnim() {
        animator?.cancel() // fires onAnimationEnd → commit(end)
    }

    /** Bakes ([s],[tx],[ty]) into the canvas transform and clears the view-layer transform. */
    private fun commit(s: Float, tx: Float, ty: Float) {
        scale = s; transX = tx; transY = ty
        target.scaleX = 1f; target.scaleY = 1f
        target.translationX = 0f; target.translationY = 0f
        target.setLayerType(View.LAYER_TYPE_NONE, null)
        target.invalidate()
    }

    private companion object {
        const val MAX_SCALE = 2.6f // double-tap zoom level
        const val ANIM_MS = 220L
        const val EPS = 0.01f
    }
}
