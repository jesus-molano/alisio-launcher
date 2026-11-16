package dev.jesusmolano.alisio.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoritesTest {
    @Test
    fun `se guardan y se leen en su orden`() {
        val keys = listOf("person:b", "person:a")
        assertEquals(keys, parseFavorites(formatFavorites(keys)))
        assertEquals(emptyList<String>(), parseFavorites(null))
        assertEquals(listOf("x"), parseFavorites("x\n\nx\n"))
    }

    @Test
    fun `poner y quitar`() {
        assertEquals(listOf("a", "b"), toggleFavorite(listOf("a"), "b"))
        assertEquals(listOf("b"), toggleFavorite(listOf("a", "b"), "a"))
    }

    @Test
    fun `no caben mas de cinco`() {
        val full = listOf("a", "b", "c", "d", "e")
        assertNull(toggleFavorite(full, "f"))
        assertEquals(listOf("a", "b", "c", "d"), toggleFavorite(full, "e"))
        assertEquals(5, parseFavorites((1..7).joinToString("\n")).size)
    }
}
