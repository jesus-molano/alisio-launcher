package dev.jesusmolano.alisio.core.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraceGridTest {
    private fun grid(count: Int, centerY: Float = 500f, maxRows: Int = 6, margin: Float = 0f) = TraceGrid(
        count = count,
        maxColumns = 4,
        maxRows = maxRows,
        cellWidth = 100f,
        cellHeight = 100f,
        right = 900f,
        centerY = centerY,
        minTop = 200f,
        maxBottom = 1000f,
        margin = margin
    )

    @Test
    fun `pocas apps forman una fila pegada al indice y a la altura del dedo`() {
        val grid = grid(count = 3)
        assertEquals(3, grid.columns)
        assertEquals(1, grid.rows)
        assertEquals(600f, grid.left)
        assertEquals(450f, grid.top)
        assertEquals(800f, grid.cellLeft(2))
    }

    @Test
    fun `muchas apps se reparten en filas en orden de lectura`() {
        val grid = grid(count = 10)
        assertEquals(4, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(500f, grid.cellLeft(4))
        assertEquals(grid.top + 100f, grid.cellTop(4))
    }

    @Test
    fun `la ultima fila incompleta se pega al indice`() {
        val grid = grid(count = 10)
        // Dos huecos a la izquierda: las dos últimas apps quedan junto al índice.
        assertEquals(700f, grid.cellLeft(8))
        assertEquals(800f, grid.cellLeft(9))
    }

    @Test
    fun `no se sale por arriba ni por abajo`() {
        assertEquals(200f, grid(count = 10, centerY = 0f).top)
        assertEquals(1000f, grid(count = 10, centerY = 5000f).bottom)
    }

    @Test
    fun `solo enseña las que caben`() {
        assertEquals(8, grid(count = 30, maxRows = 2).shown)
    }

    @Test
    fun `encuentra la app bajo el dedo`() {
        val grid = grid(count = 10)
        assertEquals(0, grid.indexAt(510f, grid.top + 10f))
        assertEquals(7, grid.indexAt(890f, grid.top + 150f))
        assertEquals(9, grid.indexAt(890f, grid.top + 250f))
    }

    @Test
    fun `un hueco de la ultima fila no elige nada`() {
        val grid = grid(count = 10)
        assertNull(grid.indexAt(510f, grid.top + 250f))
        assertNull(grid.indexAt(610f, grid.top + 250f))
    }

    @Test
    fun `el margen deja el borde de la rejilla como su ultima columna`() {
        val grid = grid(count = 3, margin = 12f)
        assertEquals(2, grid.indexAt(905f, 500f))
        assertNull(grid.indexAt(915f, 500f))
    }

    @Test
    fun `la app elegida aguanta un poco al pasar a la vecina`() {
        val grid = grid(count = 4)
        // 10 px dentro de la vecina: sigue la de antes.
        assertEquals(1, grid.indexAt(710f, 500f, current = 1))
        // 20 px dentro: ya cambia.
        assertEquals(2, grid.indexAt(720f, 500f, current = 1))
        // Sin app elegida, manda la celda.
        assertEquals(2, grid.indexAt(710f, 500f))
    }

    @Test
    fun `lejos de la rejilla no hay seleccion`() {
        val grid = grid(count = 3)
        assertNull(grid.indexAt(500f, 500f))
        assertNull(grid.indexAt(700f, 300f))
        assertNull(grid.indexAt(700f, 700f))
        assertNull(grid(count = 0).indexAt(700f, 500f))
    }
}
