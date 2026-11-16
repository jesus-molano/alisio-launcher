package dev.jesusmolano.alisio.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

/**
 * Cómo va un icono vivo con el estilo de iconos elegido: claro, como un icono a color, o sobre la
 * placa del Inicio con el trazo claro, como un icono monocromo.
 */
enum class LiveLook { LIGHT, PLATE }

fun liveLookFor(style: IconStyle): LiveLook = if (style is IconStyle.Monochrome) LiveLook.PLATE else LiveLook.LIGHT

// Lo que ocupa cada pieza dentro del icono, como fracción de su lado.
private const val WEEKDAY_RATIO = 0.19f
private const val DAY_RATIO = 0.44f
private const val CORNER_RATIO = 17f / 52f
private const val HOUR_HAND = 0.22f
private const val MINUTE_HAND = 0.32f
private const val TIMER_RADIUS = 0.39f
private val HandWidth = 2.5.dp
private val MinuteHandWidth = 1.75.dp
private val RingWidth = 2.5.dp

// Las agujas y la letra sobre la cara clara del icono: el fondo de la app, un poco aclarado el día.
private val LightInk = AlisioTokens.Background
private val LightSoftInk = AlisioTokens.Background.copy(alpha = 0.6f)

// El recorrido que queda por sonar, bajo el progreso: se ve sin competir con él.
private val TrackAlpha = 0.28f

private fun faceColor(look: LiveLook): Color = if (look == LiveLook.LIGHT) AlisioTokens.Text else AlisioTokens.Plate

private fun inkColor(look: LiveLook): Color = if (look == LiveLook.LIGHT) LightInk else AlisioTokens.Text

private fun softInkColor(look: LiveLook): Color = if (look ==
    LiveLook.LIGHT
) {
    LightSoftInk
} else {
    AlisioTokens.TextSecondary
}

/** El calendario con el día de hoy: el día de la semana corto arriba y el número grande. */
@Composable
fun CalendarLiveIcon(day: Int, weekday: String, size: Dp, look: LiveLook, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val dayStyle = AlisioTokens.Numeral
    val labelStyle = AlisioTokens.DockLabel
    Box(
        modifier.size(size).background(faceColor(look), AlisioTokens.DockIconShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val weekdaySize = with(density) { (size * WEEKDAY_RATIO).toSp() }
            val daySize = with(density) { (size * DAY_RATIO).toSp() }
            Text(
                text = weekday,
                style = labelStyle.copy(fontSize = weekdaySize, lineHeight = weekdaySize),
                color = softInkColor(look),
                maxLines = 1
            )
            Text(
                text = day.toString(),
                style = dayStyle.copy(fontSize = daySize, lineHeight = daySize, fontFeatureSettings = "tnum"),
                color = inkColor(look),
                maxLines = 1
            )
        }
    }
}

/**
 * El reloj con la hora de ahora. Con un temporizador en marcha, un arco de acento dice lo que le
 * queda de su hora en curso ([timer], de 0 a 1): es una señal viva, no adorno.
 */
@Composable
fun ClockLiveIcon(
    hour: Int,
    minute: Int,
    size: Dp,
    look: LiveLook,
    modifier: Modifier = Modifier,
    timer: Float? = null
) {
    val face = faceColor(look)
    val ink = inkColor(look)
    val accent = AlisioTokens.Accent
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val side = this.size.minDimension
        drawRoundRect(face, cornerRadius = CornerRadius(side * CORNER_RATIO))
        val center = Offset(this.size.width / 2, this.size.height / 2)
        if (timer != null) {
            val radius = side * TIMER_RADIUS
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * timer,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(RingWidth.toPx(), cap = StrokeCap.Round)
            )
        }
        fun hand(turns: Float, length: Float, width: Float) {
            val angle = 2 * PI * turns - PI / 2
            drawLine(
                color = ink,
                start = center,
                end = Offset(
                    center.x + (cos(angle) * side * length).toFloat(),
                    center.y + (sin(angle) * side * length).toFloat()
                ),
                strokeWidth = width,
                cap = StrokeCap.Round
            )
        }
        hand(((hour % 12) + minute / 60f) / 12f, HOUR_HAND, HandWidth.toPx())
        hand(minute / 60f, MINUTE_HAND, MinuteHandWidth.toPx())
        drawCircle(ink, radius = HandWidth.toPx(), center = center)
    }
}

/**
 * Por dónde va lo que suena, dibujado sobre el contorno del icono: empieza arriba en el centro y
 * avanza en el sentido de las agujas. Va encima del icono de la app, sin taparlo.
 */
@Composable
fun ProgressOutline(fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val accent = AlisioTokens.Accent
    val track = AlisioTokens.Text.copy(alpha = TrackAlpha)
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val stroke = RingWidth.toPx()
        val inset = stroke / 2
        val outline = outlineFromTop(this.size.width, this.size.height, inset, this.size.minDimension * CORNER_RATIO)
        drawPath(outline, track, style = Stroke(stroke))
        val measure = PathMeasure().apply { setPath(outline, false) }
        val done = Path()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), done, true)
        drawPath(done, accent, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** El contorno redondeado del icono empezando arriba en el centro, para que el progreso salga de ahí. */
private fun outlineFromTop(width: Float, height: Float, inset: Float, corner: Float): Path {
    val radius = (corner - inset).coerceAtLeast(0f)
    val left = inset
    val top = inset
    val right = width - inset
    val bottom = height - inset
    return Path().apply {
        moveTo(width / 2, top)
        lineTo(right - radius, top)
        arcTo(Rect(right - 2 * radius, top, right, top + 2 * radius), -90f, 90f, false)
        lineTo(right, bottom - radius)
        arcTo(Rect(right - 2 * radius, bottom - 2 * radius, right, bottom), 0f, 90f, false)
        lineTo(left + radius, bottom)
        arcTo(Rect(left, bottom - 2 * radius, left + 2 * radius, bottom), 90f, 90f, false)
        lineTo(left, top + radius)
        arcTo(Rect(left, top, left + 2 * radius, top + 2 * radius), 180f, 90f, false)
        close()
    }
}

/**
 * La hora del reloj desde el arranque, cada [periodMillis], solo mientras se ve: el anillo de lo
 * que suena y el del temporizador avanzan sin trabajo con el Inicio fuera de la vista. Sin [active]
 * no hay pulso.
 */
@Composable
fun rememberTicker(periodMillis: Long, active: Boolean): Long {
    val ticks =
        remember(periodMillis, active) {
            flow {
                while (active) {
                    emit(SystemClock.elapsedRealtime())
                    delay(periodMillis)
                }
            }
        }
    val now by ticks.collectAsStateWithLifecycle(SystemClock.elapsedRealtime())
    return now
}
