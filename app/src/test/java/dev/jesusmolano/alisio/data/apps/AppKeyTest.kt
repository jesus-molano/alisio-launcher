package dev.jesusmolano.alisio.data.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyTest {
    @Test
    fun `tiene el formato paquete, actividad y serie de usuario`() {
        assertEquals("com.x/com.x.Main#0", AppKey("com.x", "com.x.Main", 0).toString())
    }

    @Test
    fun `parse deshace toString`() {
        val key = AppKey("com.whatsapp", "com.whatsapp.Main", 10)
        assertEquals(key, AppKey.parse(key.toString()))
    }

    @Test
    fun `parse rechaza claves mal formadas`() {
        assertNull(AppKey.parse("com.x"))
        assertNull(AppKey.parse("com.x/Main"))
        assertNull(AppKey.parse("com.x/Main#abc"))
        assertNull(AppKey.parse("/Main#0"))
        assertNull(AppKey.parse("com.x/#0"))
    }
}
