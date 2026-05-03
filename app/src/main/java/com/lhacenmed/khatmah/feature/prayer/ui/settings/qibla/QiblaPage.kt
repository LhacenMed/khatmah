package com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
//import com.lhacenmed.khatmah.core.ui.components.AppTopBar
import kotlin.math.*

// Kaaba coordinates — Mecca, Saudi Arabia.
private const val MECCA_LAT = 21.4225
private const val MECCA_LNG = 39.8262

// Alignment threshold in degrees.
private const val ALIGN_THRESHOLD = 5f

// ─── Bearing math ─────────────────────────────────────────────────────────────

/** Great-circle bearing from [lat]/[lng] to Mecca (0–360°, clockwise from North). */
private fun calcQiblaBearing(lat: Double, lng: Double): Float {
    val φ1 = Math.toRadians(lat)
    val φ2 = Math.toRadians(MECCA_LAT)
    val Δλ = Math.toRadians(MECCA_LNG - lng)
    val y  = sin(Δλ) * cos(φ2)
    val x  = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

// ─── Entry composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaPage() {
    val context  = LocalContext.current
    val nav      = LocalNavController.current
    val location = remember { OnboardingPrefs.location(context) }

    val qiblaBearing = remember(location) {
        location?.let { calcQiblaBearing(it.lat, it.lng) } ?: 0f
    }

    // Cumulative azimuth avoids the 0°/360° wrap-around discontinuity in animation.
    var cumulativeAzimuth by remember { mutableFloatStateOf(0f) }
    var hasReading        by remember { mutableStateOf(false) }

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    DisposableEffect(Unit) {
        val rotMatrix   = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val deg        = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalized = ((deg % 360f) + 360f) % 360f
                // Shortest-path delta so the animated arc never wraps the long way round.
                val delta = ((normalized - (cumulativeAzimuth % 360f) + 540f) % 360f) - 180f
                cumulativeAzimuth += delta
                if (!hasReading) hasReading = true
            }
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.prayers_qibla))
                        val city = location?.cityName.orEmpty()
                        if (city.isNotBlank()) {
                            Text(city, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            location == null -> NoLocationScreen(padding)
            !hasReading      -> CalibrationScreen(padding)
            else             -> CompassScreen(cumulativeAzimuth, qiblaBearing, padding)
        }
    }
}

// ── No-location fallback ──────────────────────────────────────────────────────

@Composable
private fun NoLocationScreen(padding: PaddingValues) {
    Box(
        modifier         = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text      = stringResource(R.string.qibla_no_location),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(32.dp),
        )
    }
}

// ── Calibration (awaiting first sensor reading) ───────────────────────────────

@Composable
private fun CalibrationScreen(padding: PaddingValues) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_kaaba),
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text      = stringResource(R.string.qibla_calibrate_desc),
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

// ── Compass screen ────────────────────────────────────────────────────────────

@Composable
private fun CompassScreen(
    cumulativeAzimuth: Float,
    qiblaBearing:      Float,
    padding:           PaddingValues,
) {
    val animAzimuth by animateFloatAsState(
        targetValue   = cumulativeAzimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "azimuth",
    )

    // Normalised current heading (0–360).
    val heading     = ((animAzimuth % 360f) + 360f) % 360f
    // How much the needle must rotate from pointing straight up to point at Mecca.
    val needleAngle = qiblaBearing - heading
    // Shortest-path signed difference — used only to decide alignment colour.
    val diff        = ((needleAngle + 540f) % 360f) - 180f
    val isAligned   = abs(diff) < ALIGN_THRESHOLD

    val qiblaColor by animateColorAsState(
        targetValue = if (isAligned) Color(0xFF4CAF50) else Color(0xFFFFC107),
        label       = "qiblaColor",
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = stringResource(R.string.qibla_rotate_desc),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))

        QiblaCompass(
            dialRotation = -heading,
            needleAngle  = needleAngle,
            qiblaColor   = qiblaColor,
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text  = stringResource(R.string.qibla_from_north),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "${qiblaBearing.toInt()}°",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = qiblaColor,
        )
    }
}

// ── Compass widget ────────────────────────────────────────────────────────────

/**
 * Stateless compass widget.
 *
 * The dial rotates by [dialRotation] to keep N pointing to magnetic North.
 * The needle rotates by [needleAngle] so its tip always points toward Mecca.
 * When aligned, [qiblaColor] transitions to green.
 */
