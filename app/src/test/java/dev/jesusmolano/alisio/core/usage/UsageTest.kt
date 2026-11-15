package dev.jesusmolano.alisio.core.usage

import dev.jesusmolano.alisio.core.usage.ForegroundChange.PAUSED
import dev.jesusmolano.alisio.core.usage.ForegroundChange.RESUMED
import dev.jesusmolano.alisio.core.usage.ForegroundChange.STOPPED
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun event(at: Long, change: ForegroundChange, activity: String = "Main") =
        ForegroundEvent(at, activity, change)

    @Test
    fun `parte el uso en horas y minutos enteros`() {
        assertEquals(UsageDuration(1, 20, false), usageDuration(hour + 20 * minute))
        assertEquals(UsageDuration(0, 35, false), usageDuration(35 * minute))
        assertEquals(UsageDuration(2, 0, false), usageDuration(2 * hour))
        assertEquals(UsageDuration(12, 5, false), usageDuration(12 * hour + 5 * minute + 59_000))
    }

    @Test
    fun `menos de un minuto no es lo mismo que nada`() {
        assertEquals(UsageDuration(0, 0, true), usageDuration(59_999))
        assertEquals(UsageDuration(0, 0, false), usageDuration(0))
        assertEquals(UsageDuration(0, 0, false), usageDuration(-5))
        assertEquals(UsageDuration(0, 1, false), usageDuration(minute))
    }

    @Test
    fun `el dia empieza a medianoche de la zona`() {
        val zone = ZoneId.of("Atlantic/Canary")
        val now = LocalDateTime.of(2026, 11, 15, 18, 30).atZone(zone).toInstant().toEpochMilli()
        val midnight = LocalDateTime.of(2026, 11, 15, 0, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(midnight, startOfDay(now, zone))
        assertEquals(midnight, startOfDay(midnight, zone))
    }

    @Test
    fun `suma los tramos entre reanudar y pausar`() {
        val events =
            listOf(event(100, RESUMED), event(400, PAUSED), event(1_000, RESUMED), event(1_250, PAUSED))
        assertEquals(550, foregroundMillis(events, from = 0, until = 5_000))
    }

    @Test
    fun `sin eventos no hay uso`() {
        assertEquals(0, foregroundMillis(emptyList(), from = 0, until = 5_000))
    }

    @Test
    fun `abierta desde antes de medianoche cuenta desde el principio`() {
        assertEquals(300, foregroundMillis(listOf(event(400, PAUSED)), from = 100, until = 5_000))
    }

    @Test
    fun `una parada suelta al empezar no cuenta como uso`() {
        // Pausada antes de medianoche y parada después: no estuvo a la vista en este tramo.
        val events = listOf(event(150, STOPPED), event(1_000, RESUMED), event(1_200, PAUSED))
        assertEquals(200, foregroundMillis(events, from = 100, until = 5_000))
    }

    @Test
    fun `si sigue abierta cuenta hasta ahora`() {
        assertEquals(4_000, foregroundMillis(listOf(event(1_000, RESUMED)), from = 0, until = 5_000))
    }

    @Test
    fun `pasar de una actividad a otra no corta ni duplica`() {
        val events =
            listOf(
                event(100, RESUMED, "Lista"),
                event(300, RESUMED, "Detalle"),
                event(310, PAUSED, "Lista"),
                event(350, STOPPED, "Lista"),
                event(900, PAUSED, "Detalle"),
                event(950, STOPPED, "Detalle")
            )
        assertEquals(800, foregroundMillis(events, from = 0, until = 5_000))
    }

    @Test
    fun `una parada cierra la actividad que no llego a pausarse`() {
        val events = listOf(event(100, RESUMED), event(600, STOPPED))
        assertEquals(500, foregroundMillis(events, from = 0, until = 5_000))
    }

    @Test
    fun `el orden de llegada no importa`() {
        val events = listOf(event(400, PAUSED), event(100, RESUMED))
        assertEquals(300, foregroundMillis(events, from = 0, until = 5_000))
    }
}
