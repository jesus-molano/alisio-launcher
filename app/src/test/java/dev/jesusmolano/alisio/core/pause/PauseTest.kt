package dev.jesusmolano.alisio.core.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseTest {
    private val paused = setOf("com.twitter.android", "com.google.android.youtube")

    @Test
    fun `una app elegida lleva pausa con la duracion de los ajustes`() {
        assertEquals(5, pauseBeforeOpening("com.twitter.android", paused, 5))
        assertEquals(10, pauseBeforeOpening("com.google.android.youtube", paused, 10))
    }

    @Test
    fun `una app no elegida se abre sin pausa`() {
        assertNull(pauseBeforeOpening("com.spotify.music", paused, 5))
        assertNull(pauseBeforeOpening("com.twitter.android", emptySet(), 5))
    }

    @Test
    fun `el paquete tiene que coincidir entero`() {
        assertNull(pauseBeforeOpening("com.twitter.android.lite", paused, 5))
        assertNull(pauseBeforeOpening("com.twitter", paused, 5))
    }

    @Test
    fun `sin duracion no hay pausa`() {
        assertNull(pauseBeforeOpening("com.twitter.android", paused, 0))
        assertNull(pauseBeforeOpening("com.twitter.android", paused, -3))
    }

    @Test
    fun `la cuenta atras redondea hacia arriba y acaba en cero`() {
        assertEquals(5, pauseSecondsLeft(5, 0))
        assertEquals(5, pauseSecondsLeft(5, 1))
        assertEquals(5, pauseSecondsLeft(5, 999))
        assertEquals(4, pauseSecondsLeft(5, 1000))
        assertEquals(1, pauseSecondsLeft(5, 4999))
        assertEquals(0, pauseSecondsLeft(5, 5000))
        assertEquals(0, pauseSecondsLeft(5, 60_000))
    }

    @Test
    fun `un tiempo transcurrido negativo no alarga la cuenta atras`() {
        assertEquals(3, pauseSecondsLeft(3, -500))
    }

    @Test
    fun `una duracion guardada que ya no se ofrece pasa a la mas cercana`() {
        assertEquals(DEFAULT_PAUSE_SECONDS, normalizePauseSeconds(null))
        assertEquals(3, normalizePauseSeconds(3))
        assertEquals(3, normalizePauseSeconds(1))
        assertEquals(5, normalizePauseSeconds(6))
        assertEquals(10, normalizePauseSeconds(8))
        assertEquals(10, normalizePauseSeconds(600))
        // En empate, la más corta.
        assertEquals(3, normalizePauseSeconds(4))
    }

    @Test
    fun `las apps con pausa salen las primeras sin perder su orden`() {
        val apps = listOf("a", "com.twitter.android", "b", "com.google.android.youtube", "c")
        assertEquals(
            listOf("com.twitter.android", "com.google.android.youtube", "a", "b", "c"),
            pausedFirst(apps, paused) { it }
        )
        assertEquals(apps, pausedFirst(apps, emptySet()) { it })
    }
}
