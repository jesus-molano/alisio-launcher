package dev.jesusmolano.alisio.core.zones

import kotlin.math.max
import kotlin.math.min

// Una isla nunca es más estrecha que dos iconos: con uno solo no cabría ni su nombre.
private const val MIN_SPAN = 2
private const val MAX_PER_ROW = 2

/**
 * Ancho de una isla en columnas de icono: tantas como apps tiene, entre [MIN_SPAN] y el ancho de
 * la pantalla. Así cada isla ocupa lo que pide su tamaño en vez de formar una rejilla uniforme (§6).
 */
fun zoneSpan(size: Int, columns: Int): Int = min(max(size, MIN_SPAN), max(columns, MIN_SPAN))

/**
 * Coloca las islas en filas sin cambiarlas de orden: dos islas pequeñas seguidas comparten fila si
 * caben, y una grande ocupa la suya. Devuelve, por fila, los índices de sus islas.
 *
 * @param sizes número de apps de cada isla, en su orden.
 * @param columns iconos que caben a lo ancho de la pantalla.
 */
fun packZones(sizes: List<Int>, columns: Int): List<List<Int>> {
    val rows = ArrayList<MutableList<Int>>()
    var free = 0
    sizes.forEachIndexed { index, size ->
        val span = zoneSpan(size, columns)
        val row = rows.lastOrNull()
        if (row != null && row.size < MAX_PER_ROW && span <= free) {
            row += index
            free -= span
        } else {
            rows += mutableListOf(index)
            free = max(columns, MIN_SPAN) - span
        }
    }
    return rows
}
