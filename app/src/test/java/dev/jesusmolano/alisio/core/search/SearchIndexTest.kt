package dev.jesusmolano.alisio.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {
    private fun items(vararg labels: String) = labels.map { IndexItem(key = it, label = it, payload = it) }

    private fun labels(hits: List<SearchHit<String>>) = hits.map { it.item.label }

    @Test
    fun `ordena por calidad`() {
        val hits = search(items("Google Maps", "Maps", "Mapas del metro", "YouTube"), "maps", Usage.NONE)
        assertEquals(listOf("Maps", "Google Maps", "Mapas del metro"), labels(hits))
    }

    @Test
    fun `no distingue acentos ni mayusculas`() {
        assertEquals(listOf("Cámara"), labels(search(items("Cámara", "Reloj"), "CAMARA", Usage.NONE)))
    }

    @Test
    fun `la frecencia adelanta a una coincidencia igual`() {
        val usage = Usage(frecency = mapOf("Spotify" to 4.0), hourBonus = emptyMap())
        assertEquals(listOf("Spotify", "Spark"), labels(search(items("Spark", "Spotify"), "sp", usage)))
    }

    @Test
    fun `en empate gana el nombre mas corto y despues el alfabeto`() {
        val hits = search(items("Notas de voz", "Notion", "Notas"), "not", Usage.NONE)
        assertEquals(listOf("Notas", "Notion", "Notas de voz"), labels(hits))
    }

    @Test
    fun `los sinonimos encuentran y el peso deja pasar a la app`() {
        val setting = IndexItem("s", "Wi-Fi", "ajuste", synonyms = listOf("red", "internet"), weight = 0.85)
        val app = IndexItem("a", "Red", "app")
        val hits = search(listOf(setting, app), "red", Usage.NONE)
        assertEquals(listOf("app", "ajuste"), hits.map { it.item.payload })
        assertTrue(hits[1].positions.isEmpty())
    }

    @Test
    fun `consulta vacia no devuelve nada`() {
        assertTrue(search(items("Maps"), "  ", Usage.NONE).isEmpty())
    }

    @Test
    fun `trescientas apps y mil contactos en menos de cincuenta milisegundos`() {
        val syllables = listOf("ma", "ri", "so", "te", "lu", "can", "pe", "dro", "go", "fi", "ne", "tu", "bla", "cha")
        val names =
            List(1300) { index ->
                val first = List(3) { syllables[(index * (it + 3) + it) % syllables.size] }.joinToString("")
                val second = List(2) { syllables[(index * (it + 5) + 7) % syllables.size] }.joinToString("")
                "$first $second $index"
            }
        val index = names.map { IndexItem(key = it, label = it, payload = it) }
        val usage = Usage(frecency = names.take(200).associateWith { 2.0 }, hourBonus = emptyMap())
        val queries = listOf("m", "ma", "mar", "mari", "pedr", "luca", "zz", "ts", "goofi", "cha 12")
        // Calentamiento: la primera pasada paga la carga de clases y el JIT.
        repeat(20) { queries.forEach { search(index, it, usage) } }
        val worst =
            queries.maxOf { query ->
                val start = System.nanoTime()
                search(index, query, usage)
                (System.nanoTime() - start) / 1_000_000.0
            }
        assertTrue("La peor consulta tardó $worst ms", worst < 50.0)
    }
}
