package dev.jesusmolano.alisio.core.glance

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val fiveMinutes = 5 * 60_000L

    private fun at(text: String, zone: ZoneId = madrid): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun assertNear(expected: Long, actual: Long) {
        assertTrue(
            "esperado ${Instant.ofEpochMilli(expected)}, sale ${Instant.ofEpochMilli(actual)}",
            abs(expected - actual) <= fiveMinutes
        )
    }

    @Test
    fun `Madrid en el solsticio de verano`() {
        // Referencia: el Observatorio Astronómico Nacional da 6:44 y 21:48 (hora de verano).
        val (rise, set) = sunTimes(LocalDate.of(2026, 6, 21), 40.4168, -3.7038)!!
        assertNear(at("2026-06-21T06:44"), rise)
        assertNear(at("2026-06-21T21:48"), set)
    }

    @Test
    fun `Madrid en noviembre`() {
        val (rise, set) = sunTimes(LocalDate.of(2026, 11, 16), 40.4168, -3.7038)!!
        assertNear(at("2026-11-16T08:05"), rise)
        assertNear(at("2026-11-16T17:56"), set)
    }

    @Test
    fun `en el otro hemisferio y al otro lado del meridiano`() {
        val sydney = ZoneId.of("Australia/Sydney")
        val (rise, set) = sunTimes(LocalDate.of(2026, 1, 1), -33.8688, 151.2093)!!
        assertNear(at("2026-01-01T05:47", sydney), rise)
        assertNear(at("2026-01-01T20:09", sydney), set)
    }

    @Test
    fun `en la noche polar no sale`() {
        assertNull(sunTimes(LocalDate.of(2026, 12, 21), 78.22, 15.65))
        assertNull(nextSunEvent(at("2026-12-21T12:00", ZoneId.of("Arctic/Longyearbyen")), 78.22, 15.65, madrid))
    }

    @Test
    fun `lo próximo es la puesta de hoy o la salida de mañana`() {
        val afternoon = nextSunEvent(at("2026-11-16T15:00"), 40.4168, -3.7038, madrid)!!
        assertEquals(SunEventKind.SUNSET, afternoon.kind)
        assertNear(at("2026-11-16T17:56"), afternoon.at)

        val night = nextSunEvent(at("2026-11-16T22:00"), 40.4168, -3.7038, madrid)!!
        assertEquals(SunEventKind.SUNRISE, night.kind)
        assertNear(at("2026-11-17T08:06"), night.at)

        val dawn = nextSunEvent(at("2026-11-16T05:00"), 40.4168, -3.7038, madrid)!!
        assertEquals(SunEventKind.SUNRISE, dawn.kind)
    }
}
