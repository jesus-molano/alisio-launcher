package dev.jesusmolano.alisio.core.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {
    @Test
    fun `solo imagenes`() {
        assertTrue(isWallpaperImage("mar.JPG", null))
        assertTrue(isWallpaperImage("sin-extension", "image/png"))
        assertFalse(isWallpaperImage("notas.txt", "text/plain"))
        assertFalse(isWallpaperImage(null, null))
    }

    @Test
    fun `una vez al dia y en orden`() {
        assertEquals(0, nextWallpaper(today = 100, lastDay = null, lastIndex = 0, count = 3))
        assertNull(nextWallpaper(today = 100, lastDay = 100, lastIndex = 0, count = 3))
        assertEquals(1, nextWallpaper(today = 101, lastDay = 100, lastIndex = 0, count = 3))
        assertEquals(0, nextWallpaper(today = 105, lastDay = 101, lastIndex = 2, count = 3))
    }

    @Test
    fun `si la carpeta encoge se vuelve a empezar`() {
        assertEquals(0, nextWallpaper(today = 2, lastDay = 1, lastIndex = 9, count = 2))
        assertNull(nextWallpaper(today = 2, lastDay = 1, lastIndex = 0, count = 0))
    }

    @Test
    fun `por nombre sin mayusculas`() {
        assertEquals(listOf("a.jpg", "B.jpg", "c.png"), wallpaperOrder(listOf("c.png", "B.jpg", "a.jpg")) { it })
    }
}
