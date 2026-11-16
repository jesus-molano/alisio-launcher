package dev.jesusmolano.alisio.feature.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * El fondo de la bienvenida: tres manchas de color que derivan despacio sobre el fondo oscuro, la
 * primera del acento elegido. Es lo único de Alisio que se mueve solo (decisión del dueño, fuera de
 * DISENO §2.7); con «Quitar animaciones» se queda quieto. La fase se lee al dibujar: moverse no
 * recompone nada.
 *
 * @param accent el acento elegido: cambia al elegir otro en el paso de estilo.
 */
@Composable
internal fun WelcomeBackground(accent: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val phase = welcomePhase()
    val others = remember(accent) { AlisioTokens.Accents.filter { it != accent }.take(2) }
    Box(
        modifier.drawBehind {
            drawRect(AlisioTokens.Background)
            val t = phase.value * 2 * PI.toFloat()
            // Manchas más pequeñas que la pantalla: separadas, cada color se ve entero y no se funden en gris.
            val radius = max(size.width, size.height) * GLOW_RADIUS
            // Cada mancha recorre su propia curva cerrada, con otro ritmo: nunca se repite el dibujo.
            val glows =
                listOf(
                    accent to Offset(0.55f + 0.3f * sin(t), 0.18f + 0.1f * cos(t * 2)),
                    others.getOrElse(0) { accent } to Offset(0.15f + 0.15f * cos(t), 0.5f + 0.15f * sin(t * 2)),
                    others.getOrElse(1) { accent } to Offset(0.9f - 0.15f * sin(t * 2), 0.62f + 0.12f * cos(t))
                )
            glows.forEach { (color, at) ->
                val center = Offset(at.x * size.width, at.y * size.height)
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            listOf(color.copy(alpha = AlisioTokens.WELCOME_GLOW_ALPHA), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                    radius = radius,
                    center = center
                )
            }
            // El pie, donde van el texto y los botones, se oscurece para que se lea sobre cualquier color.
            drawRect(
                Brush.verticalGradient(
                    0.45f to Color.Transparent,
                    1f to AlisioTokens.Scrim
                )
            )
        }
    ) {
        content()
    }
}

/** De 0 a 1 en bucle muy lento; fija en 0 sin animaciones. */
@Composable
private fun welcomePhase(): State<Float> {
    if (!AlisioMotion.animationsOn()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "welcome-glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLOW_CYCLE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "welcome-glow-phase"
    )
}

// Una vuelta entera de las manchas: despacio, para que se note el movimiento sin distraer.
private const val GLOW_CYCLE_MILLIS = 24_000

// Lo que mide cada mancha respecto al lado mayor de la pantalla.
private const val GLOW_RADIUS = 0.42f
