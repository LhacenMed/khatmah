package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.core.graphics.PathParser
import androidx.core.graphics.withSave
import com.lhacenmed.khatmah.feature.quran.data.WarshAyaRegion
import com.lhacenmed.khatmah.feature.quran.data.WarshPageData

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

// ── Zoom hit-test inverse transform ──────────────────────────────────────────

/**
 * Maps a screen-space tap point back to pre-zoom composable pixel space.
 *
 * Forward transform applied during draw:
 *   P' = scale * (P − center) + center + pan
 *      = scale * P + center * (1 − scale) + pan
 * Inverse:
 *   P  = (P' − center * (1 − scale) − pan) / scale
 */
private fun inverseZoom(point: Offset, scale: Float, pan: Offset, size: IntSize): Offset {
    val cx = size.width  / 2f
    val cy = size.height / 2f
    return Offset(
        (point.x - cx * (1f - scale) - pan.x) / scale,
        (point.y - cy * (1f - scale) - pan.y) / scale,
    )
}

// ── Per-path paint cache ──────────────────────────────────────────────────────

/**
 * Builds one [android.graphics.Paint] per path.
 * Marker paths (sentinel color) are painted with [markerColor] instead of their
 * stored fill, allowing dynamic theming from MaterialTheme or user preferences.
 * Content paths respect dark-mode inversion as before.
 */
private fun buildPaints(
    paths:       List<VectorPath>,
    isDark:      Boolean,
    markerColor: Int,
): List<android.graphics.Paint> = paths.map { vp ->
    android.graphics.Paint().apply {
        isAntiAlias = true
        style       = android.graphics.Paint.Style.FILL
        color = when {
            vp.isMarker -> markerColor
            isDark      -> (vp.fillColor xor 0x00FFFFFF) or (vp.fillColor and 0xFF000000.toInt())
            else        -> vp.fillColor
        }
        alpha = (vp.fillAlpha * 255).toInt().coerceIn(0, 255)
    }
}

// ── Composable ────────────────────────────────────────────────────────────────

/**
 * Renders one Warsh mushaf page natively in Compose — no WebView, no Bitmap,
 * no VectorDrawable API (avoids the XmlBlock$Parser ClassCastException).
 *
 * Zoom: pinch-to-zoom (1×–4×) with constrained panning. Pager scroll is
 * suppressed while zoomed via [onZoomChanged]; restores automatically on reset.
 * Hit regions are inverse-transformed so aya tap accuracy is maintained at
 * any zoom level.
 *
 * Gesture contract:
 *   Long-press inside a polygon → [onAyaPress] (triggers audio).
 *   Tap anywhere (inside or outside polygons) → [onBaresTap] (toggles bars).
 *   Long-press outside polygons → no-op.
 *
 * [onAyaPress]     — long-press inside a polygon (surahNum, ayahNum).
 * [onBaresTap]     — tap anywhere (toggles top/bottom bars).
 * [onZoomChanged]  — emitted when zoom crosses the 1× boundary.
 * [highlightColor] — semi-transparent fill for the selected aya region.
 */
