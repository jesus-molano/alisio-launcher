package dev.jesusmolano.alisio.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTest {
    private val spotify = "com.spotify.music"
    private val ytMusic = "com.google.android.apps.youtube.music"
    private val other = "com.example.radio"

    @Test
    fun `la elegida gana a la ultima que sono`() {
        assertEquals(ytMusic, musicAppFor(ytMusic, spotify, setOf(spotify, ytMusic)))
    }

    @Test
    fun `sin elegir vale la ultima que sono`() {
        assertEquals(other, musicAppFor(null, other, setOf(spotify, other)))
    }

    @Test
    fun `sin nada se usa una conocida instalada`() {
        assertEquals(ytMusic, musicAppFor(null, null, setOf(ytMusic)))
        assertNull(musicAppFor(null, null, setOf(other)))
    }

    @Test
    fun `una app desinstalada no vale`() {
        assertEquals(spotify, musicAppFor(other, null, setOf(spotify)))
    }

    @Test
    fun `cuenta como musica la que publica navegacion multimedia o es conocida`() {
        assertTrue(countsAsMusic(other, knownBrowser = true))
        assertTrue(countsAsMusic(spotify, knownBrowser = false))
        assertFalse(countsAsMusic("com.whatsapp", knownBrowser = false))
    }

    @Test
    fun `al elegir app las de musica van primero y cada grupo conserva su orden`() {
        val apps = listOf("calc", spotify, "brave", other, ytMusic)
        assertEquals(
            listOf(spotify, other, ytMusic, "calc", "brave"),
            musicAppsFirst(apps, setOf(spotify, ytMusic, other)) { it }
        )
    }

    @Test
    fun `la clave de musica no choca con la de abrir la app`() {
        assertEquals("music:$spotify", musicKey(spotify))
    }
}
