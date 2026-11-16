package dev.jesusmolano.alisio.core.glance

import dev.jesusmolano.alisio.core.glance.GlanceItem.ALARM
import dev.jesusmolano.alisio.core.glance.GlanceItem.BATTERY
import dev.jesusmolano.alisio.core.glance.GlanceItem.EVENT
import dev.jesusmolano.alisio.core.glance.GlanceItem.SUN
import dev.jesusmolano.alisio.core.glance.GlanceItem.WEATHER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceTest {
    private val hour = 60 * 60_000L

    @Test
    fun `sin nada guardado salen las de por defecto`() {
        assertEquals(DEFAULT_GLANCE, parseGlance(null))
        // Vaciar la línea es una elección: no vuelven las de por defecto.
        assertEquals(emptyList<GlanceItem>(), parseGlance(""))
    }

    @Test
    fun `guarda y lee el orden, sin repetir y sin lo desconocido`() {
        assertEquals("weather,alarm", formatGlance(listOf(WEATHER, ALARM)))
        assertEquals(listOf(WEATHER, ALARM), parseGlance("weather,alarm"))
        assertEquals(listOf(SUN, BATTERY), parseGlance("sun, futuro ,battery,sun"))
    }

    @Test
    fun `encender añade al final y apagar quita`() {
        assertEquals(listOf(ALARM, BATTERY, EVENT), toggleGlance(listOf(ALARM, BATTERY), EVENT))
        assertEquals(listOf(BATTERY), toggleGlance(listOf(ALARM, BATTERY), ALARM))
    }

    @Test
    fun `subir cambia el puesto con el de arriba`() {
        assertEquals(listOf(BATTERY, ALARM, EVENT), raiseGlance(listOf(ALARM, BATTERY, EVENT), BATTERY))
        assertEquals(listOf(ALARM, BATTERY), raiseGlance(listOf(ALARM, BATTERY), ALARM))
        assertEquals(listOf(ALARM), raiseGlance(listOf(ALARM), WEATHER))
    }

    @Test
    fun `la batería se enseña según el umbral`() {
        assertTrue(showBattery(90, charging = false, BatteryShow.ALWAYS))
        assertTrue(showBattery(29, charging = false, BatteryShow.LOW))
        assertFalse(showBattery(30, charging = false, BatteryShow.LOW))
        assertTrue(showBattery(80, charging = true, BatteryShow.CHARGING))
        assertFalse(showBattery(10, charging = false, BatteryShow.CHARGING))
        assertEquals(BatteryShow.LOW, BatteryShow.parse(null))
        assertEquals(BatteryShow.CHARGING, BatteryShow.parse("charging"))
    }

    @Test
    fun `la alarma solo cuenta en las próximas 12 horas`() {
        val now = 100 * hour
        assertEquals(now + 7 * hour, alarmToShow(now + 7 * hour, now))
        assertEquals(now + 12 * hour, alarmToShow(now + 12 * hour, now))
        assertNull(alarmToShow(now + 12 * hour + 1, now))
        assertNull(alarmToShow(now - 1, now))
        assertNull(alarmToShow(null, now))
    }

    @Test
    fun `el próximo evento con hora, sin los de todo el día ni los rechazados`() {
        val now = 100 * hour
        fun event(id: Long, begin: Long, allDay: Boolean = false, declined: Boolean = false) =
            CalendarEvent(id, "E$id", begin, begin + hour, allDay, declined)
        val events =
            listOf(
                event(1, now - hour),
                event(2, now + 5 * hour),
                event(3, now + 2 * hour, allDay = true),
                event(4, now + hour, declined = true),
                event(5, now + 3 * hour),
                event(6, now + 13 * hour)
            )
        assertEquals(5L, nextEvent(events, now)?.id)
        assertNull(nextEvent(listOf(event(6, now + 13 * hour)), now))
        // Uno que empieza justo ahora aún es el próximo.
        assertEquals(7L, nextEvent(listOf(event(7, now)), now)?.id)
    }

    @Test
    fun `menos de un minuto de uso no se enseña`() {
        assertNull(screenTimeToShow(59_999))
        assertNull(screenTimeToShow(null))
        assertEquals(60_000L, screenTimeToShow(60_000))
    }
}
