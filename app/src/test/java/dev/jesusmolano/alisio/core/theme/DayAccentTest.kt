package dev.jesusmolano.alisio.core.theme

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayAccentTest {
    private val text = 0xFF0B0C0E.toInt()
    private val teal = 0xFF6FD6C6.toInt()
    private val amber = 0xFFF2B872.toInt()
    private val sun = SunMinutes(sunrise = 8 * 60, sunset = 18 * 60)

    @Test
    fun `de noche, de dia y a medias`() {
        assertEquals(0f, daylight(3 * 60, sun), 0f)
        assertEquals(1f, daylight(13 * 60, sun), 0f)
        assertEquals(0.5f, daylight(8 * 60, sun), 0.01f)
        assertEquals(0.5f, daylight(18 * 60, sun), 0.01f)
        assertEquals(0f, daylight(23 * 60, sun), 0f)
    }

    @Test
    fun `el cambio es suave, sin saltos de un minuto a otro`() {
        val steps = (0 until DAY_MINUTES).map { daylight(it, sun) }
        assertTrue(steps.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < 0.02f })
    }

    @Test
    fun `los extremos son los dos tonos elegidos`() {
        assertEquals(teal, dayAccent(teal, amber, 1f, text))
        assertEquals(amber, dayAccent(teal, amber, 0f, text))
    }

    @Test
    fun `la mezcla siempre se lee`() {
        val navy = 0xFF1A237E.toInt()
        (0..10).forEach { step ->
            assertTrue(isLegibleAccent(dayAccent(teal, navy, step / 10f, text), text))
        }
    }

    @Test
    fun `salida y puesta en Madrid en verano`() {
        val madrid = sunMinutes(LocalDate.of(2026, 6, 21), ZoneId.of("Europe/Madrid"), 40.42, -3.70)
        // El OAN da 6:44 y 21:48 para ese día.
        assertEquals((6 * 60 + 44).toDouble(), madrid.sunrise.toDouble(), 3.0)
        assertEquals((21 * 60 + 48).toDouble(), madrid.sunset.toDouble(), 3.0)
    }

    @Test
    fun `sin ubicacion se usa la zona horaria`() {
        val guess = sunMinutes(LocalDate.of(2026, 3, 20), ZoneId.of("Europe/Madrid"), null, null)
        // Una hora sobre UTC es la longitud 15° E: en el equinoccio, hacia las 6:05 y las 18:15.
        assertTrue(guess.sunrise in 5 * 60 + 45..6 * 60 + 30)
        assertTrue(guess.sunset in 17 * 60 + 45..18 * 60 + 45)
    }

    @Test
    fun `la franja tiene un color por media hora`() {
        val strip = dayStrip(teal, amber, sun, text)
        assertEquals(48, strip.size)
        assertEquals(amber, strip.first())
        assertEquals(teal, strip[26])
    }

    @Test
    fun `dos tonos iguales no se distinguen`() {
        assertFalse(accentsDiffer(teal, teal))
        assertTrue(accentsDiffer(teal, amber))
    }
}
