package dev.jesusmolano.alisio.core.now

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTest {
    private fun signals(
        key: String = "k",
        ongoing: Boolean = true,
        promoted: Boolean = false,
        category: String? = "navigation",
        progress: Boolean = false,
        chronometer: Boolean = false,
        media: Boolean = false,
        at: Long = 0
    ) = LiveSignals(key, "com.app", ongoing, promoted, category, progress, chronometer, media, at)

    @Test
    fun `la marca de Live Update vale siempre`() {
        assertTrue(isLive(signals(promoted = true, ongoing = false, category = null)))
    }

    @Test
    fun `sin la marca hace falta categoria, permanencia y algo que avance`() {
        assertTrue(isLive(signals(progress = true)))
        assertTrue(isLive(signals(category = "stopwatch", chronometer = true)))
        assertFalse(isLive(signals()))
        assertFalse(isLive(signals(category = "msg", progress = true)))
        assertFalse(isLive(signals(ongoing = false, progress = true)))
    }

    @Test
    fun `un reproductor no es algo en curso`() {
        assertFalse(isLive(signals(promoted = true, media = true)))
    }

    @Test
    fun `gana lo ultimo publicado`() {
        val items =
            listOf(signals("a", progress = true, at = 1), signals("b", progress = true, at = 5), signals("c", at = 9))
        assertEquals("b", chooseLive(items) { it }?.key)
        assertNull(chooseLive(listOf(signals())) { it })
    }
}
