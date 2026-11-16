package dev.jesusmolano.alisio.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockFormatTest {
    @Test
    fun `del sistema sigue al sistema y forzado manda`() {
        assertEquals("H:mm", clockPattern(ClockFormat.SYSTEM, system24 = true))
        assertEquals("h:mm", clockPattern(ClockFormat.SYSTEM, system24 = false))
        assertEquals("h:mm", clockPattern(ClockFormat.H12, system24 = true))
        assertEquals("H:mm", clockPattern(ClockFormat.H24, system24 = false))
    }

    @Test
    fun `lo guardado se lee con su valor por defecto`() {
        assertEquals(ClockFormat.SYSTEM, ClockFormat.parse(null))
        assertEquals(ClockFormat.H24, ClockFormat.parse("24"))
        assertEquals(ClockSize.LARGE, ClockSize.parse("otro"))
        assertEquals(ClockSize.SMALL, ClockSize.parse("small"))
    }
}
