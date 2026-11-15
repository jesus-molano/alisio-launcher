package dev.jesusmolano.alisio.core.extras

import dev.jesusmolano.alisio.core.extras.ClipContent.Address
import dev.jesusmolano.alisio.core.extras.ClipContent.Phone
import dev.jesusmolano.alisio.core.extras.ClipContent.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipContentTest {
    @Test
    fun `enlaces con esquema`() {
        assertEquals(Url("https://example.com/a?b=1"), detectClip("https://example.com/a?b=1"))
        assertEquals(Url("http://example.com"), detectClip("  http://example.com \n"))
    }

    @Test
    fun `enlace dentro de una frase, sin la puntuacion del final`() {
        assertEquals(Url("https://example.com/articulo"), detectClip("Mira esto: https://example.com/articulo."))
        assertEquals(Url("https://example.com/a"), detectClip("Título\nhttps://example.com/a\nEnviado desde X"))
    }

    @Test
    fun `dominios sin esquema`() {
        assertEquals(Url("https://www.example.xyz/ruta"), detectClip("www.example.xyz/ruta"))
        assertEquals(Url("https://github.com/JetBrains/kotlin"), detectClip("github.com/JetBrains/kotlin"))
        assertEquals(Url("https://jesusmolano.dev"), detectClip("jesusmolano.dev"))
    }

    @Test
    fun `lo que tiene un punto pero no es un enlace`() {
        assertNull(detectClip("informe.pdf"))
        assertNull(detectClip("v1.2"))
        assertNull(detectClip("hola.adios"))
        assertNull(detectClip("https://"))
        // Un dominio en medio de una frase no se abre: podría ser cualquier cosa.
        assertNull(detectClip("lo vi en github.com ayer"))
    }

    @Test
    fun `telefonos`() {
        assertEquals(Phone("612345678", "612 34 56 78"), detectClip("612 34 56 78"))
        assertEquals(Phone("+34612345678", "+34 612-345-678"), detectClip("+34 612-345-678"))
        assertEquals(Phone("5551234567", "(555) 123-4567"), detectClip("(555) 123-4567"))
    }

    @Test
    fun `numeros que no son telefonos`() {
        assertNull(detectClip("28013"))
        assertNull(detectClip("2026-11-15"))
        assertNull(detectClip("1234 5678 9012 3456"))
        assertNull(detectClip("12,50"))
        assertNull(detectClip("612 34 56 78 llámame"))
    }

    @Test
    fun `direcciones postales`() {
        assertEquals(Address("Calle Mayor 5, Madrid"), detectClip("Calle Mayor 5, Madrid"))
        assertEquals(Address("C/ Alcalá, 12, 3ºB"), detectClip("C/ Alcalá, 12, 3ºB"))
        assertEquals(
            Address("Avda. de la Constitución 3, 41001 Sevilla"),
            detectClip("Avda. de la Constitución 3\n41001 Sevilla")
        )
        assertEquals(Address("221B Baker Street, London"), detectClip("221B Baker Street, London"))
        assertEquals(Address("Edificio Sol, 28013 Madrid"), detectClip("Edificio Sol, 28013 Madrid"))
    }

    @Test
    fun `frases que no son direcciones`() {
        assertNull(detectClip("Voy de camino, llego en 5 minutos"))
        assertNull(detectClip("Nos vemos en la plaza a las 8"))
        assertNull(detectClip("Calle Mayor"))
        assertNull(detectClip("Plazas libres para el 15"))
        assertNull(detectClip("Placas solares 3 kW"))
        assertNull(detectClip("Caminos de hierro 2"))
        assertNull(detectClip("Tengo 28013 motivos"))
        assertNull(detectClip("hola"))
        assertNull(detectClip(""))
    }

    @Test
    fun `un texto largo no se mira`() {
        assertNull(detectClip("https://example.com " + "a".repeat(2_000)))
        assertNull(detectClip("Calle Mayor 5, " + "muy lejos ".repeat(20)))
    }
}
