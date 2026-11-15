package dev.jesusmolano.alisio.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test
    fun `quita acentos y mayusculas`() {
        assertEquals("camara", normalize("Cámara"))
        assertEquals("pinguino", normalize("PINGÜINO"))
        assertEquals("espana", normalize("España"))
    }

    @Test
    fun `recorta espacios y deja intacto lo demas`() {
        assertEquals("google maps 2", normalize("  Google Maps 2 "))
        assertEquals("", normalize(""))
    }
}