@Composable
internal fun QuranXmlPage(
    modifier:       Modifier = Modifier,
    pageData:       WarshPageData,
    selectedAya:    Pair<Int, Int>?,
    highlightColor: Color,
    markerColor:    Color,
    onAyaPress:     (surahNum: Int, ayahNum: Int) -> Unit,
    onBaresTap:     () -> Unit,
    onZoomChanged:  (Boolean) -> Unit = {},
) {
    var composableSize by remember { mutableStateOf(IntSize.Zero) }
    val isDark = isSystemInDarkTheme()

    // ── Zoom state ────────────────────────────────────────────────────────────
    var scale     by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        val maxPanX  = composableSize.width  * (newScale - 1f) / 2f
        val maxPanY  = composableSize.height * (newScale - 1f) / 2f
        val newPan   = if (newScale > 1f) Offset(
            (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
            (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY),
        ) else Offset.Zero
        if ((newScale > 1f) != (scale > 1f)) onZoomChanged(newScale > 1f)
        scale     = newScale
        panOffset = newPan
    }

    // ── Pre-built android Paths ───────────────────────────────────────────────
    val androidPaths: List<Path> = remember(pageData.pageNum) {
        pageData.vector.paths.map { vp ->
            PathParser.createPathFromPathData(vp.pathData)
        }
    }

    // Per-path paints — rebuilt when page, dark mode, or marker color changes.
    val markerArgb = markerColor.toArgb()
    val paints: List<android.graphics.Paint> = remember(pageData.pageNum, isDark, markerArgb) {
        buildPaints(pageData.vector.paths, isDark, markerArgb)
    }

    // Scale matrix: maps 0–235 viewport units → composable pixels.
    val scaleMatrix: Matrix = remember(pageData.pageNum, composableSize) {
        if (composableSize == IntSize.Zero) Matrix() else Matrix().also {
            it.setScale(
                composableSize.width  / pageData.viewportW,
                composableSize.height / pageData.viewportH,
            )
        }
    }

    // Scaled paths for rendering — rebuilt when size or page changes.
    val scaledPaths: List<Path> = remember(pageData.pageNum, composableSize) {
        if (composableSize == IntSize.Zero) emptyList()
        else androidPaths.map { src ->
            Path().also { dst -> src.transform(scaleMatrix, dst) }
        }
    }

    // Hit regions scaled to screen pixels — rebuilt only when size or page changes.
    val hitRegions: List<HitRegion> = remember(pageData.pageNum, composableSize) {
        if (composableSize == IntSize.Zero) emptyList()
        else {
            val viewBox = RectF(0f, 0f, pageData.viewportW, pageData.viewportH)
            buildHitRegions(pageData.regions, viewBox, composableSize)
        }
    }

    // Highlight path for the currently selected aya.
    val highlightPath: Path? = remember(selectedAya, hitRegions) {
        selectedAya?.let { (sura, aya) ->
            hitRegions.firstOrNull { it.surahNum == sura && it.ayahNum == aya }?.path
        }
    }

    val highlightArgb  = highlightColor.copy(alpha = 0.18f).toArgb()
    val highlightPaint = remember(highlightArgb) {
        android.graphics.Paint().apply {
            color       = highlightArgb
            style       = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }

    // Always-current state snapshots for use inside the stable pointerInput block.
    // Using rememberUpdatedState avoids restarting pointerInput on every recomposition
    // while ensuring the lambda always reads the latest values.
    val currentHitRegions by rememberUpdatedState(hitRegions)
    val currentScale      by rememberUpdatedState(scale)
    val currentPan        by rememberUpdatedState(panOffset)
    val currentSize       by rememberUpdatedState(composableSize)
    val currentOnAyaPress by rememberUpdatedState(onAyaPress)
    val currentOnBaresTap by rememberUpdatedState(onBaresTap)

    Box(
        modifier         = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .aspectRatio(pageData.viewportW / pageData.viewportH)
                .onSizeChanged { composableSize = it }
                // Pinch-to-zoom: only intercepts pan gestures when zoomed in,
                // so the pager can still handle horizontal swipes at 1×.
                .transformable(state = transformState, canPan = { scale > 1f })
                // Stable Unit key — never restarts. rememberUpdatedState above
                // ensures the block always reads the latest hitRegions and zoom.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Tap always toggles bars — never triggers audio.
                            currentOnBaresTap()
                        },
                        onLongPress = { raw ->
                            // Long-press inside a polygon → play audio for that aya.
                            val p   = inverseZoom(raw, currentScale, currentPan, currentSize)
                            val hit = hitAt(currentHitRegions, p)
                            if (hit != null) currentOnAyaPress(hit.surahNum, hit.ayahNum)
                        },
                    )
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.withSave {

                    // Apply zoom centered on composable center with pan offset.
                    val cx = composableSize.width / 2f
                    val cy = composableSize.height / 2f
                    translate(panOffset.x, panOffset.y)
                    scale(scale, scale, cx, cy)

                    // Layer 1: all vector paths, already scaled to composable pixels.
                    scaledPaths.forEachIndexed { i, path ->
                        drawPath(path, paints[i])
                    }

                    // Layer 2: aya highlight on top.
                    highlightPath?.let { drawPath(it, highlightPaint) }

                }
            }
        }
    }
}

// ── Hit-test helper ───────────────────────────────────────────────────────────

/** Returns the first [HitRegion] that contains [offset], or null if none. */
private fun hitAt(regions: List<HitRegion>, offset: Offset): HitRegion? =
    regions.firstOrNull { pathContains(it.path, offset.x, offset.y, it.bounds) }