package dev.jesusmolano.alisio.feature.home

import kotlin.math.max
import kotlin.math.min

/**
 * Geometría de la rejilla de Trazo, en píxeles y sin Android: las apps de una letra en orden de
 * lectura, pegadas a [right] (el borde del índice) y centradas en [centerY] sin salirse de
 * [minTop]..[maxBottom].
 */
class TraceGrid(
    count: Int,
    maxColumns: Int,
    maxRows: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    val right: Float,
    centerY: Float,
    minTop: Float,
    maxBottom: Float
) {
    /** Las que caben; el resto sigue en la lista. */
    val shown: Int = min(count, max(maxColumns, 1) * max(maxRows, 1))
    val columns: Int = min(shown, max(maxColumns, 1)).coerceAtLeast(1)
    val rows: Int = (shown + columns - 1) / columns
    val left: Float = right - columns * cellWidth
    val top: Float = (centerY - rows * cellHeight / 2).coerceIn(minTop, max(minTop, maxBottom - rows * cellHeight))
    val bottom: Float = top + rows * cellHeight

    fun cellLeft(index: Int): Float = left + (index % columns) * cellWidth

    fun cellTop(index: Int): Float = top + (index / columns) * cellHeight

    /**
     * App bajo el dedo, con media celda de margen alrededor; `null` si el dedo se ha ido lejos
     * (soltar ahí cancela). Los huecos de la última fila cuentan como su última app.
     */
    fun indexAt(x: Float, y: Float): Int? {
        if (shown == 0) return null
        if (x < left - cellWidth / 2 || y < top - cellHeight / 2 || y > bottom + cellHeight / 2) return null
        val column = ((x - left) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((y - top) / cellHeight).toInt().coerceIn(0, rows - 1)
        return min(row * columns + column, shown - 1)
    }
}
