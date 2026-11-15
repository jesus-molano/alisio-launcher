package dev.jesusmolano.alisio.core.now

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowEngineTest {
    private val zone = ZoneOffset.UTC

    // Miércoles 11 de noviembre de 2026, 09:00.
    private val now = at(2026, 11, 11, 9, 0)
    private val hour = 60L * 60 * 1000

    // El coche, conectado hace un minuto.
    private val justConnected = ConnectedAudio("Coche", now - 60_000)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(zone).toEpochMilli()

    private fun input(
        apps: List<NowApp> = listOf(NowApp("gym", 0), NowApp("news", 0), NowApp("music", 0)),
        openings: Map<String, List<Long>> = emptyMap(),
        dockKeys: Set<String> = emptySet(),
        bluetooth: ConnectedAudio? = null,
        bluetoothOpenings: Map<String, Map<String, Int>> = emptyMap(),
        feedback: Map<Pair<NowRule, String>, NowFeedback> = emptyMap()
    ) = NowInput(now, zone, apps, openings, dockKeys, bluetooth, bluetoothOpenings, feedback)

    /** Aperturas a las 09:10 en los laborables anteriores que se indiquen (1 = ayer laborable). */
    private fun weekdayMornings(vararg daysAgo: Int): List<Long> = daysAgo.map { at(2026, 11, 11 - it, 9, 10) }

    @Test
    fun `sin datos no sugiere nada`() {
        assertNull(suggest(input()))
    }

    @Test
    fun `una version nueva de menos de un dia se sugiere con su hora`() {
        val updated = now - 2 * hour
        val apps = listOf(NowApp("gym", updated), NowApp("news", now - 30 * hour))
        assertEquals(NowSuggestion(NowRule.NEW_VERSION, "gym", since = updated), suggest(input(apps = apps)))
    }

    @Test
    fun `entre dos versiones nuevas gana la mas reciente`() {
        val apps = listOf(NowApp("gym", now - 5 * hour), NowApp("news", now - hour))
        assertEquals("news", suggest(input(apps = apps))?.appKey)
    }

    @Test
    fun `una version nueva ya abierta deja de sugerirse`() {
        val updated = now - 2 * hour
        val apps = listOf(NowApp("gym", updated))
        assertNull(suggest(input(apps = apps, openings = mapOf("gym" to listOf(updated + hour)))))
        // Haberla abierto antes de actualizar no cuenta.
        assertEquals("gym", suggest(input(apps = apps, openings = mapOf("gym" to listOf(updated - hour))))?.appKey)
    }

    @Test
    fun `con un dispositivo conectado gana la app que mas se abre con el`() {
        val cases = mapOf("Coche" to mapOf("music" to 5, "news" to 3, "gym" to 2), "Cascos" to mapOf("gym" to 9))
        val suggestion = suggest(input(bluetooth = justConnected, bluetoothOpenings = cases))
        assertEquals(NowSuggestion(NowRule.BLUETOOTH, "music", device = "Coche"), suggestion)
    }

    @Test
    fun `menos de tres casos con el dispositivo no bastan`() {
        assertNull(suggest(input(bluetooth = justConnected, bluetoothOpenings = mapOf("Coche" to mapOf("music" to 2)))))
    }

    @Test
    fun `pasado un rato desde la conexion el dispositivo deja de sugerir y pasa la costumbre`() {
        val cases = mapOf("Coche" to mapOf("music" to 5))
        val habit = mapOf("gym" to weekdayMornings(1, 2, 5))
        val lastMinute = ConnectedAudio("Coche", now - BLUETOOTH_SUGGEST_MILLIS + 1)
        assertEquals(
            "music",
            suggest(input(openings = habit, bluetooth = lastMinute, bluetoothOpenings = cases))?.appKey
        )
        val longAgo = ConnectedAudio("Coche", now - BLUETOOTH_SUGGEST_MILLIS)
        assertEquals(
            NowSuggestion(NowRule.HABIT, "gym"),
            suggest(input(openings = habit, bluetooth = longAgo, bluetoothOpenings = cases))
        )
    }

    @Test
    fun `la app ya abierta en esta conexion deja paso a la siguiente`() {
        val cases = mapOf("Coche" to mapOf("music" to 5, "news" to 3))
        val opened = mapOf("music" to listOf(justConnected.since + 1000))
        assertEquals(
            "news",
            suggest(input(openings = opened, bluetooth = justConnected, bluetoothOpenings = cases))?.appKey
        )
        // Haberla abierto antes de conectar no cuenta.
        val before = mapOf("music" to listOf(justConnected.since - hour))
        assertEquals(
            "music",
            suggest(input(openings = before, bluetooth = justConnected, bluetoothOpenings = cases))?.appKey
        )
    }

    @Test
    fun `una app abierta a esta hora tres laborables de los ultimos cinco se sugiere`() {
        // Hoy es miércoles: los cinco laborables anteriores son martes, lunes, viernes, jueves y miércoles.
        val openings = mapOf("gym" to weekdayMornings(1, 2, 5))
        assertEquals(NowSuggestion(NowRule.HABIT, "gym"), suggest(input(openings = openings)))
    }

    @Test
    fun `el fin de semana no cuenta para un laborable`() {
        // Sábado 7 y domingo 8 no son días como hoy; solo queda el martes.
        val openings = mapOf("gym" to listOf(at(2026, 11, 7, 9, 0), at(2026, 11, 8, 9, 0), at(2026, 11, 10, 9, 0)))
        assertNull(suggest(input(openings = openings)))
    }

    @Test
    fun `dos dias, otra hora, ya abierta hoy o en el dock no se sugieren`() {
        assertNull(suggest(input(openings = mapOf("gym" to weekdayMornings(1, 2)))))
        val evenings = listOf(1, 2, 5).map { at(2026, 11, 11 - it, 18, 0) }
        assertNull(suggest(input(openings = mapOf("gym" to evenings))))
        val openedToday = weekdayMornings(1, 2, 5) + at(2026, 11, 11, 7, 0)
        assertNull(suggest(input(openings = mapOf("gym" to openedToday))))
        assertNull(suggest(input(openings = mapOf("gym" to weekdayMornings(1, 2, 5)), dockKeys = setOf("gym"))))
    }

    @Test
    fun `varias aperturas el mismo dia cuentan como un dia`() {
        val sameDay = listOf(at(2026, 11, 10, 8, 50), at(2026, 11, 10, 9, 5), at(2026, 11, 10, 9, 20))
        assertNull(suggest(input(openings = mapOf("gym" to sameDay))))
    }

    @Test
    fun `las reglas van por prioridad`() {
        val everything =
            input(
                apps = listOf(NowApp("gym", now - hour), NowApp("news", 0), NowApp("music", 0)),
                openings = mapOf("news" to weekdayMornings(1, 2, 5)),
                bluetooth = justConnected,
                bluetoothOpenings = mapOf("Coche" to mapOf("music" to 4))
            )
        assertEquals(NowRule.NEW_VERSION, suggest(everything)?.rule)
    }

    @Test
    fun `descartar oculta seis horas y deja pasar a la regla siguiente`() {
        val openings = mapOf("news" to weekdayMornings(1, 2, 5))
        val apps = listOf(NowApp("gym", now - hour), NowApp("news", 0))
        val dismissed = mapOf((NowRule.NEW_VERSION to "gym") to NowFeedback(1, now - hour))
        assertEquals("news", suggest(input(apps = apps, openings = openings, feedback = dismissed))?.appKey)
        val longAgo = mapOf((NowRule.NEW_VERSION to "gym") to NowFeedback(1, now - 7 * hour))
        assertEquals("gym", suggest(input(apps = apps, openings = openings, feedback = longAgo))?.appKey)
    }

    @Test
    fun `tres descartes silencian esa sugerencia para siempre`() {
        val openings = mapOf("gym" to weekdayMornings(1, 2, 5))
        val silenced = mapOf((NowRule.HABIT to "gym") to NowFeedback(3, now - 100 * hour))
        assertNull(suggest(input(openings = openings, feedback = silenced)))
    }

    @Test
    fun `una app desinstalada no se sugiere`() {
        val openings = mapOf("borrada" to weekdayMornings(1, 2, 5))
        assertNull(suggest(input(openings = openings)))
        assertNull(
            suggest(input(bluetooth = justConnected, bluetoothOpenings = mapOf("Coche" to mapOf("borrada" to 9))))
        )
    }

    @Test
    fun `pasada la medianoche no se sugiere lo abierto un rato antes`() {
        // Jueves 12 a las 00:10; las aperturas son a las 23:50 de los laborables anteriores.
        val midnight = at(2026, 11, 12, 0, 10)
        val lateNights = listOf(11, 10, 9, 6).map { at(2026, 11, it, 23, 50) }
        val apps = listOf(NowApp("gym", 0))
        assertNull(suggest(NowInput(midnight, zone, apps, mapOf("gym" to lateNights), emptySet())))
        // Sin la apertura de anoche sí toca: tres noches de las últimas cinco.
        val earlier = lateNights.drop(1)
        assertEquals("gym", suggest(NowInput(midnight, zone, apps, mapOf("gym" to earlier), emptySet()))?.appKey)
    }
}
