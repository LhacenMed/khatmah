package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.EdgeEffect
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Where the wird's last page stops following the finger and the wall begins, and the ceiling the
 * page can never pass — both as a fraction of its width.
 *
 * [WIRD_ARM] is the whole gesture: the ring completes there and releasing there finishes the wird.
 * Past it the page keeps moving, but always less, so the wall can be leaned on without ever being
 * opened — the end of a wird is a glimpse, never a destination.
 */
internal const val WIRD_ARM = 0.3f
private const val WIRD_MAX = 0.42f

/**
 * The wall behind the last page of a Khatmah wird — the reader's own overscroll.
 *
 * Two framework signals drive it, each doing only what it is actually good for:
 *
 * - the pager's **edge effect** says *when* the pages are exhausted. RecyclerView routes a drag
 *   there only after its own scrolling has taken everything it can, so the wall never has to guess
 *   whether a swipe was meant to turn a page — and page turns, taps, zoom and long-presses are
 *   never in the conversation.
 * - an **item-touch listener** then carries the gesture. Once engaged it intercepts, which is
 *   RecyclerView's own supported hand-over: it cancels its scroll and stops competing, so exactly
 *   one thing moves the page. The pull is measured as absolute displacement from where the wall
 *   engaged, so finger wobble and reversals resolve smoothly instead of accumulating.
 *
 * Only a lifted finger commits. `EdgeEffect.onRelease` is deliberately not used for that: it also
 * fires when a scroll merely points away from the edge, so a pixel of backward wobble mid-pull
 * would have finished the wird on its own.
 *
 * The pull moves the *page views*, never the pager itself: transforming the scrolling view would
 * shift the coordinates its own touch handling reads back, so each frame of pull would distort the
 * next frame's delta — a feedback loop, and the reason an earlier version of this juddered.
 */
