package dev.jesusmolano.alisio.core.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNotesTest {
    @Test
    fun `una nota empieza por mas`() {
        assertEquals("comprar pan", parseNoteCommand("+ comprar pan"))
        assertEquals("comprar pan", parseNoteCommand("+comprar   pan "))
        assertEquals("Call mum", parseNoteCommand("  + Call mum"))
    }

    @Test
    fun `sin letras o sin mas no es una nota`() {
        assertNull(parseNoteCommand("+5"))
        assertNull(parseNoteCommand("+ 12 + 3"))
        assertNull(parseNoteCommand("+"))
        assertNull(parseNoteCommand("comprar pan"))
        assertNull(parseNoteCommand("5 + pan"))
    }

    @Test
    fun `una nota larga se corta`() {
        assertEquals(MAX_NOTE_LENGTH, parseNoteCommand("+ " + "a".repeat(400))!!.length)
    }

    @Test
    fun `lo compartido junta titulo y enlace`() {
        assertEquals("Receta · https://example.org/pan", sharedNote("https://example.org/pan", "Receta"))
        assertEquals("https://example.org/pan", sharedNote("https://example.org/pan", null))
        assertEquals("Mira esto https://a.es", sharedNote("Mira esto https://a.es", "esto"))
        assertEquals("Solo título", sharedNote("  ", "Solo  título"))
        assertNull(sharedNote(null, "  "))
    }

    @Test
    fun `al cortar lo compartido el enlace llega entero`() {
        val title = "T".repeat(120)
        val url = "https://example.org/articulo?" + "utm=x&".repeat(28) + "fin=1"
        assertEquals(true, url.length in 190..210)
        val note = sharedNote(url, title)!!
        assertEquals(MAX_NOTE_LENGTH, note.length)
        assertEquals(url, noteLink(note))
        // El título se acorta, pero se sigue leyendo él y no la dirección.
        assertEquals(true, noteDisplay(note).startsWith("TTT"))
    }

    @Test
    fun `un enlace que ya pasa del limite se guarda entero`() {
        val url = "https://example.org/" + "a".repeat(300)
        assertEquals(url, sharedNote(url, "Título"))
        assertEquals(url, noteLink(clampNote("Leer $url después")))
    }

    @Test
    fun `el enlace de una nota`() {
        assertEquals("https://example.org/a?b=1", noteLink("Leer https://example.org/a?b=1."))
        assertEquals("https://www.elpais.com", noteLink("ver www.elpais.com, luego"))
        assertEquals("http://x.io/p", noteLink("(http://x.io/p)"))
        assertNull(noteLink("comprar pan"))
        assertNull(noteLink("www."))
    }

    @Test
    fun `lo que se lee de una nota`() {
        assertEquals("Receta", noteDisplay("Receta · https://example.org/pan"))
        assertEquals("example.org", noteDisplay("https://www.example.org/pan"))
        assertEquals("comprar pan", noteDisplay("comprar  pan"))
        assertEquals("Leer luego", noteDisplay("Leer https://a.es/x luego"))
    }

    @Test
    fun `se ven dos y se cuentan las demas`() {
        val notes = visibleNotes(listOf("a", "b", "c", "d"))
        assertEquals(listOf("a", "b"), notes.shown)
        assertEquals(2, notes.more)
        assertEquals(0, visibleNotes(listOf("a")).more)
    }
}
