package dev.jesusmolano.alisio.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test
    fun `lee objetos, listas y valores sueltos`() {
        val root = parseJson("""{"a": 1.5, "b": [1, -2e1, true, null], "c": {"d": "x"}}""") as Map<*, *>
        assertEquals(1.5, root.number("a"))
        assertEquals(listOf(1.0, -20.0, true, null), root.list("b"))
        assertEquals("x", root.obj("c")?.text("d"))
    }

    @Test
    fun `entiende los escapes y los caracteres fuera del ASCII`() {
        val root = parseJson("""{"n": "Málaga \"sur\"\n", "e": "Córdoba"}""") as Map<*, *>
        assertEquals("Málaga \"sur\"\n", root.text("n"))
        assertEquals("Córdoba", root.text("e"))
    }

    @Test
    fun `un objeto y una lista vacíos son válidos`() {
        assertEquals(emptyMap<String, Any?>(), parseJson(" {} "))
        assertEquals(emptyList<Any?>(), parseJson("[]"))
    }

    @Test
    fun `lo que falta o es de otro tipo da null`() {
        val root = parseJson("""{"a": "1"}""") as Map<*, *>
        assertNull(root.number("a"))
        assertNull(root.obj("b"))
    }

    @Test
    fun `un texto mal formado avisa en vez de inventar`() {
        listOf("{", """{"a" 1}""", "[1,]", "tru", """{"a": 1} x""", "\"sin cerrar").forEach { bad ->
            val failed = runCatching { parseJson(bad) }.exceptionOrNull()
            assertTrue("«$bad» debería fallar", failed is JsonException)
        }
    }
}
