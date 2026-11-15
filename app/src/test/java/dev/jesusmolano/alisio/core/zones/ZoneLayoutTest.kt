package dev.jesusmolano.alisio.core.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneLayoutTest {
    @Test
    fun `una isla ocupa tantas columnas como apps, entre dos y el ancho`() {
        assertEquals(2, zoneSpan(0, columns = 4))
        assertEquals(2, zoneSpan(1, columns = 4))
        assertEquals(3, zoneSpan(3, columns = 4))
        assertEquals(4, zoneSpan(9, columns = 4))
    }

    @Test
    fun `dos islas pequenas seguidas comparten fila`() {
        // Diario (5), Dinero (2), Dev (2) y Sistema (6) en una pantalla de 4 columnas.
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), packZones(listOf(5, 2, 2, 6), columns = 4))
    }

    @Test
    fun `en una pantalla mas ancha caben anchos distintos en la misma fila`() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), packZones(listOf(3, 2, 2), columns = 5))
    }

    @Test
    fun `nunca mas de dos islas por fila ni cambios de orden`() {
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), packZones(listOf(2, 2, 2, 8), columns = 6))
    }

    @Test
    fun `una isla que no cabe junto a la anterior empieza fila`() {
        assertEquals(listOf(listOf(0), listOf(1)), packZones(listOf(3, 2), columns = 4))
        assertEquals(emptyList<List<Int>>(), packZones(emptyList(), columns = 4))
    }

    @Test
    fun `pellizco hacia dentro y hacia fuera`() {
        assertEquals(Pinch.IN, pinchOf(startDistance = 400f, distance = 280f, minDistance = 100f))
        assertEquals(Pinch.OUT, pinchOf(startDistance = 200f, distance = 300f, minDistance = 100f))
        assertNull(pinchOf(startDistance = 400f, distance = 360f, minDistance = 100f))
    }

    @Test
    fun `con los dedos casi juntos al empezar no hay pellizco`() {
        assertNull(pinchOf(startDistance = 40f, distance = 10f, minDistance = 100f))
        assertNull(pinchOf(startDistance = 0f, distance = 500f, minDistance = 0f))
    }
}
