package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSeenTest {
    private val paused = PlayerSeen("musica", playing = false)
    private val playing = PlayerSeen("musica", playing = true)

    @Test
    fun `a la vista sigue a la vista`() {
        assertNull(hiddenAfter(null, playing))
        assertNull(hiddenAfter(null, null))
    }

    @Test
    fun `oculto en pausa sigue oculto mientras no suene nada`() {
        assertEquals(paused, hiddenAfter(paused, paused))
    }

    @Test
    fun `vuelve cuando la misma app pasa a reproducir`() {
        assertNull(hiddenAfter(paused, playing))
    }

    @Test
    fun `oculto mientras sonaba no vuelve con la pista siguiente`() {
        assertEquals(playing, hiddenAfter(playing, playing))
    }

    @Test
    fun `oculto mientras sonaba vuelve tras una pausa y otra reproduccion`() {
        val afterPause = hiddenAfter(playing, paused)
        assertEquals(paused, afterPause)
        assertNull(hiddenAfter(afterPause, playing))
    }

    @Test
    fun `vuelve cuando suena otra app`() {
        assertNull(hiddenAfter(playing, PlayerSeen("podcasts", playing = true)))
    }

    @Test
    fun `otra app en pausa no lo devuelve`() {
        val other = PlayerSeen("podcasts", playing = false)
        assertEquals(other, hiddenAfter(paused, other))
    }

    @Test
    fun `sin sesion sigue oculto y la siguiente que suene lo devuelve`() {
        val gone = hiddenAfter(playing, null)
        assertEquals(paused, gone)
        assertNull(hiddenAfter(gone, playing))
    }
}
