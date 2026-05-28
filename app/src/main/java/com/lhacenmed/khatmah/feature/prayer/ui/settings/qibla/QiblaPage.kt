package com.lhacenmed.khatmah.feature.prayer.ui.settings.qibla

import android.content.Context
import android.graphics.Typeface
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhacenmed.khatmah.R
import androidx.navigation.NavBackStackEntry
import com.lhacenmed.khatmah.core.nav.AppPage
import com.lhacenmed.khatmah.core.nav.LocalNavController
import com.lhacenmed.khatmah.shared.util.OnboardingPrefs
import kotlin.math.*
import androidx.core.graphics.withRotation
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp

// Kaaba coordinates — Mecca, Saudi Arabia.
private const val MECCA_LAT = 21.4225
private const val MECCA_LNG = 39.8262

// Alignment threshold in degrees.
private const val ALIGN_THRESHOLD = 5f

private const val TICK_LEN_RATIO = 0.07f

// Semantic accent colors (independent of dynamic theme).
private val QiblaGreen = Color(0xFF4CAF50)
private val QiblaAmber = Color(0xFFFFB300)
private val NorthRed   = Color(0xFFE53935)

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

/** Maps a heading in degrees to its nearest cardinal / intercardinal abbreviation. */
private fun headingToCardinal(h: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((h % 360f) + 360f) % 360f / 45f + 0.5f).toInt() % 8]
}

/** Converts a decimal-degree value to a DMS string (sign ignored; label handled separately). */
private fun Double.toDms(): String {
    val abs = abs(this)
    val d   = abs.toInt()
    val m   = ((abs - d) * 60).toInt()
    val s   = (((abs - d) * 60 - m) * 60).toInt()
    return "$d°$m'$s\""
}

