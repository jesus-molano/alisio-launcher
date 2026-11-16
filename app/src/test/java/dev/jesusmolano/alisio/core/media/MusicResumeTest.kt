package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicResumeTest {
    private val spotify = "com.spotify.music"
    private val whatsapp = "com.whatsapp"
    private val youtube = "com.google.android.youtube"

    @Test
    fun `con la sesion en pausa se prueba primero la sesion`() {
        assertEquals(
            listOf(ResumeStep.SESSION, ResumeStep.MEDIA_KEY, ResumeStep.MEDIA_BUTTON),
            resumeSteps(spotify, hasSession = true, lastStarted = spotify, watching = true, audioActive = false)
        )
    }

    @Test
    fun `la tecla solo si la ultima que sono es esa app`() {
        assertEquals(
            listOf(ResumeStep.MEDIA_KEY, ResumeStep.MEDIA_BUTTON),
            resumeSteps(spotify, hasSession = false, lastStarted = spotify, watching = true, audioActive = false)
        )
    }

    @Test
    fun `si despues sono otra app no se manda la tecla`() {
        assertEquals(
            listOf(ResumeStep.MEDIA_BUTTON),
            resumeSteps(spotify, hasSession = false, lastStarted = whatsapp, watching = true, audioActive = false)
        )
    }

    @Test
    fun `sin escuchar las sesiones no se manda la tecla`() {
        assertEquals(
            listOf(ResumeStep.MEDIA_BUTTON),
            resumeSteps(spotify, hasSession = false, lastStarted = spotify, watching = false, audioActive = false)
        )
    }

    @Test
    fun `sin escuchar y con otro audio sonando se abre la app directamente`() {
        assertTrue(
            resumeSteps(spotify, hasSession = false, lastStarted = null, watching = false, audioActive = true).isEmpty()
        )
    }

    @Test
    fun `escuchando el audio que ya suena no impide reanudar`() {
        assertEquals(
            listOf(ResumeStep.MEDIA_BUTTON),
            resumeSteps(spotify, hasSession = false, lastStarted = null, watching = true, audioActive = true)
        )
    }

    @Test
    fun `una app que empieza a sonar pasa a ser la ultima`() {
        val change = playingChange(emptySet(), setOf(spotify), wasWatching = true, watching = true, lastStarted = null)
        assertEquals(setOf(spotify), change.started)
        assertTrue(change.played)
        assertEquals(spotify, change.lastStarted)
    }

    @Test
    fun `un audio de otra app despues de la musica pasa a ser el ultimo`() {
        val change =
            playingChange(emptySet(), setOf(whatsapp), wasWatching = true, watching = true, lastStarted = spotify)
        assertEquals(whatsapp, change.lastStarted)
    }

    @Test
    fun `al parar se apunta la hora y se recuerda la ultima`() {
        val change =
            playingChange(setOf(spotify), emptySet(), wasWatching = true, watching = true, lastStarted = spotify)
        assertTrue(change.started.isEmpty())
        assertTrue(change.played)
        assertEquals(spotify, change.lastStarted)
    }

    @Test
    fun `si empiezan dos a la vez no se sabe cual es la ultima`() {
        val change =
            playingChange(
                emptySet(),
                setOf(spotify, youtube),
                wasWatching = true,
                watching = true,
                lastStarted = spotify
            )
        assertNull(change.lastStarted)
    }

    @Test
    fun `al empezar a escuchar lo que ya sonaba no cuenta como inicio`() {
        val change = playingChange(emptySet(), setOf(spotify), wasWatching = false, watching = true, lastStarted = null)
        assertTrue(change.started.isEmpty())
        assertTrue(change.played)
        assertEquals(spotify, change.lastStarted)
    }

    @Test
    fun `al empezar a escuchar sin nada sonando no se apunta la hora`() {
        val change = playingChange(emptySet(), emptySet(), wasWatching = false, watching = true, lastStarted = null)
        assertFalse(change.played)
    }

    @Test
    fun `al dejar de escuchar no se apunta nada y se olvida la ultima`() {
        val change =
            playingChange(setOf(spotify), emptySet(), wasWatching = true, watching = false, lastStarted = spotify)
        assertEquals(PlayingChange(emptySet(), played = false, lastStarted = null), change)
    }
}