@Composable
private fun QiblaCompass(
    dialRotation: Float,
    needleAngle:  Float,
    qiblaColor:   Color,
) {
    val iconSize   = 28.dp
    val iconTopPad = 14.dp   // gap from the needle Box top to the Kaaba icon's top edge

    Box(
        modifier         = Modifier.size(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Rotating compass dial — N/S/E/W ticks turn with the device.
        CompassDial(
            rotation = dialRotation,
            modifier = Modifier.fillMaxSize(),
        )

        // Qibla needle — rotates independently to always point at Mecca.
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = needleAngle },
            contentAlignment = Alignment.TopCenter,
        ) {
            // Kaaba icon at the needle tip.
            Icon(
                painter            = painterResource(R.drawable.ic_kaaba),
                contentDescription = null,
                tint               = qiblaColor,
                modifier           = Modifier
                    .padding(top = iconTopPad)
                    .size(iconSize),
            )

            // Arrow shaft from icon bottom to pivot + short counterweight tail.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx         = size.width / 2
                val cy         = size.height / 2
                val tipY       = (iconTopPad + iconSize).toPx()
                val tailLength = (cy - tipY) * 0.3f

                drawLine(
                    color       = qiblaColor,
                    start       = Offset(cx, tipY),
                    end         = Offset(cx, cy),
                    strokeWidth = 4.dp.toPx(),
                )
                drawLine(
                    color       = Color(0xFFE53935),
                    start       = Offset(cx, cy),
                    end         = Offset(cx, cy + tailLength),
                    strokeWidth = 4.dp.toPx(),
                )
            }
        }

        // Center pivot dot.
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
        )
    }
}

// ── Compass dial ──────────────────────────────────────────────────────────────

/** Draws the rotating N/S/E/W compass ring via native Canvas for crisp, lightweight rendering. */
@Composable
private fun CompassDial(rotation: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation }) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r  = minOf(cx, cy)
        val nc = drawContext.canvas.nativeCanvas

        // White background.
        nc.drawCircle(cx, cy, r * 0.94f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
        )
        // Outer border.
        nc.drawCircle(cx, cy, r * 0.94f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color       = android.graphics.Color.LTGRAY
                style       = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.dp.toPx()
            }
        )

        // Tick marks: major every 90°, mid every 30°, minor every 5°.
        val tickPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
        for (i in 0 until 72) {
            val a       = Math.toRadians(i * 5.0 - 90.0)
            val isMajor = i % 18 == 0
            val isMid   = i % 6 == 0
            val oR = r * 0.93f
            val iR = when {
                isMajor -> r * 0.72f
                isMid   -> r * 0.82f
                else    -> r * 0.88f
            }
            tickPaint.strokeWidth = if (isMajor) 3.dp.toPx() else 1.dp.toPx()
            tickPaint.color = if (isMajor) android.graphics.Color.DKGRAY
            else android.graphics.Color.argb(100, 120, 120, 120)
            nc.drawLine(
                cx + cos(a).toFloat() * oR, cy + sin(a).toFloat() * oR,
                cx + cos(a).toFloat() * iR, cy + sin(a).toFloat() * iR,
                tickPaint,
            )
        }

        // Cardinal direction labels (N in red, rest in dark grey).
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize  = 18.sp.toPx()
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
        }
        val labelR = r * 0.60f
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (lbl, bearing) ->
            val a = Math.toRadians(bearing - 90.0)
            labelPaint.color = if (bearing == 0) android.graphics.Color.rgb(183, 28, 28)
            else android.graphics.Color.DKGRAY
            nc.drawText(
                lbl,
                cx + cos(a).toFloat() * labelR,
                cy + sin(a).toFloat() * labelR + labelPaint.textSize * 0.35f,
                labelPaint,
            )
        }

        // Degree labels at 90°, 180°, 270° (0° is covered by "N").
        val degPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize  = 10.sp.toPx()
            color     = android.graphics.Color.GRAY
        }
        val degR = r * 0.79f
        listOf(90 to "90°", 180 to "180°", 270 to "270°").forEach { (bearing, lbl) ->
            val a = Math.toRadians(bearing - 90.0)
            nc.drawText(
                lbl,
                cx + cos(a).toFloat() * degR,
                cy + sin(a).toFloat() * degR + degPaint.textSize * 0.35f,
                degPaint,
            )
        }
    }
}