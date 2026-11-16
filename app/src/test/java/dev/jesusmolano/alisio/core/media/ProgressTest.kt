package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {
    @Test
    fun `sonando, la posicion avanza sola`() {
        assertEquals(0.5f, trackProgress(60_000, 1_000, 1f, 180_000, playing = true, now = 31_000)!!, 0.001f)
    }

    @Test
    fun `en pausa se queda donde estaba`() {
        assertEquals(1f / 3, trackProgress(60_000, 1_000, 1f, 180_000, playing = false, now = 99_000)!!, 0.001f)
    }

    @Test
    fun `sin duracion no hay progreso`() {
        assertNull(trackProgress(10, 0, 1f, null, playing = true, now = 5))
        assertNull(trackProgress(10, 0, 1f, 0, playing = true, now = 5))
    }

    @Test
    fun `nunca pasa del final`() {
        assertEquals(1f, trackProgress(170_000, 0, 1f, 180_000, playing = true, now = 60_000)!!, 0f)
    }

    @Test
    fun `lo que queda de un temporizador`() {
        assertEquals(0.25f, timerRemaining(endsAt = 15 * 60_000L, now = 0)!!, 0.001f)
        assertEquals(1f, timerRemaining(endsAt = 60 * 60_000L, now = 0)!!, 0f)
        assertNull(timerRemaining(endsAt = 10, now = 10))
    }
}
