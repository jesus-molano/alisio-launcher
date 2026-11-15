package dev.jesusmolano.alisio.core.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private fun quality(query: String, name: String) = match(query, name)?.quality

    @Test
    fun `cada regla da su calidad`() {
        assertEquals(QUALITY_EXACT, quality("maps", "maps"))
        assertEquals(QUALITY_PREFIX, quality("you", "youtube"))
        assertEquals(QUALITY_WORD_START, quality("maps", "google maps"))
        assertEquals(QUALITY_INITIALS, quality("gm", "google maps"))
        assertEquals(QUALITY_SUBSEQUENCE, quality("tube", "youtube"))
        assertEquals(QUALITY_TYPO, quality("spotufy", "spotify music"))
    }

    @Test
    fun `gana la regla mas estricta`() {
        // «ss» es a la vez prefijo y secuencia de «ssh server».
        assertEquals(QUALITY_PREFIX, quality("ss", "ssh server"))
    }

    @Test
    fun `sin coincidencia devuelve null`() {
        assertNull(match("zzz", "youtube"))
        assertNull(match("", "youtube"))
    }

    @Test
    fun `la errata solo cuenta con cuatro letras o mas`() {
        assertNull(match("spx", "spotify"))
        assertEquals(QUALITY_TYPO, quality("sptoify", "spotify"))
        assertEquals(QUALITY_TYPO, quality("whatsaap", "whatsapp business"))
        assertEquals(QUALITY_TYPO, quality("mapz", "google maps"))
    }

    @Test
    fun `dos erratas no cuentan`() {
        assertNull(match("spxtxfy", "spotify"))
    }

    @Test
    fun `las posiciones marcan lo que coincide`() {
        assertArrayEquals(intArrayOf(7, 8, 9, 10), match("maps", "google maps")!!.positions)
        assertArrayEquals(intArrayOf(0, 7), match("gm", "google maps")!!.positions)
        assertArrayEquals(intArrayOf(3, 4, 5, 6), match("tube", "youtube")!!.positions)
        assertTrue(match("spotufy", "spotify")!!.positions.isEmpty())
    }

    @Test
    fun `una edicion de distancia`() {
        assertTrue(withinOneEdit("casa", "cosa"))
        assertTrue(withinOneEdit("casa", "cas"))
        assertTrue(withinOneEdit("casa", "casas"))
        assertTrue(withinOneEdit("casa", "caas"))
        assertTrue(withinOneEdit("casa", "casa"))
        assertTrue(!withinOneEdit("casa", "coso"))
        assertTrue(!withinOneEdit("casa", "ca"))
    }
}
