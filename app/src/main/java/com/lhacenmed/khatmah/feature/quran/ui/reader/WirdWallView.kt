package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.feature.quran.ui.reader.book.BookPageView
import com.lhacenmed.khatmah.shared.util.HapticFeedback.slightHapticFeedback

/**
 * What lies behind the last page of a wird: a ring that fills as the page is pulled aside, a
 * forward arrow, and a label. It sits *under* the pager, so it is uncovered by the drag rather
 * than swiped onto — there is no page here to land on, only a wall to lean against.
 *
 * The ring rides just inside the page's edge, so it is the first thing the pull uncovers, and it
 * completes exactly where the wall's resistance begins ([WIRD_ARM]): a full ring means "let go and
 * the wird is done". Reaching it ticks the haptics and fires one halo pulse.
 *
 * Transparent — the reader's parchment/night background shows through from the root, so the wall
 * reads as more mushaf rather than a screen behind it.
 */
class WirdWallView @JvmOverloads constructor(
    context: Context,
    attrs:   AttributeSet? = null,
) : View(context, attrs) {

    /** How far the page has been pulled aside, in px. The strip it uncovers is what we draw in. */
    var pull: Float = 0f
        set(value) {
            val next = value.coerceAtLeast(0f)
            if (next == field) return
            field = next
            armed = next >= armPx
            invalidate()
        }

    /**
     * True on the khatmah's final wird, where releasing ends the khatmah rather than opening the
     * next one. Only the label differs — the gesture is the same either way.
     */
    var endsKhatmah: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    /** Ring full — releasing now completes the wird. Drives the haptic tick and the halo pulse. */
    private var armed = false
        set(value) {
            if (value == field) return
            field = value
            if (value) { slightHapticFeedback(); pulse.start() }
        }

    /** The commit distance, in px — the same fraction of the page the wall itself works in. */
    private val armPx get() = width * WIRD_ARM

    private val density = resources.displayMetrics.density
    private val radius  = RING_RADIUS_DP * density
    private val ring    = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
        strokeCap   = Paint.Cap.ROUND
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = RING_WIDTH_DP * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize  = LABEL_SIZE_DP * density
        textAlign = Paint.Align.CENTER
    }

    // Mirroring is off on purpose: the arrow points the way the finger keeps travelling (right),
    // not the way the pages turn.
    private val arrow = AppCompatResources.getDrawable(context, R.drawable.ic_chevron_forward)!!
        .mutate().apply { isAutoMirrored = false }

    /** 0..1 halo phase — one expansion on arming, the way a confirmation reads. */
    private val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration     = PULSE_MS
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    private var ink    = BookPageView.DAY_TEXT
    private var accent = BookPageView.ACCENT_FALLBACK

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Resolved here rather than per frame: the reader recreates its pages on a theme change.
        ink = if (ReaderTheme.effectiveNight(context)) BookPageView.NIGHT_TEXT else BookPageView.DAY_TEXT
        accent = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, accent)
        arrow.setTint(ink)
        labelPaint.color = ink
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (pull <= 0f || width == 0) return

        val sweep = (pull / armPx).coerceAtMost(1f)
        // Just inside the page's edge, so the ring is the first thing the pull uncovers and it
        // travels out with the page rather than waiting in the middle of the screen.
        val cx = pull - (radius + EDGE_GAP_DP * density)
        val cy = height / 2f

        // Halo — one expansion out of the ring the moment it fills.
        if (armed && pulse.isRunning) {
            val phase = pulse.animatedValue as Float
            haloPaint.color = ColorUtils.setAlphaComponent(accent, (255 * (1f - phase) * HALO_ALPHA).toInt())
            canvas.drawCircle(cx, cy, radius + phase * HALO_GROWTH_DP * density, haloPaint)
        }

        fillPaint.color = ColorUtils.setAlphaComponent(
            accent, (255 * (if (armed) FILL_ALPHA_ARMED else FILL_ALPHA)).toInt(),
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)

        trackPaint.color = ColorUtils.setAlphaComponent(ink, (255 * TRACK_ALPHA).toInt())
        canvas.drawCircle(cx, cy, radius, trackPaint)

        ring.set(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPaint.color = accent
        canvas.drawArc(ring, ARC_START_DEG, 360f * sweep, false, arcPaint)

        val half = (ARROW_SIZE_DP * density / 2f).toInt()
        arrow.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        arrow.draw(canvas)

        canvas.drawText(
            context.getString(if (endsKhatmah) R.string.wird_finish_khatmah else R.string.wird_next),
            cx,
            cy + radius + LABEL_GAP_DP * density,
            labelPaint,
        )
    }

    private companion object {
        const val RING_RADIUS_DP   = 22f
        const val RING_WIDTH_DP    = 2.5f
        const val EDGE_GAP_DP      = 12f    // ring centre inset from the pulled page's edge
        const val ARROW_SIZE_DP    = 20f
        const val LABEL_SIZE_DP    = 13f
        const val LABEL_GAP_DP     = 22f
        const val ARC_START_DEG    = -90f   // fills clockwise from 12 o'clock
        const val TRACK_ALPHA      = 0.18f
        const val FILL_ALPHA       = 0.10f
        const val FILL_ALPHA_ARMED = 0.22f
        const val HALO_ALPHA       = 0.45f
        const val HALO_GROWTH_DP   = 14f
        const val PULSE_MS         = 900L
    }
}
