package dev.jesusmolano.alisio.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Alfa que pierde la capa con el gesto entero: se sigue viendo lo que se va a cerrar. Una pantalla
// ya deja ver el fondo por su velo; una hoja es opaca y tiene el Inicio a la vista debajo, así que
// pierde poco: más dejaría asomar la hora y el dock a través de sus filas.
internal const val SCREEN_BACK_FADE = 0.4f
internal const val SHEET_BACK_FADE = 0.1f

/**
 * El gesto atrás que sigue al dedo (docs/IDEAS.md §2.2, DISENO §2.7). Mientras dura, la capa que
 * se va a cerrar baja hasta [AlisioMotion.Rise] y pierde alfa según el progreso. Si se suelta antes
 * del final, vuelve a su sitio con Follow; si acaba, se cierra con su salida de siempre (Exit) y
 * conserva la posición mientras se desvanece, para no dar un salto.
 *
 * La capa se elige al empezar el gesto: atrás cierra siempre lo que estaba encima al tocar el borde.
 * Si el sistema no manda el progreso (One UI con algunos launchers), el gesto acaba sin moverla.
 */
@Stable
class BackFollow internal constructor(private val scope: CoroutineScope) {
    private val progress = Animatable(0f)

    /** La capa que sigue al dedo ahora o la última que lo siguió; `null` si ninguna. */
    private var layer by mutableStateOf<Any?>(null)

    /** Lo que ha avanzado el gesto sobre [layer], de 0 a 1; las demás capas, 0. */
    fun progressOf(layer: Any): Float = if (this.layer == layer) progress.value else 0f

    /** Empieza un gesto sobre [layer], desde su sitio. */
    internal suspend fun begin(layer: Any?) {
        this.layer = layer
        progress.snapTo(0f)
    }

    /** El dedo avanza: la capa baja hasta donde diga [event]. */
    internal suspend fun follow(event: BackEventCompat) {
        progress.snapTo(event.progress)
    }

    /** Devuelve la capa a su sitio: el gesto se ha cancelado o ha acabado sin cerrarla. */
    fun settle() {
        scope.launch { progress.animateTo(0f, AlisioMotion.follow()) }
    }

    /** La capa vuelve a mostrarse: empieza en su sitio aunque la última salida fuera con el gesto. */
    internal fun forget(layer: Any) {
        if (this.layer != layer) return
        this.layer = null
        scope.launch { progress.snapTo(0f) }
    }
}

@Composable
fun rememberBackFollow(): BackFollow {
    val scope = rememberCoroutineScope()
    return remember(scope) { BackFollow(scope) }
}

/**
 * El gesto atrás con progreso, sustituto de `BackHandler`. [layer] dice al empezar qué capa se
 * va a cerrar (o `null` si ninguna se mueve) y [onBack] la cierra al acabar.
 */
@Composable
fun FollowingBackHandler(back: BackFollow, enabled: Boolean = true, layer: () -> Any?, onBack: (layer: Any?) -> Unit) {
    val currentLayer by rememberUpdatedState(layer)
    val currentOnBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        val target = currentLayer()
        back.begin(target)
        try {
            events.collect { back.follow(it) }
        } catch (cancelled: CancellationException) {
            // El gesto se ha soltado antes del final: la corrutina ya está cancelada, así que la
            // vuelta a su sitio se anima fuera de ella.
            back.settle()
            throw cancelled
        }
        currentOnBack(target)
    }
}

/**
 * Modificador de la capa [layer]: baja y se desvanece con el progreso del gesto atrás.
 *
 * @param fade la alfa que pierde con el gesto entero.
 */
fun Modifier.followBack(back: BackFollow, layer: Any, fade: Float = SCREEN_BACK_FADE): Modifier = graphicsLayer {
    // Se lee al dibujar: el gesto mueve la capa sin recomponerla.
    val done = back.progressOf(layer)
    translationY = done * AlisioMotion.Rise.toPx()
    alpha = 1f - done * fade
}

/**
 * Una pantalla que se cierra con el gesto atrás, dentro de su `AnimatedVisibility` o de su
 * `AnimatedContent`. Al volver a mostrarse olvida el gesto con el que se cerró la última vez.
 */
@Composable
fun AnimatedVisibilityScope.BackFollowLayer(back: BackFollow, layer: Any, content: @Composable () -> Unit) {
    val shown = transition.targetState == EnterExitState.Visible
    LaunchedEffect(shown) { if (shown) back.forget(layer) }
    Box(Modifier.followBack(back, layer), propagateMinConstraints = true) { content() }
}
