package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicPieceTest {
    @Test
    fun `con musica sonando se oculta ya`() {
        assertNull(musicPieceDelay(watching = true, playing = true, starting = false, hiddenBefore = false))
    }

    @Test
    fun `al tocarla se oculta aunque aun no suene`() {
        assertNull(musicPieceDelay(watching = true, playing = false, starting = true, hiddenBefore = false))
    }

    @Test
    fun `al abrir el Inicio sin musica sale sin esperar`() {
        assertEquals(0L, musicPieceDelay(watching = true, playing = false, starting = false, hiddenBefore = false))
    }

    @Test
    fun `tras ocultarse solo vuelve despues del margen`() {
        assertEquals(
            MUSIC_PIECE_GRACE_MILLIS,
            musicPieceDelay(watching = true, playing = false, starting = false, hiddenBefore = true)
        )
    }

    @Test
    fun `sin escuchar las sesiones sale siempre, tambien al tocarla`() {
        assertEquals(0L, musicPieceDelay(watching = false, playing = false, starting = true, hiddenBefore = true))
    }
}
