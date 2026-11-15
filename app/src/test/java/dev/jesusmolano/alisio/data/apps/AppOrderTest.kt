package dev.jesusmolano.alisio.data.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private fun app(label: String, serial: Long = 0) = label to AppKey("pkg.${label.lowercase()}", "Main", serial)

    private fun sorted(vararg apps: Pair<String, AppKey>) =
        apps.toList().sortedAlphabetically(label = { it.first }, key = { it.second })

    @Test
    fun `ordena sin distinguir acentos ni mayusculas`() {
        val result = sorted(app("spotify"), app("Ábaco"), app("Zoom"), app("banco"), app("Ajustes"))
        assertEquals(listOf("Ábaco", "Ajustes", "banco", "spotify", "Zoom"), result.map { it.first })
    }

    @Test
    fun `la misma app en dos perfiles queda junta y en orden estable`() {
        val result = sorted(app("Chrome", serial = 10), app("Chrome", serial = 0), app("Calendario"))
        assertEquals(listOf(0L, 10L), result.filter { it.first == "Chrome" }.map { it.second.userSerial })
        assertEquals("Calendario", result.first().first)
    }
}
