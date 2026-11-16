package dev.jesusmolano.alisio.core.trace

import org.junit.Assert.assertEquals
import org.junit.Test

class TraceBandTest {
    private fun band(anchor: Int, anchorY: Float, count: Int = 26) = TraceBand(
        count = count,
        anchor = anchor,
        anchorY = anchorY,
        minTop = 100f,
        maxBottom = 1700f,
        maxPitch = 80f
    )

    @Test
    fun `la letra tocada se queda bajo el dedo`() {
        val band = band(anchor = 12, anchorY = 900f)
        assertEquals(900f, band.centerOf(12))
        assertEquals(12, band.letterAt(900f))
    }

    @Test
    fun `cada lado se abre hasta su limite`() {
        val band = band(anchor = 12, anchorY = 900f)
        // 800 px para 12 letras por encima; 800 px para 13 por debajo.
        assertEquals(800f / 12, band.pitchAbove, 0.001f)
        assertEquals(800f / 13, band.pitchBelow, 0.001f)
        assertEquals(100f, band.centerOf(0), 0.01f)
        assertEquals(1700f, band.centerOf(25), 0.01f)
    }

    @Test
    fun `con sitio de sobra no pasa de la separacion maxima`() {
        val band = band(anchor = 0, anchorY = 300f, count = 5)
        assertEquals(80f, band.pitchBelow)
        assertEquals(380f, band.centerOf(1))
    }

    @Test
    fun `tocar abajo deja las letras de arriba a la vista`() {
        val band = band(anchor = 25, anchorY = 1700f)
        assertEquals(64f, band.pitchAbove, 0.001f)
        assertEquals(0, band.letterAt(100f))
    }

    @Test
    fun `cambiar de letra pide pasar la frontera`() {
        val band = band(anchor = 0, anchorY = 300f, count = 5)
        // Frontera entre 0 y 1 en 340; con histéresis, en 360.
        assertEquals(1, band.letterAt(345f))
        assertEquals(0, band.letterAt(345f, current = 0))
        assertEquals(1, band.letterAt(365f, current = 0))
        // Y lo mismo al volver.
        assertEquals(1, band.letterAt(335f, current = 1))
        assertEquals(0, band.letterAt(315f, current = 1))
    }

    @Test
    fun `fuera de la franja vale la letra del extremo`() {
        val band = band(anchor = 2, anchorY = 900f, count = 5)
        assertEquals(0, band.letterAt(-500f))
        assertEquals(4, band.letterAt(5000f))
        assertEquals(0f, band.positionAt(-500f))
    }

    @Test
    fun `un dedo fuera de los limites no rompe la franja`() {
        val band = band(anchor = 3, anchorY = 50f, count = 5)
        assertEquals(100f, band.anchorY)
        assertEquals(3, band.letterAt(100f))
    }
}
