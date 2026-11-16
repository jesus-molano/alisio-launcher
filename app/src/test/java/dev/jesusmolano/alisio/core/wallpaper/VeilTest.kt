package dev.jesusmolano.alisio.core.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VeilTest {
    private val tolerance = 0.0001f

    @Test
    fun `con fondo oscuro el velo medio usa la base`() {
        val stops = veilStops(dim = 1f, lightWallpaper = false)
        assertEquals(0.38f, stops.top, tolerance)
        assertEquals(0.30f, stops.middle, tolerance)
        assertEquals(0.82f, stops.bottom, tolerance)
    }

    @Test
    fun `abajo nunca baja del minimo que deja leer el dock`() {
        assertEquals(0.66f, veilStops(dim = 0.45f, lightWallpaper = false).bottom, tolerance)
    }

    @Test
    fun `ninguna opacidad pasa de uno`() {
        val stops = veilStops(dim = 4f, lightWallpaper = true)
        assertEquals(1f, stops.top, tolerance)
        assertEquals(1f, stops.middle, tolerance)
        assertEquals(1f, stops.bottom, tolerance)
    }

    @Test
    fun `con fondo claro y poco velo manda el suelo`() {
        val stops = veilStops(dim = 0.45f, lightWallpaper = true)
        assertEquals(0.50f, stops.top, tolerance)
        assertEquals(0.30f, stops.middle, tolerance)
    }

    @Test
    fun `con fondo claro las opciones siguen graduando por encima del suelo`() {
        val medium = veilStops(dim = 1f, lightWallpaper = true)
        val strong = veilStops(dim = 1.6f, lightWallpaper = true)
        assertEquals(0.50f, medium.top, tolerance)
        assertTrue(strong.top > medium.top)
        assertTrue(strong.middle > medium.middle)
    }

    @Test
    fun `sin fondo claro poco velo no tiene suelo arriba`() {
        assertEquals(0.171f, veilStops(dim = 0.45f, lightWallpaper = false).top, tolerance)
    }

    @Test
    fun `la miniatura no pone nada arriba, donde ya esta el velo del Inicio`() {
        val stops = previewVeilStops(dim = 1f, lightWallpaper = false)
        assertEquals(0f, stops.top, tolerance)
        // En medio el velo real es mas fino que arriba: no se puede aclarar, se deja como esta.
        assertEquals(0f, stops.middle, tolerance)
    }

    @Test
    fun `la miniatura sumada al velo de debajo oscurece abajo como el Inicio`() {
        val full = veilStops(dim = 1f, lightWallpaper = false)
        val extra = previewVeilStops(dim = 1f, lightWallpaper = false)
        val total = 1f - (1f - full.top) * (1f - extra.bottom)
        assertEquals(full.bottom, total, tolerance)
    }

    @Test
    fun `con el velo de debajo opaco la miniatura no pone nada`() {
        assertEquals(0f, previewVeilStops(dim = 4f, lightWallpaper = true).bottom, tolerance)
    }
}
