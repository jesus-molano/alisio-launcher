package dev.jesusmolano.alisio.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomIconTest {
    @Test
    fun `cada icono propio se recupera de su id`() {
        listOf(CustomIcon.Pack("com.pack.vera", "google_maps"), CustomIcon.Image("a1b2.png"))
            .forEach { assertEquals(it, CustomIcon.parse(it.id)) }
    }

    @Test
    fun `lo que no se reconoce vuelve al icono de la app`() {
        assertNull(CustomIcon.parse(null))
        assertNull(CustomIcon.parse(""))
        assertNull(CustomIcon.parse("pack:"))
        assertNull(CustomIcon.parse("pack:com.pack"))
        assertNull(CustomIcon.parse("pack:com.pack/"))
        assertNull(CustomIcon.parse("otro:cosa"))
    }

    @Test
    fun `una imagen no puede salir de su carpeta`() {
        assertNull(CustomIcon.parse("image:../shared_prefs/settings.xml"))
        assertNull(CustomIcon.parse("image:a/b.png"))
        assertNull(CustomIcon.parse("image:.oculto"))
        assertNull(CustomIcon.parse("image:"))
    }

    @Test
    fun `el nombre propio se limpia y el original no se guarda`() {
        assertEquals("Chat", customLabel("  Chat ", "WhatsApp"))
        assertNull(customLabel("WhatsApp", "WhatsApp"))
        assertNull(customLabel("   ", "WhatsApp"))
    }

    @Test
    fun `el recorte es el cuadrado central`() {
        assertEquals(CenterSquare(100, 0, 300), centerSquare(500, 300))
        assertEquals(CenterSquare(0, 50, 200), centerSquare(200, 300))
        assertEquals(CenterSquare(0, 0, 64), centerSquare(64, 64))
    }

    @Test
    fun `el nombre de un dibujo se lee con espacios`() {
        assertEquals("google maps", drawableTitle("google_maps"))
        assertEquals("k 9 mail", drawableTitle("k-9_mail"))
    }
}
