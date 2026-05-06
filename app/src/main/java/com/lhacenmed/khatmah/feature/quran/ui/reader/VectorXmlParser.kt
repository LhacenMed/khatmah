package com.lhacenmed.khatmah.feature.quran.ui.reader

import android.graphics.Color
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import androidx.core.graphics.toColorInt

data class ParsedVector(
    val viewportWidth:  Float,
    val viewportHeight: Float,
    val paths:          List<VectorPath>,
)

/**
 * One path element from an Android <vector> drawable.
 *
 * [isMarker] — true when [fillColor] equals [MARKER_SENTINEL], meaning this path
 * is an aya-number marker whose color should be overridden at render time.
 * The sentinel (#FF03A9F4) is assigned by the SVG pre-processing script and
 * survives vd-tool conversion unchanged.
 */
data class VectorPath(
    val pathData:  String,
    val fillColor: Int,
    val fillAlpha: Float,
    val isMarker:  Boolean = false,
)

object VectorXmlParser {

    /**
     * Sentinel ARGB assigned to aya-marker paths by the SVG conversion script.
     * Any path with this exact fill color is treated as a marker at render time
     * and painted with the app's dynamic marker color instead.
     */
    internal const val MARKER_SENTINEL = 0xFF03A9F4.toInt()

    fun parse(stream: InputStream): ParsedVector {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser  = factory.newPullParser()
        parser.setInput(stream, null)

        var vpWidth  = 0f
        var vpHeight = 0f
        val paths    = mutableListOf<VectorPath>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "vector" -> {
                        vpWidth  = parser.attrFloat("android:viewportWidth")  ?: 0f
                        vpHeight = parser.attrFloat("android:viewportHeight") ?: 0f
                    }
                    "path" -> {
                        val data  = parser.attrString("android:pathData") ?: continue
                        val fill  = parser.attrString("android:fillColor")
                            ?.let { parseColor(it) } ?: Color.BLACK
                        val alpha = parser.attrFloat("android:fillAlpha") ?: 1f
                        paths += VectorPath(
                            pathData  = data,
                            fillColor = fill,
                            fillAlpha = alpha,
                            isMarker  = fill == MARKER_SENTINEL,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return ParsedVector(vpWidth, vpHeight, paths)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun XmlPullParser.attrString(name: String): String? {
        val localName = name.substringAfter(':')
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == localName) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlPullParser.attrFloat(name: String): Float? =
        attrString(name)?.toFloatOrNull()

    private fun parseColor(raw: String): Int = runCatching {
        raw.toColorInt()
    }.getOrDefault(Color.BLACK)
}