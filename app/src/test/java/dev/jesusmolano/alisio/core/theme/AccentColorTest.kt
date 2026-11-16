package dev.jesusmolano.alisio.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorTest {
    // El fondo de Alisio, que es el texto que va sobre el acento.
    private val background = 0xFF0B0C0E.toInt()

    @Test
    fun `hsv y argb van y vuelven`() {
        val colors = listOf(0xFF6FD6C6, 0xFF8FB4FF, 0xFFF2B872, 0xFFC9A7FF, 0xFFFF0000, 0xFF808080).map { it.toInt() }
        colors.forEach { assertEquals(hexOf(it), hexOf(hsvOf(it).toArgb())) }
    }

    @Test
    fun `los primarios caen en su tono`() {
        assertEquals(0f, hsvOf(0xFFFF0000.toInt()).hue, 0.01f)
        assertEquals(120f, hsvOf(0xFF00FF00.toInt()).hue, 0.01f)
        assertEquals(240f, hsvOf(0xFF0000FF.toInt()).hue, 0.01f)
        assertEquals(0f, hsvOf(0xFF808080.toInt()).saturation, 0.001f)
    }

    @Test
    fun `contraste de referencia de wcag`() {
        assertEquals(21.0, contrastRatio(0xFF000000.toInt(), -1), 0.01)
        assertEquals(1.0, contrastRatio(background, background), 0.0001)
    }

    @Test
    fun `los cuatro acentos de la paleta se leen con el fondo encima`() {
        listOf(0xFF6FD6C6, 0xFF8FB4FF, 0xFFF2B872, 0xFFC9A7FF).forEach {
            assertTrue(hexOf(it.toInt()), isLegibleAccent(it.toInt(), background))
        }
    }

    @Test
    fun `un azul oscuro no se lee y su version legible conserva el tono`() {
        val navy = 0xFF1A237E.toInt()
        assertFalse(isLegibleAccent(navy, background))
        val legible = legibleAccent(navy, background)
        assertTrue(isLegibleAccent(legible, background))
        assertEquals(hsvOf(navy).hue, hsvOf(legible).hue, 2f)
    }

    @Test
    fun `un color que ya se lee no cambia`() {
        val teal = 0xFF6FD6C6.toInt()
        assertEquals(teal, legibleAccent(teal, background))
    }

    @Test
    fun `las propuestas son legibles, sin grises ni repetidas`() {
        val wallpaper = listOf(0xFF102A43, 0xFF808080, 0xFF12304A, 0xFF7A2E10).map { it.toInt() }
        val proposals = accentProposals(wallpaper, background)
        assertEquals(2, proposals.size)
        proposals.forEach { assertTrue(isLegibleAccent(it, background)) }
    }

    @Test
    fun `como mucho tres propuestas`() {
        val wallpaper = listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00).map { it.toInt() }
        assertEquals(3, accentProposals(wallpaper, background).size)
    }

    @Test
    fun `el color se escribe en hexadecimal`() {
        assertEquals("#6FD6C6", hexOf(0xFF6FD6C6.toInt()))
    }
}
