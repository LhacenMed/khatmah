package com.lhacenmed.khatmah.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lhacenmed.khatmah.R
import androidx.compose.foundation.Canvas

/**
 * Full-featured HSV color picker dialog.
 *
 * Layout:
 *  • 2-D Saturation × Value field (largest area, draggable thumb)
 *  • Hue slider (rainbow strip)
 *  • Old / New color swatches + hex input
 *  • Cancel / OK actions
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    // Decompose initial color into HSV
    val initHsv = FloatArray(3).also {
        android.graphics.Color.colorToHSV(initialColor.toArgb(), it)
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var vValue by remember { mutableFloatStateOf(initHsv[2]) }

    val pickedColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, vValue)))

    // Hex field
    var hexText by remember { mutableStateOf(pickedColor.toHexString()) }
    var hexError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ── Saturation × Value field ──────────────────────────────────
                SvField(
                    hue = hue,
                    sat = sat,
                    vValue = vValue,
                    onUpdate = { s, v -> sat = s; vValue = v },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                // ── Hue slider ────────────────────────────────────────────────
                HueSlider(
                    hue = hue,
                    onHueChange = { h ->
                        hue = h
                        hexText = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, vValue))).toHexString()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )

                // ── Swatches + hex ────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Old color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(initialColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    // New (live) color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(pickedColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    // Hex input
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { raw ->
                            hexText = raw
                            val clean = raw.trimStart('#').take(6)
                            if (clean.length == 6) {
                                runCatching { android.graphics.Color.parseColor("#$clean") }.onSuccess { argb ->
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb, hsv)
                                    hue = hsv[0]; sat = hsv[1]; vValue = hsv[2]
                                    hexError = false
                                }.onFailure { hexError = true }
                            } else hexError = raw.isNotEmpty()
                        },
                        label = { Text("HEX") },
                        prefix = { Text("#") },
                        isError = hexError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(pickedColor) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

// ── Saturation × Value 2-D field ──────────────────────────────────────────────

@Composable
private fun SvField(
    hue: Float,
    sat: Float,
    vValue: Float,
    onUpdate: (sat: Float, value: Float) -> Unit,
    modifier: Modifier,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { fieldSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun handle(pos: Offset) {
                        if (fieldSize.width == 0 || fieldSize.height == 0) return
                        onUpdate(
                            (pos.x / fieldSize.width).coerceIn(0f, 1f),
                            1f - (pos.y / fieldSize.height).coerceIn(0f, 1f),
                        )
                    }
                    handle(down.position)
                    drag(down.id) { handle(it.position) }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            // Base hue color
            drawRect(hueColor)
            // White-to-transparent → saturation axis
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            // Transparent-to-black → value axis
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            // Thumb
            val thumbX = sat * size.width
            val thumbY = (1f - vValue) * size.height
            val r = 10.dp.toPx()
            drawCircle(Color.Black, r + 2.dp.toPx(), Offset(thumbX, thumbY), style = Stroke(2.dp.toPx()))
            drawCircle(Color.White, r, Offset(thumbX, thumbY), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ── Hue slider ────────────────────────────────────────────────────────────────

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier,
) {
    val hueGradient = remember {
        listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
        )
    }
    var sliderWidth by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .onSizeChanged { sliderWidth = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun handle(pos: Offset) {
                        if (sliderWidth == 0) return
                        onHueChange((pos.x / sliderWidth).coerceIn(0f, 1f) * 360f)
                    }
                    handle(down.position)
                    drag(down.id) { handle(it.position) }
                }
            }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.horizontalGradient(hueGradient))
            val thumbX = (hue / 360f) * size.width
            drawLine(Color.White, Offset(thumbX, 0f), Offset(thumbX, size.height), 3.dp.toPx())
            drawLine(Color.Black, Offset(thumbX - 1.5f.dp.toPx(), 0f), Offset(thumbX - 1.5f.dp.toPx(), size.height), 1.dp.toPx())
            drawLine(Color.Black, Offset(thumbX + 1.5f.dp.toPx(), 0f), Offset(thumbX + 1.5f.dp.toPx(), size.height), 1.dp.toPx())
        }
    }
}

// ── Extension ─────────────────────────────────────────────────────────────────

private fun Color.toHexString(): String =
    "%06X".format(toArgb() and 0xFFFFFF)