// ─── Entry composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen() {
    val context  = LocalContext.current
    val nav      = LocalNavController.current
    val location = remember { OnboardingPrefs.location(context) }

    val qiblaBearing = remember(location) {
        location?.let { calcQiblaBearing(it.lat, it.lng) } ?: 0f
    }

    // Cumulative azimuth avoids the 0°/360° wrap-around discontinuity in animation.
    var cumulativeAzimuth by remember { mutableFloatStateOf(0f) }
    var hasReading        by remember { mutableStateOf(false) }
    var isTilted by remember { mutableStateOf(false) }
    var devicePitch by remember { mutableFloatStateOf(0f) }
    var deviceRoll  by remember { mutableFloatStateOf(0f) }

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
    val gravitySensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    DisposableEffect(Unit) {
        val rotMatrix   = FloatArray(9)
        val orientation = FloatArray(3)
        val alpha       = 0.15f   // low-pass smoothing: lower = smoother, higher = faster

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val deg        = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalized = ((deg % 360f) + 360f) % 360f
                // Shortest-path delta so the animated arc never wraps the long way round.
                val raw   = ((normalized - (cumulativeAzimuth % 360f) + 540f) % 360f) - 180f
                val delta = raw * alpha   // low-pass: blend toward new reading gradually
                cumulativeAzimuth += delta
                if (!hasReading) hasReading = true
            }
        }

        val tiltListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                if (mag == 0f) return
                val tiltDeg = Math.toDegrees(acos((abs(z) / mag).toDouble())).toFloat()
                isTilted    = tiltDeg > 50f
                devicePitch = (Math.toDegrees(atan2(-y.toDouble(), z.toDouble())).toFloat()
                    .coerceIn(-80f, 80f)) * 0.4f
                deviceRoll  = -(Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
                    .coerceIn(-80f, 80f)) * 0.4f
            }
        }

        sensorManager.registerListener(tiltListener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager.unregisterListener(listener)
            sensorManager.unregisterListener(tiltListener)
        }
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
            else             -> CompassScreen(cumulativeAzimuth, qiblaBearing, location, isTilted, devicePitch, deviceRoll, padding)
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
    location:          OnboardingPrefs.LocationData,
    isTilted:          Boolean,
    devicePitch:       Float,
    deviceRoll:        Float,
    padding:           PaddingValues,
) {
    val animAzimuth by animateFloatAsState(
        targetValue   = cumulativeAzimuth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium,
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
        targetValue = if (isAligned) QiblaGreen else QiblaAmber,
        label       = "qiblaColor",
    )

    // ── Major tick alignment haptic (every 30°: cardinals + mid-ticks) ────────
    val view        = androidx.compose.ui.platform.LocalView.current
    var lastSnapped by remember { mutableIntStateOf(-1) }

    val snappedMajor = ((heading / 30f).roundToInt() * 30 % 360)
    val nearMajor    = abs(heading - snappedMajor) < 2f ||
            abs(heading - snappedMajor - 360f) < 2f

    LaunchedEffect(snappedMajor, nearMajor) {
        if (nearMajor && snappedMajor != lastSnapped) {
            lastSnapped = snappedMajor
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (!nearMajor) {
            lastSnapped = -1
        }
    }

    // ── Qibla alignment haptic ────────────────────────────────────────────────
    val nearQibla = abs(diff) < 2f
    var qiblaFired by remember { mutableStateOf(false) }

    LaunchedEffect(nearQibla) {
        if (nearQibla && !qiblaFired) {
            qiblaFired = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (!nearQibla) {
            qiblaFired = false
        }
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // ── Tilt warning ──────────────────────────────────────────────────────────
        if (isTilted) {
            Text(
                text     = stringResource(R.string.qibla_calibrate_desc),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        color  = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape  = MaterialTheme.shapes.medium,
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        } else {
            // ── Live heading label ────────────────────────────────────────────────
            Text(
                text  = "${headingToCardinal(heading)} ${heading.toInt()}°",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight    = FontWeight.Light,
                    letterSpacing = 1.sp,
                ),
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text  = stringResource(if (isAligned) R.string.qibla_aligned else R.string.qibla_rotate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (isAligned) QiblaGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 24.dp),
            )

            // ── Compass + measure widget ──────────────────────────────────────────
            BoxWithConstraints(
                modifier         = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val compassSize = minOf(maxWidth, maxHeight, 280.dp)
                QiblaCompass(
                    dialRotation  = -heading,
                    needleAngle   = needleAngle,
                    qiblaColor    = qiblaColor,
                    pitchDeg      = devicePitch,
                    rollDeg       = deviceRoll,
                    heading       = heading,
                    compassSize   = compassSize,
                )
            }

            // ── Qibla bearing + coordinates ───────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.padding(bottom = 24.dp),
            ) {
                Text(
                    text  = stringResource(R.string.qibla_from_north),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${qiblaBearing.toInt()}°",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = qiblaColor,
                )
                Spacer(Modifier.height(28.dp))
                CoordRow(location)
            }
        }
    }
}

// ── Coordinates row ───────────────────────────────────────────────────────────

@Composable
private fun CoordRow(location: OnboardingPrefs.LocationData) {
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CoordItem(
                label = if (location.lat >= 0) "NL" else "SL",
                dms   = location.lat.toDms(),
            )
            CoordItem(
                label = if (location.lng >= 0) "EL" else "WL",
                dms   = location.lng.toDms(),
            )
        }
    }
}

