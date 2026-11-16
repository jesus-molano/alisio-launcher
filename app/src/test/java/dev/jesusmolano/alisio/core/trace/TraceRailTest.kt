package dev.jesusmolano.alisio.core.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRailTest {
    private fun place(count: Int, softTop: Float = 100f, hardTop: Float = 100f) = RestRail.place(
        count = count,
        softTop = softTop,
        hardTop = hardTop,
        bottom = 1000f,
        reachTop = 600f,
        maxPitch = 20f,
        minPitch = 10f
    )

    @Test
    fun `pocas letras van pegadas abajo con su separacion completa`() {
        val rail = place(count = 10)
        assertEquals(800f, rail.top)
        assertEquals(20f, rail.pitch)
    }

    @Test
    fun `muchas letras se aprietan sin pasar del alcance del pulgar`() {
        val rail = place(count = 26)
        assertEquals(600f, rail.top)
        assertEquals(400f / 26, rail.pitch, 0.001f)
    }

    @Test
    fun `si no caben ni apretadas suben lo justo`() {
        val rail = place(count = 50)
        assertEquals(500f, rail.top)
        assertEquals(10f, rail.pitch)
    }

    @Test
    fun `nunca pasan del tope duro`() {
        val rail = place(count = 200, softTop = 300f, hardTop = 300f)
        assertEquals(300f, rail.top)
    }

    @Test
    fun `el dedo elige la letra bajo el`() {
        val rail = RestRail(count = 4, top = 0f, bottom = 400f)
        assertEquals(0, rail.letterAt(-50f))
        assertEquals(1, rail.letterAt(150f))
        assertEquals(3, rail.letterAt(399f))
        assertEquals(3, rail.letterAt(900f))
        assertEquals(250f, rail.centerOf(2))
    }

    @Test
    fun `apretadas se escriben unas si y otras no, siempre los extremos`() {
        val rail = RestRail(count = 6, top = 0f, bottom = 48f)
        val step = rail.labelStep(minLabelPitch = 14f)
        assertEquals(2, step)
        // Los extremos y, entre ellos, repartidas: nunca dos escritas seguidas.
        assertEquals(setOf(0, 3, 5), rail.labels(step))
        assertEquals(1, RestRail(count = 6, top = 0f, bottom = 120f).labelStep(14f))
        assertEquals((0 until 6).toSet(), rail.labels(1))
    }

    @Test
    fun `con almohadilla delante la A se escribe y el final no deja huecos dobles`() {
        // «#» y las 26 letras, a una de cada dos.
        val rail = RestRail(count = 27, top = 0f, bottom = 27 * 10f)
        val written = rail.labels(step = 2, first = 1)
        assertTrue(1 in written)
        assertTrue(26 in written)
        assertFalse(0 in written)
        val sorted = written.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it in 2..3 })
        // Lo que queda antes de la última es un hueco normal, no dos puntos de más.
        assertEquals(2, gaps.last())
    }

    @Test
    fun `una preferencia desconocida enseña el indice`() {
        assertEquals(TraceRail.ALWAYS, TraceRail.parse(null))
        assertEquals(TraceRail.ALWAYS, TraceRail.parse("raro"))
        assertEquals(TraceRail.EDGE, TraceRail.parse("edge"))
    }
}
