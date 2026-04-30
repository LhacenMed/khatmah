package com.lhacenmed.khatmah.ui.page.quran

import android.graphics.Color
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

data class ParsedVector(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<VectorPath>
)

data class VectorPath(
    val pathData: String,
    val fillColor: Int,   // ARGB int; defaults to black
    val fillAlpha: Float  // 0f–1f
)

object VectorXmlParser {

    fun parse(stream: InputStream): ParsedVector {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val parser = factory.newPullParser()
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
                        paths += VectorPath(data, fill, alpha)
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
        Color.parseColor(raw)
    }.getOrDefault(Color.BLACK)
}