@Composable
private fun CoordItem(label: String, dms: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = dms,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
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
 * A fixed vertical line at 12 o'clock overlays the tick zone as a heading indicator.
 */
@Composable
private fun QiblaCompass(
    dialRotation: Float,
    needleAngle:  Float,
    qiblaColor:   Color,
    pitchDeg:     Float,
    rollDeg:      Float,
    heading:      Float,
    compassSize:  Dp,
) {
    val context    = LocalContext.current
    val onSurface  = MaterialTheme.colorScheme.onSurface

    // ── Gesture tilt state ────────────────────────────────────────────────────
    data class GestureTilt(val active: Boolean = false, val pitch: Float = 0f, val roll: Float = 0f)
    var gesture       by remember { mutableStateOf(GestureTilt()) }
    var lockedBearing by remember { mutableStateOf<Float?>(null) }

    val currentHeading by rememberUpdatedState(heading)

    val targetPitch = if (gesture.active) gesture.pitch else pitchDeg
    val targetRoll  = if (gesture.active) gesture.roll  else rollDeg

    val tiltSpec  = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val animPitch by animateFloatAsState(targetPitch, tiltSpec, label = "pitch")
    val animRoll  by animateFloatAsState(targetRoll,  tiltSpec, label = "roll")

    val maxTilt = 20f

    Box(
        modifier = Modifier
            .size(compassSize)
            .pointerInput(Unit) {
                val radius = size.width / 2f
                fun tiltFrom(x: Float, y: Float) = GestureTilt(
                    active = true,
                    roll   = ((x - radius) / radius * maxTilt).coerceIn(-maxTilt, maxTilt),
                    pitch  = -((y - radius) / radius * maxTilt).coerceIn(-maxTilt, maxTilt),
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    gesture  = tiltFrom(down.position.x, down.position.y)

                    var lastPos = down.position
                    while (true) {
                        val event  = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPos = change.position
                        if (!change.pressed) break
                        change.consume()
                        gesture = tiltFrom(change.position.x, change.position.y)
                    }

                    // Toggle only if released inside the compass circle.
                    val radius = size.width / 2f
                    val dx     = lastPos.x - radius
                    val dy     = lastPos.y - radius
                    if (dx * dx + dy * dy <= radius * radius) {
                        lockedBearing = if (lockedBearing == null) currentHeading else null
                    }

                    gesture = GestureTilt()
                }
            }
            .graphicsLayer {
                rotationX      = animPitch
                rotationY      = animRoll
                cameraDistance = 5f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        // Rotating compass dial — ticks, labels, starburst, locked indicator.
        CompassDial(
            rotation      = dialRotation,
            onSurface     = onSurface,
            context       = context,
            heading       = heading,
            lockedBearing = lockedBearing,
            modifier      = Modifier.fillMaxSize(),
        )

        // Qibla direction arrow + kaaba icon — outside tick circle, rotates to point at Mecca.
        // Kaaba counter-rotates to stay upright like cardinal letters.
        val qiblaArrow = painterResource(R.drawable.ic_triangle_arrow)
        val kaabaIcon  = painterResource(R.drawable.ic_kaaba)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = needleAngle; clip = false },
        ) {
            val r2      = minOf(size.width, size.height) / 2f
            val tickLen = r2 * TICK_LEN_RATIO
            val arrowSz = 20.dp.toPx()
            val kaabaSz = 20.dp.toPx()
            val gap     = 4.dp.toPx()

            // Arrow — points toward Mecca, rotates with needleAngle.
            translate(size.width / 2f - arrowSz / 2f, -(arrowSz + tickLen * 0.9f)) {
                with(qiblaArrow) {
                    draw(Size(arrowSz, arrowSz), colorFilter = ColorFilter.tint(qiblaColor))
                }
            }

            // Kaaba — sits above arrow, counter-rotates to remain vertically upright.
            val kaabaLeft = size.width / 2f - kaabaSz / 2f
            val kaabaTop  = -(arrowSz + tickLen * 0.4f + gap + kaabaSz)
            withTransform({
                translate(kaabaLeft, kaabaTop)
                rotate(-needleAngle, pivot = Offset(kaabaSz / 2f, kaabaSz / 2f))
            }) {
                with(kaabaIcon) {
                    draw(Size(kaabaSz, kaabaSz), colorFilter = ColorFilter.tint(qiblaColor))
                }
            }
        }

        // Fixed heading indicator — protrudes above the outer tick edge, bottom
        // pinned at inner tick edge; clip=false lets the top overflow the Box.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false },
        ) {
            val r2      = minOf(size.width, size.height) / 2f
            val tickLen = r2 * TICK_LEN_RATIO
            drawLine(
                color       = if (lockedBearing != null) NorthRed else onSurface,
                start       = Offset(size.width / 2f, -tickLen * 0.7f),
                end         = Offset(size.width / 2f, tickLen),
                strokeWidth = 3.dp.toPx(),
                cap         = StrokeCap.Round,
            )
        }
    }
}

