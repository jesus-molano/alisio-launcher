package dev.jesusmolano.alisio.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSectionsTest {
    @Test
    fun `la inicial ignora acentos y mayusculas`() {
        assertEquals("A", sectionLetter("Ábaco"))
        assertEquals("N", sectionLetter("ñu"))
        assertEquals("S", sectionLetter(" spotify"))
    }

    @Test
    fun `lo que no empieza por letra va a la almohadilla`() {
        assertEquals("#", sectionLetter("1Password"))
        assertEquals("#", sectionLetter("微信"))
        assertEquals("#", sectionLetter(""))
    }

    @Test
    fun `agrupa en orden y recuerda donde empieza cada letra`() {
        val sections = listOf("1Password", "Ábaco", "Ajustes", "banco", "Zoom").toSections { it }
        assertEquals(listOf("#", "A", "B", "Z"), sections.map { it.letter })
        assertEquals(listOf(0, 1, 3, 4), sections.map { it.firstIndex })
        assertEquals(listOf("Ábaco", "Ajustes"), sections[1].items)
    }

    @Test
    fun `los simbolos del principio y del final comparten seccion`() {
        val sections = listOf("1Password", "Zoom", "微信").toSections { it }
        assertEquals(listOf("#", "Z"), sections.map { it.letter })
        assertEquals(listOf("1Password", "微信"), sections[0].items)
    }
}
