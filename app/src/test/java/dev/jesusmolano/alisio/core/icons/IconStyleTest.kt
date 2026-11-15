package dev.jesusmolano.alisio.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconStyleTest {
    @Test
    fun `cada estilo se recupera de su id`() {
        val styles = listOf(IconStyle.Color, IconStyle.Monochrome, IconStyle.System, IconStyle.Pack("com.pack.vera"))
        styles.forEach { assertEquals(it, IconStyle.parse(it.id)) }
    }

    @Test
    fun `lo que no se reconoce es el estilo a color`() {
        assertEquals(IconStyle.Color, IconStyle.parse(null))
        assertEquals(IconStyle.Color, IconStyle.parse(""))
        assertEquals(IconStyle.Color, IconStyle.parse("otro"))
        assertEquals(IconStyle.Color, IconStyle.parse("pack:"))
    }

    @Test
    fun `extrae el componente de un appfilter`() {
        assertEquals("com.app/com.app.Main", appFilterComponent("ComponentInfo{com.app/com.app.Main}"))
        assertNull(appFilterComponent("ComponentInfo{com.app/com.app.Main"))
        assertNull(appFilterComponent(":CALENDAR"))
        assertNull(appFilterComponent("ComponentInfo{sin-barra}"))
    }
}
