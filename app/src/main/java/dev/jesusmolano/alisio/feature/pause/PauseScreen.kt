package dev.jesusmolano.alisio.feature.pause

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.pause.pauseSecondsLeft
import dev.jesusmolano.alisio.data.pause.PendingLaunch
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlinx.coroutines.delay

private val PauseIconSize = 56.dp
private val ScreenMargin = 24.dp

// Cada cuánto se vuelve a mirar el reloj: bastante menos de un segundo para que el número no se salte.
private const val TICK_MILLIS = 100L

/**
 * La pausa antes de abrir una app elegida en los ajustes: su icono, una cuenta atrás y dos salidas.
 * «No abrir» vale desde el principio; «Abrir igualmente» solo cuando la cuenta llega a cero, que es
 * lo que hace de la pausa una pausa. Nada se abre solo.
 */
@Composable
fun PauseScreen(launch: PendingLaunch, onOpen: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var left by remember(launch) { mutableIntStateOf(launch.seconds) }
    LaunchedEffect(launch) {
        // Con el reloj y no contando esperas: una espera que se alarga no alarga la pausa.
        val start = SystemClock.elapsedRealtime()
        while (left > 0) {
            delay(TICK_MILLIS)
            left = pauseSecondsLeft(launch.seconds, SystemClock.elapsedRealtime() - start)
        }
    }
    val counting = left > 0
    // Al acabar, el número se apaga en su sitio: quitarlo movería el título justo cuando se va a tocar.
    val numberAlpha by animateFloatAsState(if (counting) 1f else 0f, label = "pause-number")
    val secondsLeft = pluralStringResource(R.plurals.pause_seconds_left, left, left)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AlisioTokens.Background)
                // La pausa se queda los toques: no llegan a lo que haya debajo.
                .pointerInput(Unit) {}
                .systemBarsPadding()
                .padding(ScreenMargin),
        verticalArrangement = Arrangement.Bottom
    ) {
        launch.icon?.let {
            AppIcon(it, PauseIconSize)
            Spacer(Modifier.height(24.dp))
        }
        Text(
            text = left.coerceAtLeast(1).toString(),
            style = AlisioTokens.Clock,
            color = AlisioTokens.Text,
            modifier =
                Modifier
                    .alpha(numberAlpha)
                    // Apagado sigue ocupando su sitio, pero el lector no debe anunciar una cuenta acabada.
                    .then(
                        if (counting) {
                            Modifier.semantics { contentDescription = secondsLeft }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        }
                    )
        )
        Text(
            text = stringResource(R.string.pause_title, launch.label),
            style = MaterialTheme.typography.headlineLarge,
            color = AlisioTokens.Text
        )
        Spacer(Modifier.height(40.dp))
        // «No abrir» es el botón grande y el más cercano al pulgar: la salida fácil es no entrar.
        TextButton(onClick = onOpen, enabled = !counting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.pause_open))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.pause_cancel))
        }
    }
}
