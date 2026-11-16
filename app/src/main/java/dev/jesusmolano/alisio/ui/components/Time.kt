package dev.jesusmolano.alisio.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

/** Hora en el formato del sistema (12 o 24 h), sin «a. m.»/«p. m.». */
@Composable
fun formatClock(now: ZonedDateTime): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(now, locale) {
        val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        now.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}
