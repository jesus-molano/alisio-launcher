package dev.jesusmolano.alisio.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Movimiento de docs/DISENO.md §2.7. Solo se animan la alfa y un desplazamiento corto: lo que entra
 * sube un poco y aparece; lo que sale se desvanece. Sin rebotes ni nada en bucle.
 *
 * Compose ya multiplica estas duraciones por la escala de animaciones del sistema: con las
 * animaciones desactivadas valen 0.
 */
object AlisioMotion {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val Leave = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Lo que entra nunca recorre más que esto. */
    val Rise = 12.dp

    /** Pulsado, marcas y anillo de elegido. */
    fun <T> fast(): FiniteAnimationSpec<T> = tween(120, easing = LinearOutSlowInEasing)

    /** Subpáginas, ocultar el Inicio, aparecer Ahora y el reproductor, cambio de contenido de una hoja. */
    fun <T> standard(): FiniteAnimationSpec<T> = tween(220, easing = Emphasized)

    /** Pantallas que tapan el Inicio: barra, Zonas y ajustes. */
    fun <T> screen(): FiniteAnimationSpec<T> = tween(320, easing = Emphasized)

    /** Toda salida. */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(160, easing = Leave)

    /** Lo que sigue al dedo y vuelve a su sitio al soltar. */
    fun <T> follow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 500f)

    /** Entrada de una pantalla que tapa el Inicio: aparece subiendo [Rise]. */
    @Composable
    fun screenEnter(): EnterTransition {
        val rise = with(LocalDensity.current) { Rise.roundToPx() }
        return fadeIn(screen()) + slideInVertically(screen()) { rise }
    }

    /** Salida de cualquier pantalla: solo se desvanece. */
    fun screenExit(): ExitTransition = fadeOut(exit())
}
