package dev.jesusmolano.alisio.core.wallpaper

/**
 * Opacidad del velo que oscurece el fondo de pantalla en tres alturas: arriba (reloj y fecha), a
 * [VEIL_MIDDLE] de la altura y abajo (dock y píldora). Entre ellas se interpola.
 */
data class VeilStops(val top: Float, val middle: Float, val bottom: Float)

/** Altura, de 0 a 1, a la que el velo es más fino: ahí es donde más se ve el fondo. */
const val VEIL_MIDDLE = 0.45f

private const val TOP_BASE = 0.38f
private const val MIDDLE_BASE = 0.30f
private const val BOTTOM_BASE = 0.82f

// Abajo el velo nunca baja de lo que hace legibles las etiquetas del dock.
private const val BOTTOM_FLOOR = 0.66f

// Con un fondo claro el texto blanco necesita este mínimo arriba aunque se elija «Poco».
private const val LIGHT_FLOOR = 0.50f
private const val LIGHT_MIDDLE_RATIO = 0.6f

/**
 * Opacidades del velo del Inicio.
 *
 * @param dim cuánto oscurece la opción elegida («Poco», «Medio», «Mucho»): multiplica la base.
 * @param lightWallpaper el sistema dice que el fondo es claro (admite texto oscuro). El texto de
 *   Alisio es siempre claro, así que entonces el velo no baja de un suelo, se elija lo que se elija.
 */
fun veilStops(dim: Float, lightWallpaper: Boolean): VeilStops {
    val floor = if (lightWallpaper) LIGHT_FLOOR else 0f
    return VeilStops(
        top = (TOP_BASE * dim).coerceIn(floor, 1f),
        middle = (MIDDLE_BASE * dim).coerceIn(floor * LIGHT_MIDDLE_RATIO, 1f),
        bottom = (BOTTOM_BASE * dim).coerceIn(BOTTOM_FLOOR, 1f)
    )
}

/**
 * Opacidades del velo para el Inicio en miniatura de los ajustes. La miniatura ocupa lo alto de la
 * pantalla y debajo sigue el velo del Inicio real, que ahí tiene su opacidad de arriba: cada parada
 * es lo que falta por encima de esa capa para que la suma oscurezca como [veilStops].
 */
fun previewVeilStops(dim: Float, lightWallpaper: Boolean): VeilStops {
    val full = veilStops(dim, lightWallpaper)
    return VeilStops(
        top = 0f,
        middle = veilOver(full.middle, under = full.top),
        bottom = veilOver(full.bottom, under = full.top)
    )
}

/** Opacidad de una capa que, puesta sobre otra de opacidad [under], da en total [target]. */
private fun veilOver(target: Float, under: Float): Float =
    if (under >= 1f) 0f else ((target - under) / (1f - under)).coerceIn(0f, 1f)
