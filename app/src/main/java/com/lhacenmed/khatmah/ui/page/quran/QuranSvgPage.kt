package com.lhacenmed.khatmah.ui.page.quran

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.lhacenmed.khatmah.data.quran.WarshAyaRegion
import com.lhacenmed.khatmah.data.quran.WarshPageData

// ── Polygon hit-testing helpers ───────────────────────────────────────────────

/**
 * Parses a polygon string like "213,38 66,38 66,70 213,70" into a list of [PointF].
 * Returns empty list on any parse error.
 */
private fun parsePoints(polygon: String): List<PointF> =
    polygon.trim().split(" ").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size == 2) {
            val x = parts[0].trim().toFloatOrNull()
            val y = parts[1].trim().toFloatOrNull()
            if (x != null && y != null) PointF(x, y) else null
        } else null
    }

/**
 * Builds an [android.graphics.Path] from polygon points scaled from the 0–235
 * viewport coordinate space to screen pixel space.
 *
 * [scaleX] = composable width  / viewportW (235)
 * [scaleY] = composable height / viewportH (235)
 */
private fun buildScaledPath(points: List<PointF>, scaleX: Float, scaleY: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x * scaleX, points[0].y * scaleY)
    for (i in 1 until points.size) path.lineTo(points[i].x * scaleX, points[i].y * scaleY)
    path.close()
    return path
}

/**
 * Returns true if [touchX]/[touchY] (in pixels) lies inside [scaledPath].
 * Uses [android.graphics.Region] which is the standard Android polygon hit-test.
 */
private fun pathContains(scaledPath: Path, touchX: Float, touchY: Float, bounds: RectF): Boolean {
    if (bounds.isEmpty) return false
    val region = Region()
    region.setPath(scaledPath, Region(bounds.left.toInt(), bounds.top.toInt(),
        bounds.right.toInt(), bounds.bottom.toInt()))
    return region.contains(touchX.toInt(), touchY.toInt())
}

// ── Prepared hit region ───────────────────────────────────────────────────────

/**
 * Pre-computed hit region for one aya polygon, already scaled to screen pixels.
 * Built once per page when the composable size is first known.
 */
private data class HitRegion(
    val surahNum: Int,
    val ayahNum:  Int,
    val path:     Path,
    val bounds:   RectF,
)

private fun buildHitRegions(
    regions: List<WarshAyaRegion>,
    viewBox: RectF,
    size:    IntSize,
): List<HitRegion> {
    val scaleX = size.width  / viewBox.width()
    val scaleY = size.height / viewBox.height()
    return regions.mapNotNull { region ->
        val points = parsePoints(region.polygon)
        if (points.isEmpty()) return@mapNotNull null
        val path = buildScaledPath(points, scaleX, scaleY)
        val rf   = RectF()
        path.computeBounds(rf, true)
        HitRegion(surahNum = region.surahNum, ayahNum = region.ayahNum, path = path, bounds = rf)
    }
}

// ── Composable ────────────────────────────────────────────────────────────────

/**
 * Renders one Warsh mushaf page natively in Compose — no WebView, no Bitmap.
 *
 * Drawing:
 *   The drawable is set to bounds (0, 0, 235, 235) — matching the viewport and
 *   polygon coordinate space — then drawn onto a native Canvas that is pre-scaled
 *   by a [Matrix] mapping those 235×235 units to the composable's pixel dimensions.
 *   This gives true vector sharpness at any screen density.
 *
 * Alignment guarantee:
 *   [WarshPageData.viewportW/H] are always 235f (set by the repository, not from
 *   drawable.intrinsicWidth which is density-dependent). setBounds uses the same
 *   235 value. JSON polygon coordinates are also in 0–235 space. Result: the drawn
 *   content and the hit regions are always perfectly aligned on all devices.
 *
 * Layers (bottom → top, all on one Canvas):
 *  1. Vector drawable scaled to fill the composable.
 *  2. Aya highlight fill for [selectedAya] (drawn only when non-null).
 *
 * [onAyaPress]     — tap or long-press inside a polygon (surahNum, ayahNum).
 * [onBaresTap]     — tap outside all polygons (toggles top/bottom bars).
 * [highlightColor] — semi-transparent fill for the selected aya region.
 */
