package dev.jesusmolano.alisio.core.draw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialsTest {
    private val items = listOf("Spotify", "Slack", "Sara", "Signal", "Maps", "Ñu")

    private fun pick(letter: Char, usage: Map<String, Double>) = pickByInitial(letter, items, { it }, { it }, usage)

    @Test
    fun `la inicial sin tilde y en mayuscula`() {
        assertEquals('A', initialOf("ágora"))
        assertEquals('N', initialOf("Ñu"))
        assertEquals('4', initialOf("4Ruedas"))
        assertNull(initialOf("  "))
    }

    @Test
    fun `el mas usado se abre si destaca`() {
        val result = pick('S', mapOf("Slack" to 5.0, "Spotify" to 1.0))
        assertEquals("Slack", (result as InitialPick.Open).item)
    }

    @Test
    fun `un empate deja elegir entre los tres primeros`() {
        val result = pick('S', mapOf("Slack" to 5.0, "Sara" to 4.5, "Spotify" to 1.0))
        assertEquals(listOf("Slack", "Sara", "Spotify"), (result as InitialPick.Choose).items)
    }

    @Test
    fun `sin uso se elige por orden alfabetico`() {
        val result = pick('S', emptyMap())
        assertTrue(result is InitialPick.Choose)
        assertEquals(listOf("Sara", "Signal", "Slack"), (result as InitialPick.Choose).items)
    }

    @Test
    fun `uno solo se abre aunque no se use`() {
        assertEquals("Maps", (pick('M', emptyMap()) as InitialPick.Open).item)
        assertEquals("Ñu", (pick('N', emptyMap()) as InitialPick.Open).item)
    }

    @Test
    fun `nada con esa letra`() {
        assertNull(pick('Z', emptyMap()))
    }
}
