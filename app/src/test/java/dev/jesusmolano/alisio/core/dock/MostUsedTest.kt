package dev.jesusmolano.alisio.core.dock

import org.junit.Assert.assertEquals
import org.junit.Test

class MostUsedTest {
    @Test
    fun `ordena por numero de aperturas`() {
        val openings = mapOf("a" to listOf(1L), "b" to listOf(1L, 2L, 3L), "c" to listOf(1L, 2L))
        assertEquals(listOf("b", "c", "a"), mostUsed(openings, listOf("a", "b", "c"), 6))
    }

    @Test
    fun `a igualdad gana la abierta mas recientemente`() {
        val openings = mapOf("a" to listOf(1L, 5L), "b" to listOf(2L, 9L))
        assertEquals(listOf("b", "a"), mostUsed(openings, listOf("a", "b"), 6))
    }

    @Test
    fun `ignora lo que no es una app instalada y las apps sin aperturas`() {
        val openings = mapOf("acceso" to listOf(1L, 2L, 3L), "a" to listOf(1L), "vacia" to emptyList())
        assertEquals(listOf("a"), mostUsed(openings, listOf("a", "vacia", "nunca"), 6))
    }

    @Test
    fun `no pasa del tope`() {
        val openings = (1..9).associate { "app$it" to List(it) { at -> at.toLong() } }
        assertEquals(listOf("app9", "app8"), mostUsed(openings, openings.keys, 2))
    }

    @Test
    fun `sin historial no propone nada`() {
        assertEquals(emptyList<String>(), mostUsed(emptyMap(), listOf("a"), 6))
    }
}
