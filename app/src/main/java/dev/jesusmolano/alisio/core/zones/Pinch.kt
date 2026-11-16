package dev.jesusmolano.alisio.core.zones

enum class Pinch { IN, OUT }

/**
 * Los dedos tienen que acercarse o separarse un cuarto largo de lo que distaban: menos que eso lo
 * hace sin querer cualquier toque con dos dedos. El árbitro del Inicio (`core/gesture`) usa el mismo
 * umbral para comprometer el pellizco.
 */
const val PINCH_IN_RATIO = 0.72f
private const val PINCH_OUT_RATIO = 1.35f

/**
 * Si dos dedos que empezaron a [startDistance] y ahora están a [distance] ya han pellizcado, y hacia
 * dónde. Con los dedos casi juntos al empezar no se decide nada: la proporción sería puro ruido.
 *
 * @param minDistance separación inicial mínima, en las mismas unidades.
 */
fun pinchOf(startDistance: Float, distance: Float, minDistance: Float): Pinch? {
    if (startDistance < minDistance || startDistance <= 0f) return null
    val ratio = distance / startDistance
    return when {
        ratio <= PINCH_IN_RATIO -> Pinch.IN
        ratio >= PINCH_OUT_RATIO -> Pinch.OUT
        else -> null
    }
}
