package dev.jesusmolano.alisio.core.trace

import kotlin.math.max
import kotlin.math.min

/**
 * Geometría de la rejilla de Trazo, en píxeles y sin Android: las apps de una letra en orden de
 * lectura, pegadas a [right] (el borde del índice) y centradas en [centerY] sin salirse de
 * [minTop]..[maxBottom]. La última fila, si no está llena, se pega también a la derecha: así los
 * huecos quedan lejos del índice, de donde llega el dedo, y no entre él y las apps.
 *
 * @param margin cuánto se acepta el dedo fuera de la rejilla como si estuviera en su borde.
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
    maxBottom: Float,
    private val margin: Float = 0f
) {
    /** Las que caben; el resto se busca en la barra o en Zonas. */
    val shown: Int = min(count, max(maxColumns, 1) * max(maxRows, 1))
    val columns: Int = min(shown, max(maxColumns, 1)).coerceAtLeast(1)
    val rows: Int = (shown + columns - 1) / columns
    val left: Float = right - columns * cellWidth
    val top: Float = (centerY - rows * cellHeight / 2).coerceIn(minTop, max(minTop, maxBottom - rows * cellHeight))
    val bottom: Float = top + rows * cellHeight

    // Huecos de la última fila, que quedan a su izquierda.
    private val lastRowGap: Int = rows * columns - shown

    fun column(index: Int): Int = index % columns + if (index / columns == rows - 1) lastRowGap else 0

    fun cellLeft(index: Int): Float = left + column(index) * cellWidth

    fun cellTop(index: Int): Float = top + (index / columns) * cellHeight

    /**
     * App bajo el dedo, o `null` si está fuera de la rejilla (más allá de [margin]) o sobre un hueco:
     * soltar ahí cancela. Con [current], la app elegida se mantiene hasta que el dedo entra en la
     * vecina [stick] veces su tamaño: el temblor del dedo en una frontera no cambia la app.
     */
    fun indexAt(x: Float, y: Float, current: Int? = null, stick: Float = STICK): Int? {
        if (shown == 0) return null
        if (current != null && current in 0 until shown) {
            val slackX = cellWidth * stick
            val slackY = cellHeight * stick
            val cellX = cellLeft(current)
            val cellY = cellTop(current)
            if (x >= cellX - slackX && x <= cellX + cellWidth + slackX &&
                y >= cellY - slackY && y <= cellY + cellHeight + slackY
            ) {
                return current
            }
        }
        if (x < left - margin || x > right + margin || y < top - margin || y > bottom + margin) return null
        val column = ((x - left) / cellWidth).toInt().coerceIn(0, columns - 1)
        val row = ((y - top) / cellHeight).toInt().coerceIn(0, rows - 1)
        val inRow = if (row == rows - 1) column - lastRowGap else column
        return if (inRow < 0) null else row * columns + inRow
    }

    companion object {
        /** Cuánto hay que entrar en la celda vecina, en fracción de celda, para cambiar de app. */
        const val STICK = 0.15f
    }
}
