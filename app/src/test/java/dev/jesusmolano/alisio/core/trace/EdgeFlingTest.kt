package dev.jesusmolano.alisio.core.trace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeFlingTest {
    private val slop = 20f

    @Test
    fun `salir recto y rapido del indice es atras`() {
        assertTrue(isEdgeFling(elapsedMillis = 125, verticalTravel = 5f, slop = slop))
    }

    @Test
    fun `pararse a ver la letra antes de salir es un trazo`() {
        assertFalse(isEdgeFling(elapsedMillis = EDGE_FLING_MILLIS, verticalTravel = 0f, slop = slop))
        assertFalse(isEdgeFling(elapsedMillis = 400, verticalTravel = 0f, slop = slop))
    }

    @Test
    fun `buscar la letra subiendo o bajando es un trazo aunque sea rapido`() {
        assertFalse(isEdgeFling(elapsedMillis = 120, verticalTravel = slop + 1f, slop = slop))
    }

    @Test
    fun `el temblor dentro del slop sigue contando como quieto`() {
        assertTrue(isEdgeFling(elapsedMillis = 80, verticalTravel = slop, slop = slop))
    }
}
