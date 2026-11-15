package dev.jesusmolano.alisio.core.search

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FrecencyTest {
    private val now = 1_800_000_000_000L
    private val hour = DAY_MILLIS / 24

    @Test
    fun `una apertura de ahora vale uno y se reduce a la mitad cada siete dias`() {
        assertEquals(1.0, frecency(listOf(now), now), 1e-9)
        assertEquals(0.5, frecency(listOf(now - 7 * DAY_MILLIS), now), 1e-9)
        assertEquals(0.25, frecency(listOf(now - 14 * DAY_MILLIS), now), 1e-9)
    }

    @Test
    fun `las aperturas se suman`() {
        assertEquals(1.75, frecency(listOf(now, now - 7 * DAY_MILLIS, now - 14 * DAY_MILLIS), now), 1e-9)
    }

    @Test
    fun `mas de noventa dias o en el futuro no cuenta`() {
        assertEquals(0.0, frecency(listOf(now - 91 * DAY_MILLIS, now + hour), now), 1e-9)
    }

    @Test
    fun `tres aperturas a esta hora dan el extra`() {
        val sameHour = listOf(now - DAY_MILLIS, now - 2 * DAY_MILLIS + hour / 2, now - 3 * DAY_MILLIS - hour / 2)
        assertEquals(0.3, hourBonus(sameHour, now, ZoneOffset.UTC), 1e-9)
        assertEquals(0.0, hourBonus(sameHour.take(2), now, ZoneOffset.UTC), 1e-9)
    }

    @Test
    fun `otra hora o mas de catorce dias no dan extra`() {
        val otherHour = List(5) { now - (it + 1) * DAY_MILLIS + 3 * hour }
        assertEquals(0.0, hourBonus(otherHour, now, ZoneOffset.UTC), 1e-9)
        val old = List(5) { now - (15 + it) * DAY_MILLIS }
        assertEquals(0.0, hourBonus(old, now, ZoneOffset.UTC), 1e-9)
    }

    @Test
    fun `la franja horaria cruza la medianoche`() {
        // 23:40 UTC; las aperturas son a las 00:20 de días anteriores.
        val lateNight = now - now % DAY_MILLIS + 23 * hour + 40 * 60_000
        val afterMidnight = List(3) { lateNight - (it + 1) * DAY_MILLIS + 40 * 60_000 }
        assertEquals(0.3, hourBonus(afterMidnight, lateNight, ZoneOffset.UTC), 1e-9)
    }

    @Test
    fun `la puntuacion combina calidad frecencia y hora`() {
        assertEquals(0.9, score(0.9, 0.0, 0.0), 1e-9)
        assertEquals(0.9 * (1 + Math.log(3.0) + 0.3), score(0.9, 2.0, 0.3), 1e-9)
    }
}