@Composable
internal fun QuranSvgPage(
    pageData:       WarshPageData,
    selectedAya:    Pair<Int, Int>?,
    highlightColor: Color,
    onAyaPress:     (surahNum: Int, ayahNum: Int) -> Unit,
    onBaresTap:     () -> Unit,
    modifier:       Modifier = Modifier,
) {
    var composableSize by remember { mutableStateOf(IntSize.Zero) }

    // Hit regions scaled to screen pixels — rebuilt only when size changes.
    val hitRegions by remember(pageData.pageNum, composableSize) {
        derivedStateOf {
            if (composableSize == IntSize.Zero) emptyList()
            else {
                val viewBox = RectF(0f, 0f, pageData.viewportW, pageData.viewportH)
                buildHitRegions(pageData.regions, viewBox, composableSize)
            }
        }
    }

    // Highlight path for the currently selected aya.
    val highlightPath by remember(selectedAya, hitRegions) {
        derivedStateOf {
            selectedAya?.let { (sura, aya) ->
                hitRegions.firstOrNull { it.surahNum == sura && it.ayahNum == aya }?.path
            }
        }
    }

    val isDark         = isSystemInDarkTheme()
    val highlightArgb  = highlightColor.copy(alpha = 0.18f).toArgb()
    val highlightPaint = remember(highlightArgb) {
        android.graphics.Paint().apply {
            color       = highlightArgb
            style       = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }

    // Invert paint for dark mode — applied as a ColorFilter layer over the drawable.
    val invertPaint = remember(isDark) {
        if (isDark) {
            android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix(floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                        0f, -1f,  0f, 0f, 255f,
                        0f,  0f, -1f, 0f, 255f,
                        0f,  0f,  0f, 1f,   0f,
                    ))
                )
            }
        } else null
    }

    // Scale matrix: maps 0–235 viewport units → composable pixels.
    val scaleMatrix = remember(pageData.pageNum, composableSize) {
        if (composableSize == IntSize.Zero) Matrix() else {
            Matrix().also {
                it.setScale(
                    composableSize.width  / pageData.viewportW,
                    composableSize.height / pageData.viewportH,
                )
            }
        }
    }

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .aspectRatio(pageData.viewportW / pageData.viewportH)
                .onSizeChanged { composableSize = it }
                .pointerInput(hitRegions) {
                    detectTapGestures(
                        onTap = { offset ->
                            val hit = hitAt(hitRegions, offset)
                            if (hit != null) onAyaPress(hit.surahNum, hit.ayahNum)
                            else             onBaresTap()
                        },
                        onLongPress = { offset ->
                            val hit = hitAt(hitRegions, offset)
                            if (hit != null) onAyaPress(hit.surahNum, hit.ayahNum)
                            // Long-press outside polygons: no action
                        },
                    )
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                // Layer 1: Vector drawable in viewport space (0–235), scaled to fill.
                native.save()
                native.concat(scaleMatrix)

                // setBounds must match the viewport dimensions so VectorDrawable scales
                // its path data (which is in 0–235 space) to fill exactly these bounds.
                pageData.drawable.setBounds(
                    0, 0,
                    pageData.viewportW.toInt(),
                    pageData.viewportH.toInt(),
                )

                if (invertPaint != null) {
                    // saveLayer applies the invert ColorFilter to the entire drawable.
                    native.saveLayer(0f, 0f, pageData.viewportW, pageData.viewportH, invertPaint)
                    pageData.drawable.draw(native)
                    native.restore()
                } else {
                    pageData.drawable.draw(native)
                }

                native.restore()

                // Layer 2: aya highlight in screen-pixel space (no matrix needed).
                highlightPath?.let { path ->
                    native.drawPath(path, highlightPaint)
                }
            }
        }
    }
}

// ── Hit-test helper ───────────────────────────────────────────────────────────

/** Returns the first [HitRegion] that contains [offset], or null if none. */
private fun hitAt(regions: List<HitRegion>, offset: Offset): HitRegion? =
    regions.firstOrNull { pathContains(it.path, offset.x, offset.y, it.bounds) }