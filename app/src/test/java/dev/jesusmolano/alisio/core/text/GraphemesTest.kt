package dev.jesusmolano.alisio.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemesTest {
    @Test
    fun `corta por caracteres visibles`() {
        assertEquals("ab", "abc".takeGraphemes(2))
        assertEquals("abc", "abc".takeGraphemes(5))
        assertEquals("", "abc".takeGraphemes(0))
        assertEquals("", "".takeGraphemes(2))
    }

    @Test
    fun `un emoji no se parte por la mitad`() {
        val grin = "😀"
        assertEquals(grin, "${grin}x".takeGraphemes(1))
        assertEquals("a$grin", "a${grin}b".takeGraphemes(2))
    }

    @Test
    fun `una bandera cuenta como un solo caracter`() {
        val spain = "🇪🇸"
        assertEquals(spain, "${spain}x".takeGraphemes(1))
    }
}
