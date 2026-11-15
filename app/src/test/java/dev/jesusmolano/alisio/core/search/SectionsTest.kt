package dev.jesusmolano.alisio.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionsTest {
    @Test
    fun `las secciones salen por el orden de su mejor resultado y se cortan en su limite`() {
        val ranked = listOf("ajuste1", "app1", "app2", "ajuste2", "app3")
        val sections =
            ranked.toSections(
                sectionOf = { if (it.startsWith("app")) "apps" else "ajustes" },
                limitOf = { if (it == "apps") 2 else 5 }
            )
        assertEquals(listOf("ajustes" to listOf("ajuste1", "ajuste2"), "apps" to listOf("app1", "app2")), sections)
    }

    @Test
    fun `filtra por nombre de mejor a peor coincidencia`() {
        val apps = listOf("Google Maps", "YouTube", "Maps", "Reloj")
        assertEquals(listOf("Maps", "Google Maps"), apps.filterByName("maps") { it })
        assertEquals(apps, apps.filterByName("  ") { it })
        assertEquals(emptyList<String>(), apps.filterByName("zzz") { it })
    }

    @Test
    fun `las claves mas recientes van primero`() {
        val openings = mapOf("a" to listOf(1L, 9L), "b" to listOf(5L), "c" to listOf(10L), "d" to emptyList())
        assertEquals(listOf("c", "a", "b"), openings.mostRecentKeys())
    }

    @Test
    fun `mover un elemento conserva el orden de los demas`() {
        val apps = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), apps.moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), apps.moved(3, 0))
        assertEquals(apps, apps.moved(1, 1))
        assertEquals(apps, apps.moved(1, 9))
    }
}