class WirdWall(
    private val pager: ViewPager2,
    private val view:  WirdWallView,
) : RecyclerView.OnItemTouchListener {

    /**
     * Called when the pull is released at or past [WIRD_ARM]. Return true if the caller has taken
     * charge of the page (the reader does, when it hands over to the next wird); false springs the
     * page home.
     */
    var onCommit: (() -> Boolean)? = null

    /** The wall stands only while an unread wird is open — never during a hand-off. */
    var enabled = false
        set(value) {
            if (value == field) return
            field = value
            if (!value) reset()
        }

    /** How far the page is currently held aside, in px. */
    var pullPx = 0f
        private set

    // The pull is measured from [anchorX] to the finger the gesture is following. Which finger that
    // is can change mid-gesture, so the anchor moves with it — see [rebase].
    private var anchorX = 0f
    private var lastX = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var pulling = false
    private var spring: ValueAnimator? = null

    /**
     * The pages have run out and the drag continues: from here the gesture is the wall's. Called by
     * the edge effect, so the decision is the pager's own, not a guess about the finger.
     */
    fun engage() {
        if (!enabled || pulling) return
        spring?.cancel()
        pulling = true
        anchorX = lastX   // the pull starts at zero, wherever the finger happens to be
    }

    // ── The gesture ─────────────────────────────────────────────────────────────

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        track(e)
        // Taking the gesture only once engaged; until then this is a passive observer and the
        // pager pages exactly as it always does.
        return pulling
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = track(e)

    override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit

    private fun track(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = e.getPointerId(0)
                lastX = e.x
            }

            // A second finger takes over, exactly as the pages themselves hand over to it.
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = e.actionIndex
                pointerId = e.getPointerId(index)
                rebase(e.getX(index))
            }

            // The followed finger left: carry on with one that remains, from where it is.
            MotionEvent.ACTION_POINTER_UP -> {
                val index = e.actionIndex
                if (e.getPointerId(index) == pointerId) {
                    val next = if (index == 0) 1 else 0
                    pointerId = e.getPointerId(next)
                    rebase(e.getX(next))
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val index = e.findPointerIndex(pointerId)
                if (index < 0) return
                lastX = e.getX(index)
                if (pulling) apply(damp((lastX - anchorX).coerceAtLeast(0f)))
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (pulling) {
                pulling = false
                val released = pullPx
                if (released >= pager.width * WIRD_ARM && onCommit?.invoke() == true) return
                springHome(released)
            }
        }
    }

    /**
     * Follows a different finger without moving the page: the anchor shifts by the same distance as
     * the reference did, so the pull either side of the change is identical. Landing or lifting a
     * second finger mid-pull is then invisible, which is how the pages behave.
     */
    private fun rebase(x: Float) {
        anchorX += x - lastX
        lastX = x
    }

    /** Drops the pull immediately — the wall is closing, or the page has changed hands. */
    fun reset() {
        spring?.cancel()
        pulling = false
        apply(0f)
    }

    // ── Rendering ───────────────────────────────────────────────────────────────

    /**
     * Slides the page views aside by [px]; the strip they leave behind is the wall. The pager keeps
     * its own transform untouched, so its touch handling reads the same coordinates throughout.
     */
    private fun apply(px: Float) {
        pullPx = px
        val pages = pager.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until pages.childCount) pages.getChildAt(i).translationX = px
        view.pull = px
    }

    private fun springHome(from: Float) {
        if (from <= 0f) return
        spring = ValueAnimator.ofFloat(from, 0f).apply {
            duration     = SPRING_MS
            interpolator = DecelerateInterpolator(SPRING_TENSION)
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * The page's travel for [raw] finger travel:
     *
     *     pull(raw) = arm + limit·over / (over + limit),   over = raw − arm,  limit = max − arm
     *
     * Up to `arm` this is the identity — the page sits exactly under the finger, which is the whole
     * range the wird is finished in. Past it the term is a hyperbola, and three properties are why
     * this curve rather than any other:
     *
     * - its slope at `over = 0` is 1, so it joins the free travel with no kink to feel: resistance
     *   arrives gradually instead of switching on;
     * - by `over = limit` it has given half of what remains, and half again at `3·limit` — the
     *   momentum bleeds off smoothly rather than hitting a stop;
     * - it approaches `max` asymptotically and never reaches it, so no throw, however hard, can
     *   open the wall.
     */
    private fun damp(raw: Float): Float {
        val arm = pager.width * WIRD_ARM
        if (raw <= arm) return raw
        val limit = pager.width * (WIRD_MAX - WIRD_ARM)
        val over = raw - arm
        return arm + limit * over / (over + limit)
    }

    private companion object {
        const val SPRING_MS      = 240L   // page falling back over an unfinished wall
        const val SPRING_TENSION = 1.6f
    }
}

/**
 * Wakes the wall when the pager's leading edge is reached. Reading runs backwards through the
 * positions, so the wird's last page is position 0 and its end is the pager's left edge; the
 * trailing edge keeps the platform's ordinary glow.
 */
class WirdWallEdgeEffectFactory(private val wall: WirdWall) : RecyclerView.EdgeEffectFactory() {

    override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect =
        if (direction == DIRECTION_LEFT) WallEdgeEffect(view, wall)
        else super.createEdgeEffect(view, direction)
}

/**
 * Reports "the pages are exhausted" to [wall] and draws nothing. It deliberately handles no other
 * callback: the glow's release and absorb are about drawing a glow, not about the finger.
 */
private class WallEdgeEffect(
    host: RecyclerView,
    private val wall: WirdWall,
) : EdgeEffect(host.context) {

    override fun onPull(deltaDistance: Float) = wall.engage()

    override fun onPull(deltaDistance: Float, displacement: Float) = wall.engage()

    /** Nothing to draw: the wall is a view of its own, behind the pager. */
    override fun draw(canvas: Canvas): Boolean = false

    /** Never animating, so the pager never invalidates or releases on the wall's behalf. */
    override fun isFinished(): Boolean = true
}
