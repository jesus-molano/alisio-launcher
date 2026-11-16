package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionChoiceTest {
    private val playing = SessionFacts(playing = true, hasTitle = true)
    private val paused = SessionFacts(playing = false, hasTitle = true)
    private val empty = SessionFacts(playing = false, hasTitle = false)

    private fun choose(sessions: Map<String, SessionFacts>, last: String? = null, preferred: String? = null): String? =
        chooseSession(sessions.keys.toList(), last, preferred = { it == preferred }) { sessions.getValue(it) }

    @Test
    fun `la app de musica elegida gana a otro audio que suena a la vez`() {
        val sessions = mapOf("whatsapp" to playing, "spotify" to playing)
        assertEquals(
            "spotify",
            chooseSession(sessions.keys.toList(), null, preferred = { it == "spotify" }) { sessions.getValue(it) }
        )
    }

    @Test
    fun `la elegida en pausa no tapa a la que suena`() {
        val sessions = mapOf("spotify" to paused, "video" to playing)
        assertEquals(
            "video",
            chooseSession(sessions.keys.toList(), "spotify", preferred = { it == "spotify" }) { sessions.getValue(it) }
        )
    }

    @Test
    fun `sin sesiones no hay reproductor`() {
        assertNull(choose(emptyMap()))
    }

    @Test
    fun `gana la que suena aunque no sea la primera ni la ultima`() {
        assertEquals("musica", choose(mapOf("video" to paused, "musica" to playing), last = "video"))
    }

    @Test
    fun `si ninguna suena se queda la ultima que se enseno`() {
        assertEquals("musica", choose(mapOf("video" to paused, "musica" to paused), last = "musica"))
    }

    @Test
    fun `si la ultima ya no existe se toma la primera`() {
        assertEquals("video", choose(mapOf("video" to paused, "musica" to paused), last = "podcast"))
    }

    @Test
    fun `una sesion vacia no cuenta`() {
        assertNull(choose(mapOf("vacia" to empty), last = "vacia"))
        assertEquals("musica", choose(mapOf("vacia" to empty, "musica" to paused)))
    }

    @Test
    fun `una sesion que suena cuenta aunque no tenga titulo`() {
        assertEquals("radio", choose(mapOf("radio" to SessionFacts(playing = true, hasTitle = false))))
    }

    @Test
    fun `una app que arranca sin titulo no sale hasta mandarlo o pasado el margen`() {
        val starting = SessionFacts(playing = true, hasTitle = false, playingFor = UNTITLED_GRACE_MILLIS - 1)
        assertNull(choose(mapOf("spotify" to starting)))
        // Mientras arranca se queda a la vista la que había, aunque esté en pausa.
        assertEquals("musica", choose(mapOf("spotify" to starting, "musica" to paused), last = "musica"))
        val titled = SessionFacts(playing = true, hasTitle = true, playingFor = 0)
        assertEquals("spotify", choose(mapOf("spotify" to titled)))
        val long = SessionFacts(playing = true, hasTitle = false, playingFor = UNTITLED_GRACE_MILLIS)
        assertEquals("spotify", choose(mapOf("spotify" to long)))
    }

    @Test
    fun `la que ya se ensena no se va por quedarse sin titulo al cambiar de cancion`() {
        val skipping = SessionFacts(playing = true, hasTitle = false, playingFor = 1, shown = true)
        assertEquals("spotify", choose(mapOf("spotify" to skipping, "musica" to paused), last = "spotify"))
    }

    @Test
    fun `en pausa y sin titulo no sale aunque llevara mucho sonando`() {
        assertNull(choose(mapOf("vacia" to SessionFacts(playing = false, hasTitle = false, playingFor = 60_000))))
    }

    @Test
    fun `la elegida que arranca sin titulo deja la placa al otro audio hasta mandarlo`() {
        val starting = SessionFacts(playing = true, hasTitle = false, playingFor = 100)
        val other = SessionFacts(playing = true, hasTitle = true)
        assertEquals("video", choose(mapOf("spotify" to starting, "video" to other), preferred = "spotify"))
        val titled = SessionFacts(playing = true, hasTitle = true, playingFor = 100)
        assertEquals("spotify", choose(mapOf("spotify" to titled, "video" to other), preferred = "spotify"))
    }

    @Test
    fun `desde cuando suena`() {
        assertNull(playingSinceAfter(previous = 5, playing = false, known = false, now = 10))
        assertEquals(5L, playingSinceAfter(previous = 5, playing = true, known = false, now = 10))
        assertEquals(KNOWN_PLAYING, playingSinceAfter(previous = null, playing = true, known = true, now = 10))
        assertEquals(10L, playingSinceAfter(previous = null, playing = true, known = false, now = 10))
    }

    @Test
    fun `se vuelve a mirar cuando acaba el margen mas corto`() {
        assertNull(untitledRecheckDelay(emptyList()))
        assertEquals(1_500L, untitledRecheckDelay(listOf(500, 200)))
        assertNull(untitledRecheckDelay(listOf(UNTITLED_GRACE_MILLIS)))
    }
}
