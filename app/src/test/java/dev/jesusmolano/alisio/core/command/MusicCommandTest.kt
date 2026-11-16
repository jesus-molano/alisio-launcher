package dev.jesusmolano.alisio.core.command

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCommandTest {
    @Test
    fun `musica y music en cualquier forma piden la musica`() {
        listOf("música", "musica", "Música ", "MUSIC", "pon música", "play  music").forEach {
            assertTrue(it, isMusicCommand(it))
        }
    }

    @Test
    fun `otras frases siguen siendo una busqueda`() {
        listOf("music player", "música clásica", "mus", "", "samsung music").forEach {
            assertFalse(it, isMusicCommand(it))
        }
    }
}