// ── Compass dial ──────────────────────────────────────────────────────────────

/**
 * Rotating compass dial drawn entirely via native Canvas for performance.
 *
 * Design:
 *  • No border rings — transparent background, ticks float directly on the surface.
 *  • All ticks equal height; opacity: cardinal-major 85 % (heavy), non-cardinal-major 85 % (thin), minor 18 %.
 *  • Degree labels every 30°, rotated radially and positioned just inside the tick zone.
 *  • Cardinal letters (N/E/S/W) upright inside; N is [NorthRed].
 *  • Small red triangle at 0° with base outward and tip pointing inward toward center.
 *  • 4-pointed starburst crosshair via two overlapping diamond paths + RadialGradient.
 */
@Composable
private fun CompassDial(
    rotation:      Float,
    onSurface:     Color,
    context:       Context,
    heading:       Float,
    lockedBearing: Float?,
    modifier:      Modifier = Modifier,
) {
    val arrowPainter = painterResource(R.drawable.ic_triangle_arrow)
    val notoKufiRegular = remember { Typeface.createFromAsset(context.assets, "fonts/noto_kufi_regular.ttf") }

    /** True if [deg] falls within the shortest arc between [from] and [to]. */
    fun inArcShortest(deg: Float, from: Float, to: Float): Boolean {
        val span = ((to - from + 540f) % 360f) - 180f          // signed shortest, –180..+180
        val rel  = ((deg - from + 360f) % 360f)                // 0..360 relative to from
        return if (span >= 0f) rel <= span else rel >= (360f + span)
    }

    Canvas(modifier = modifier.graphicsLayer { rotationZ = rotation }) {
        val cx = size.width  / 2
        val cy = size.height / 2
        val r  = minOf(cx, cy)
        val nc = drawContext.canvas.nativeCanvas

        val tickLen = r * TICK_LEN_RATIO   // uniform height for all ticks

        // ── Tick marks — equal height, opacity carries the visual hierarchy ───
        val tickPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style     = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        // 12 major ticks; 14 minor ticks between each → 15 slots of 2° each.
        // Cardinal majors (0°/90°/180°/270°) are heavier; non-cardinal majors match minor width.
        // Ticks in the locked arc are drawn in red.
        val minorStep = 30.0 / 15.0
        for (seg in 0 until 12) {
            val majorDeg   = seg * 30.0
            val isCardinal = seg % 3 == 0
            val majInArc = lockedBearing != null && inArcShortest(majorDeg.toFloat(), heading, lockedBearing)
            // Major tick
            val aMaj = Math.toRadians(majorDeg - 90.0)
            tickPaint.strokeWidth = if (isCardinal) 2.dp.toPx() else 1.dp.toPx()
            tickPaint.color = if (majInArc) NorthRed.toArgb()
            else onSurface.copy(alpha = 0.85f).toArgb()
            nc.drawLine(
                cx + cos(aMaj).toFloat() * r,             cy + sin(aMaj).toFloat() * r,
                cx + cos(aMaj).toFloat() * (r - tickLen),  cy + sin(aMaj).toFloat() * (r - tickLen),
                tickPaint,
            )
            // 14 minor ticks
            tickPaint.strokeWidth = 1.dp.toPx()
            for (m in 1..14) {
                val minDeg   = majorDeg + m * minorStep
                val minInArc = lockedBearing != null && inArcShortest(minDeg.toFloat(), heading, lockedBearing)
                val aMin     = Math.toRadians(minDeg - 90.0)
                tickPaint.color = if (minInArc) NorthRed.copy(alpha = 0.75f).toArgb()
                else onSurface.copy(alpha = 0.18f).toArgb()
                nc.drawLine(
                    cx + cos(aMin).toFloat() * r,             cy + sin(aMin).toFloat() * r,
                    cx + cos(aMin).toFloat() * (r - tickLen),  cy + sin(aMin).toFloat() * (r - tickLen),
                    tickPaint,
                )
            }
        }

        // ── Locked bearing indicator — rotates with dial, drawn in red ────────
        if (lockedBearing != null) {
            val aLock     = Math.toRadians(lockedBearing - 90.0)
            val lockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style       = android.graphics.Paint.Style.STROKE
                strokeCap   = android.graphics.Paint.Cap.ROUND
                strokeWidth = 3.dp.toPx()
                color       = NorthRed.toArgb()
            }
            nc.drawLine(
                cx + cos(aLock).toFloat() * (r + tickLen * 0.7f),
                cy + sin(aLock).toFloat() * (r + tickLen * 0.7f),
                cx + cos(aLock).toFloat() * (r - tickLen),
                cy + sin(aLock).toFloat() * (r - tickLen),
                lockPaint,
            )
        }

        // ── Degree labels every 30° — rotated radially, just inside tick zone ─
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize  = 13.sp.toPx()
            typeface  = notoKufiRegular
        }
        // Position: extra gap below tick inner edge so labels breathe.
        val numR = r - tickLen - numPaint.textSize * 1.1f
        for ((i, bearing) in (0 until 360 step 30).withIndex()) {
            val isCardinal = bearing % 90 == 0
            numPaint.color = onSurface.copy(alpha = if (isCardinal) 1.0f else 0.60f).toArgb()
            nc.withRotation(bearing.toFloat(), cx, cy) {
                // Rotate canvas so this bearing is at the top, then draw text there upright.
                drawText(
                    "$bearing",
                    cx,
                    cy - numR + numPaint.textSize * 0.35f,
                    numPaint,
                )
            }
        }

        // ── Cardinal letters (N/E/S/W) — upright, inside the tick zone ────────
        val cardPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize  = 28.sp.toPx()
            typeface  = notoKufiRegular
        }
        val cardR = r * 0.55f
        listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (lbl, bearing) ->
            val a      = Math.toRadians(bearing - 90.0)
            val lx     = cx + cos(a).toFloat() * cardR
            val ly     = cy + sin(a).toFloat() * cardR
            val baseY  = ly + cardPaint.textSize * 0.35f
            cardPaint.color = onSurface.toArgb()
            nc.withRotation(-rotation, lx, ly) {
                drawText(lbl, lx, baseY, cardPaint)
            }
        }

        // ── North indicator — between N and 0° label, pointing North ─────────
        val arrowR    = (numR + cardR) / 2f
        val arrowSize = 12.dp.toPx()
        translate(cx - arrowSize / 2, cy - arrowR - arrowSize / 2) {
            with(arrowPainter) {
                draw(
                    size        = Size(arrowSize, arrowSize),
                    colorFilter = ColorFilter.tint(NorthRed),
                )
            }
        }

        // ── 4-pointed starburst crosshair ─────────────────────────────────────
        // 8-point star polygon: 4 axis tips + 4 diagonal waist points near center.
        // Small blobby quad-bezier center smooths the convergence point.
        val starLen   = r * 0.3f
        val waist     = starLen * 0.025f
        val blobR     = waist * 7f
        val starPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface.copy(alpha = 0.2f).toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        val starPath = android.graphics.Path().apply {
            moveTo(cx,           cy - starLen)
            lineTo(cx + waist,   cy - waist)
            lineTo(cx + starLen, cy)
            lineTo(cx + waist,   cy + waist)
            lineTo(cx,           cy + starLen)
            lineTo(cx - waist,   cy + waist)
            lineTo(cx - starLen, cy)
            lineTo(cx - waist,   cy - waist)
            close()
        }
        val blobPath = android.graphics.Path().apply {
            moveTo(cx,          cy - blobR)
            quadTo(cx, cy,      cx + blobR, cy)
            quadTo(cx, cy,      cx, cy + blobR)
            quadTo(cx, cy,      cx - blobR, cy)
            quadTo(cx, cy,      cx, cy - blobR)
            close()
        }
        val combinedPath = android.graphics.Path().apply {
            addPath(starPath)
            addPath(blobPath)
        }
        nc.drawPath(combinedPath, starPaint)
    }
}

object QiblaPage : AppPage() {
    override val route = "qibla"
    @Composable override fun Content(back: NavBackStackEntry) = QiblaScreen()
}