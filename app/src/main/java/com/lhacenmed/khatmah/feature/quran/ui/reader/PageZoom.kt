package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Reusable pinch-zoom + drag-pan for a reader page, shared by the book and text readers so both
 * behave identically.
 *
 * The page draws itself in its own (content) coordinates through [draw]; [PageZoom] maps
 * screen↔content via [toContentX]/[toContentY] for hit-testing, and suspends pager paging while
 * zoomed. A two-finger pinch scales freely between [MIN_SCALE] and [MAX_SCALE] about the finger
 * focal point; a double-tap is a quick toggle between 1× and [DOUBLE_TAP_SCALE] centred on the
 * tapped spot. The single-tap [onTap] stays immediate — it never waits to disambiguate a
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
    /** True while the double-tap animation runs — draws the snapshot, like a pinch. */
    private var animating = false

    // ── Live pinch state ─────────────────────────────────────────────────────────
    //
    // A pinch drives the *canvas* transform (same path as [pan]), never the view's own scale, so
    // Android never warps the incoming touch coordinates and the focal point stays rock-steady.
    // To stay smooth at every zoom level — including full-screen, where re-rasterising the whole
    // page each frame stutters — the page is snapshotted once at 1× ([snapshot]) and that bitmap is
    // blitted (scaled) during the gesture instead of redrawing vector glyphs; on release the crisp
    // vector page is drawn again.
    private var pinching = false
    private var capturing = false
    private var prevFocusX = 0f; private var prevFocusY = 0f
    private var snapshot: Bitmap? = null
    private val snapMatrix = Matrix()
    private val snapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val detector = GestureDetector(target.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean { onTap(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!enabled() || pinching) return false
            toggle(e.x, e.y)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            if (pinching || scaleDetector.isInProgress) return false // a pinch owns the gesture
            if (!isZoomed) return false // not zoomed → let the host scroller / pager handle the drag
            pan(dX, dY)
            return true
        }
        override fun onLongPress(e: MotionEvent) = onLongPressAt(toContentX(e.x), toContentY(e.y))
    })

    private val scaleDetector = ScaleGestureDetector(target.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            if (!enabled()) return false
            cancelAnim() // fold any in-flight double-tap animation into the pinch's start state
            capture()    // freeze the page as a bitmap so the gesture blits instead of re-rasterising
            if (snapshot == null) return false // view not laid out yet → let the touch fall through
            pinching = true
            prevFocusX = d.focusX; prevFocusY = d.focusY
            return true
        }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            // Follow the focal point as the fingers drift (two-finger pan)…
            var tx = transX + (d.focusX - prevFocusX)
            var ty = transY + (d.focusY - prevFocusY)
            // …then scale about that focal point so the pinched spot stays under the fingers.
            val goal = (scale * d.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            val f = goal / scale
            tx = d.focusX - (d.focusX - tx) * f
            ty = d.focusY - (d.focusY - ty) * f
            clampTo(goal, tx, ty) { cx, cy -> scale = goal; transX = cx; transY = cy }
            prevFocusX = d.focusX; prevFocusY = d.focusY
            target.invalidate()
            return true
        }
        override fun onScaleEnd(d: ScaleGestureDetector) { pinching = false; target.invalidate() }
    }).apply { isQuickScaleEnabled = false } // double-tap is a toggle, not a drag-zoom

    /** Route the host view's touches here; holds the pager/scroller off while zoomed or pinching. */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            // A pinch can start from 1×, so grab the gesture as soon as a second finger lands.
            MotionEvent.ACTION_DOWN -> target.parent?.requestDisallowInterceptTouchEvent(isZoomed)
            MotionEvent.ACTION_POINTER_DOWN -> target.parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        detector.onTouchEvent(event)
        return true
    }

    fun toContentX(screenX: Float): Float = (screenX - transX) / scale
    fun toContentY(screenY: Float): Float = (screenY - transY) / scale

    /**
     * Draws the page through the current transform. While pinching, [content] is *not* re-run: the
     * one-shot [snapshot] of the page at 1× is blitted (scaled), so the gesture stays smooth at
     * every zoom level — even full-screen, where re-rasterising the whole page each frame is what
     * used to stutter. Off the gesture (and while capturing the snapshot) the vector [content] is
     * drawn crisply; at 1× the transform is the identity.
     */
    fun draw(canvas: Canvas, content: () -> Unit) {
        if (capturing) { content(); return } // render the raw 1× page into the snapshot bitmap
        val bmp = snapshot
        if ((pinching || animating) && bmp != null) {
            snapMatrix.setScale(scale, scale)
            snapMatrix.postTranslate(transX, transY)
            canvas.drawBitmap(bmp, snapMatrix, snapPaint)
        } else {
            val saved = canvas.save()
            canvas.translate(transX, transY)
            canvas.scale(scale, scale)
            content()
            canvas.restoreToCount(saved)
        }
    }

    /** Rasterises the page once at 1× into [snapshot], reusing the bitmap while the size holds. */
    private fun capture() {
        val w = target.width; val h = target.height
        if (w <= 0 || h <= 0) return
        var bmp = snapshot
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            snapshot = bmp
        } else {
            bmp.eraseColor(Color.TRANSPARENT)
        }
        capturing = true // makes [draw] render the vector page at 1× (identity) into the bitmap
        target.draw(Canvas(bmp))
        capturing = false
    }

    fun reset() {
        cancelAnim()
        pinching = false
        snapshot?.recycle(); snapshot = null
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
            val goal = DOUBLE_TAP_SCALE
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
     * Animates to ([s],[tx],[ty]) over the full-page [snapshot]. The page is heavy to rasterise (a
     * full mushaf of custom glyphs), so rather than redraw every glyph each frame we blit the
     * one-shot 1× snapshot through the canvas transform — exactly like the pinch path. Because the
     * snapshot holds the *whole* page (not just the currently-visible crop), zooming back out
     * reveals the off-screen content immediately instead of popping in only when the animation ends.
     * The final values are committed to the canvas on end and the crisp vector page is drawn once.
     */
    private fun animateTo(s: Float, tx: Float, ty: Float) {
        cancelAnim()
        val s0 = scale; val x0 = transX; val y0 = transY
        if (s0 == s && x0 == tx && y0 == ty) { commit(s, tx, ty); return }

        capture() // full-page 1× snapshot to animate over
        if (snapshot == null) { commit(s, tx, ty); return } // not laid out → jump to the end state

        animEnd = Triple(s, tx, ty)
        animating = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                // Drive the canvas transform directly; draw() blits the full snapshot through it.
                scale  = s0 + (s - s0) * f
                transX = x0 + (tx - x0) * f
                transY = y0 + (ty - y0) * f
                target.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val end = animEnd ?: return
                    animEnd = null; animator = null; animating = false
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
        const val MIN_SCALE = 1f          // pinch floor (page fills the view)
        const val MAX_SCALE = 4f          // pinch ceiling
        const val DOUBLE_TAP_SCALE = 2.6f // quick double-tap zoom level
        const val ANIM_MS = 220L
        const val EPS = 0.01f
    }
}
