package com.lhacenmed.khatmah.feature.quran.ui.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.core.graphics.withTranslation
import com.lhacenmed.khatmah.core.nav.LocalNavigator
import com.lhacenmed.khatmah.feature.quran.ui.reader.ParsedVector
import com.lhacenmed.khatmah.feature.quran.ui.reader.VectorXmlParser
import java.io.File

/**
 * Debug page — renders a Warsh XML vector from internal storage via Canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugWarshScreen() {
    val context = LocalContext.current
    val nav     = LocalNavigator.current

    // Parse once; null = file missing or parse error
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val vector = remember {
        val file = File(context.filesDir, "warsh/xml/001.xml")
        if (!file.exists()) {
            errorMsg = "File not found: ${file.absolutePath}"
            return@remember null
        }
        runCatching {
            file.inputStream().use { VectorXmlParser.parse(it) }
        }.onFailure {
            errorMsg = "Parse error: ${it.localizedMessage}"
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Warsh 001") },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (vector != null) {
                VectorFileCanvas(
                    vector   = vector,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text     = errorMsg ?: "Unknown error",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

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
