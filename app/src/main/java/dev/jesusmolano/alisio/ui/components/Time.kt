package dev.jesusmolano.alisio.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
