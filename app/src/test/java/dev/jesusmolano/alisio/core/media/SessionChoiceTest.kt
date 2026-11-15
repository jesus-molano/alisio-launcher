package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionChoiceTest {
    private val playing = SessionFacts(playing = true, hasTitle = true)
    private val paused = SessionFacts(playing = false, hasTitle = true)
    private val empty = SessionFacts(playing = false, hasTitle = false)

    private fun choose(sessions: Map<String, SessionFacts>, last: String? = null): String? =
        chooseSession(sessions.keys.toList(), last) { sessions.getValue(it) }

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
}
