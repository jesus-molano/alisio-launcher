package dev.jesusmolano.alisio.core.trace

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * El índice mientras dura un trazo: las letras se abren desde el dedo. La letra tocada
 * ([anchor]) se queda bajo él, en [anchorY], y las demás se separan hacia arriba y hacia abajo
 * hasta [maxPitch] o hasta llenar el sitio que hay a cada lado ([minTop]..[maxBottom], centros de
 * letra en píxeles). Así cada lado usa todo su alto: con el pie del Inicio oculto, las letras de
 * abajo bajan hasta el pulgar en vez de apretarse.
 *
 * El índice en reposo cabe dentro de esos límites, así que ninguna letra queda más junta que en
 * reposo.
 */
class TraceBand(val count: Int, val anchor: Int, anchorY: Float, minTop: Float, maxBottom: Float, maxPitch: Float) {
    val anchorY: Float = anchorY.coerceIn(minTop, maxOf(minTop, maxBottom))

    /** Separación entre letras por encima de la tocada. */
    val pitchAbove: Float = if (anchor <= 0) maxPitch else min(maxPitch, (this.anchorY - minTop) / anchor).atLeast()

    /** Separación entre letras por debajo de la tocada. */
    val pitchBelow: Float = when {
        anchor >= count - 1 -> maxPitch
        else -> min(maxPitch, (maxBottom - this.anchorY) / (count - 1 - anchor)).atLeast()
    }

    fun centerOf(index: Int): Float = anchorY + (index - anchor) * (if (index < anchor) pitchAbove else pitchBelow)

    /** Posición continua del dedo en letras (2,5 es entre la tercera y la cuarta), para la lupa. */
    fun positionAt(y: Float): Float {
        val position =
            if (y < anchorY) anchor - (anchorY - y) / pitchAbove else anchor + (y - anchorY) / pitchBelow
        return position.coerceIn(0f, (count - 1).toFloat())
    }

    /**
     * La letra bajo [y]. Con [current], no se cambia hasta pasar la frontera con la vecina en
     * [hysteresis] veces la separación: un dedo que tiembla en el límite no hace saltar la letra.
     */
    fun letterAt(y: Float, current: Int? = null, hysteresis: Float = HYSTERESIS): Int {
        val raw = positionAt(y).roundToInt().coerceIn(0, count - 1)
        if (current == null || raw == current || current !in 0 until count) return raw
        // La separación del hueco hacia la vecina: por encima de la tocada vale pitchAbove.
        val upperGap = if (y < centerOf(current)) current - 1 else current
        val pitch = if (upperGap < anchor) pitchAbove else pitchBelow
        return if (abs(y - centerOf(current)) < pitch * (0.5f + hysteresis)) current else raw
    }

    private fun Float.atLeast(): Float = if (this > MIN_PITCH) this else MIN_PITCH

    companion object {
        /** Cuánto hay que pasar la frontera, en separaciones de letra, para cambiar de letra. */
        const val HYSTERESIS = 0.25f

        // Solo protege de dividir por cero si el dedo cae fuera de los límites.
        private const val MIN_PITCH = 1f
    }
}
