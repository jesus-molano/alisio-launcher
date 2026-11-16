package dev.jesusmolano.alisio.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class FontChoiceTest {
    @Test
    fun `cada valor se recupera por su id`() {
        FontChoice.entries.forEach { assertEquals(it, FontChoice.parse(it.id)) }
    }

    @Test
    fun `los ids no se repiten`() {
        assertEquals(FontChoice.entries.size, FontChoice.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `sin valor guardado o con uno desconocido vale la pareja por defecto`() {
        assertEquals(FontChoice.PAIR, FontChoice.parse(null))
        assertEquals(FontChoice.PAIR, FontChoice.parse(""))
        assertEquals(FontChoice.PAIR, FontChoice.parse("comic"))
    }
}
