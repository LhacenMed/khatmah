package com.lhacenmed.khatmah.feature.quran.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.PathParser
import androidx.core.graphics.withTranslation

/**
 * Renders a [ParsedVector] directly on a Compose Canvas.
 * Scales the viewport to fill the available space uniformly.
 */
@Composable
fun VectorFileCanvas(vector: ParsedVector, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (vector.viewportWidth <= 0f || vector.viewportHeight <= 0f) return@Canvas

        val scaleX = size.width  / vector.viewportWidth
        val scaleY = size.height / vector.viewportHeight
        val scale  = minOf(scaleX, scaleY)          // uniform — no distortion
        val dx     = (size.width  - vector.viewportWidth  * scale) / 2f
        val dy     = (size.height - vector.viewportHeight * scale) / 2f

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            nativeCanvas.withTranslation(dx, dy) {
                scale(scale, scale)

                for (vp in vector.paths) {
                    val androidPath = PathParser.createPathFromPathData(vp.pathData)
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.FILL
                        color = vp.fillColor
                        alpha = (vp.fillAlpha * 255).toInt().coerceIn(0, 255)
                    }
                    drawPath(androidPath, paint)
                }

            }
        }
    }
}