package dev.jesusmolano.alisio.ui.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.format.DateFormat
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.jesusmolano.alisio.core.time.ClockFormat
import dev.jesusmolano.alisio.core.time.clockPattern
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Hora actual, que avanza cada minuto en punto. Solo corre con la pantalla a la vista: Alisio no
 * trabaja en segundo plano (docs/PLAN.md §5).
 */
@Composable
fun rememberNow(): ZonedDateTime {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now()
                delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L))
            }
        }
    }
    return now
}

// Hasta dónde se encoge la hora si no cabe, y en qué pasos, como fracción de su tamaño.
private const val CLOCK_MIN_SCALE = 0.5f
private const val CLOCK_STEP_SCALE = 0.05f

/**
 * Cómo se encoge la hora grande cuando no cabe: con formato de 12 h o letra grande del sistema se
 * reduce antes que cortarse. La usan el Inicio y la bienvenida.
 */
fun clockAutoSize(clock: TextStyle): TextAutoSize = TextAutoSize.StepBased(
    minFontSize = clock.fontSize * CLOCK_MIN_SCALE,
    maxFontSize = clock.fontSize,
    stepSize = clock.fontSize * CLOCK_STEP_SCALE
)

// Tamaño al que se mide la tinta: el aire de una cifra crece en proporción a la letra.
private const val INK_REFERENCE_PX = 1000f

/**
 * La hora grande con la tinta de su primera cifra en el filo, no su caja (docs/IDEAS.md §3.4): a
 * 112 sp la cifra lleva delante unos dp de aire que la apartan del filo que siguen la fecha y el dock.
 *
 * La corrección es fija por familia y tamaño y se mide con un «0» de referencia: medir la cifra de
 * cada minuto movería la hora a los lados al pasar de 9:59 a 10:00. Se aplica al colocar el texto,
 * con el tamaño que haya elegido [autoSize], sin recomponer.
 */
@Composable
fun ClockText(text: String, style: TextStyle, modifier: Modifier = Modifier, autoSize: TextAutoSize? = null) {
    val resolver = LocalFontFamilyResolver.current
    // Compose no da la tinta de un glifo (getBoundingBox es la caja de avance): se mide con Paint.
    val inkStart =
        remember(style.fontFamily, style.fontWeight, resolver) {
            val typeface = resolver.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Normal).value
            inkStartRatio(typeface as? Typeface)
        }
    val density = LocalDensity.current
    var fontSizePx by remember { mutableFloatStateOf(0f) }
    Text(
        text = text,
        style = style,
        maxLines = 1,
        autoSize = autoSize,
        onTextLayout = { layout -> fontSizePx = with(density) { layout.layoutInput.style.fontSize.toPx() } },
        modifier = modifier.offset { IntOffset(-(inkStart * fontSizePx).roundToInt(), 0) }
    )
}

/** El aire delante de la tinta del «0», como fracción del tamaño de la letra; negativo si sobresale. */
private fun inkStartRatio(typeface: Typeface?): Float {
    val paint =
        Paint().apply {
            this.typeface = typeface
            textSize = INK_REFERENCE_PX
            fontFeatureSettings = "tnum"
        }
    val bounds = Rect()
    paint.getTextBounds("0", 0, 1, bounds)
    return bounds.left / INK_REFERENCE_PX
}

/** La hora grande en el formato elegido (12 o 24 h, o el del sistema), sin «a. m.»/«p. m.». */
@Composable
fun formatClock(now: ZonedDateTime, format: ClockFormat = ClockFormat.SYSTEM): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(now, locale, format) {
        now.format(DateTimeFormatter.ofPattern(clockPattern(format, DateFormat.is24HourFormat(context)), locale))
    }
}

/**
 * Una hora pequeña (la de una alarma, un evento o la puesta del sol) en el mismo formato que la
 * hora grande. En 12 h lleva «a. m.»/«p. m.»: suelta, sin la hora grande al lado, «7:30» no dice si
 * es por la mañana o por la tarde.
 */
@Composable
fun formatShortTime(at: Long, format: ClockFormat): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(at, locale, format) {
        val pattern =
            if (clockPattern(format, DateFormat.is24HourFormat(context)) == "H:mm") "H:mm" else "h:mm a"
        Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
