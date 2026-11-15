package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.core.zones.Pinch
import dev.jesusmolano.alisio.core.zones.pinchOf

// Con los dedos más juntos que esto al empezar, la proporción entre distancias no dice nada.
private val MinPinchDistance = 48.dp

/**
 * Avisa una vez por gesto cuando dos dedos pellizcan hacia dentro o hacia fuera. Mira los toques
 * antes que lo que haya dentro (pasada inicial) y, mientras hay dos dedos, se los queda: así ni se
 * desplaza una lista ni salta una pulsación larga a mitad de pellizco. Con un solo dedo no toca nada.
 */
fun Modifier.onPinch(enabled: Boolean = true, onPinch: (Pinch) -> Unit): Modifier = if (!enabled) {
    this
} else {
    pointerInput(onPinch) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var start = 0f
            var fired = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (start == 0f) start = distance
                    event.changes.forEach { it.consume() }
                    if (!fired) {
                        pinchOf(start, distance, MinPinchDistance.toPx())?.let {
                            fired = true
                            onPinch(it)
                        }
                    }
                } else {
                    // Al levantar un dedo se empieza de cero: el siguiente par mide desde donde caiga.
                    start = 0f
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
