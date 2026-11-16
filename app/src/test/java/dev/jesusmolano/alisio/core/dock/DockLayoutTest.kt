package dev.jesusmolano.alisio.core.dock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockLayoutTest {
    @Test
    fun `lo guardado se queda entre 0 y 8, con 6 por defecto`() {
        assertEquals(6, normalizeDockSlots(null))
        assertEquals(0, normalizeDockSlots(0))
        assertEquals(8, normalizeDockSlots(12))
        assertEquals(0, normalizeDockSlots(-3))
    }

    @Test
    fun `en el S26 los iconos miden 52 hasta seis y encogen con siete y ocho`() {
        (1..6).forEach { assertEquals("con $it", 52, dockIconSize(it, 412f)) }
        assertEquals(44, dockIconSize(7, 412f))
        assertEquals(38, dockIconSize(8, 412f))
    }

    @Test
    fun `los iconos caben siempre con su aire y nunca bajan del mínimo`() {
        listOf(360f, 384f, 412f, 426f).forEach { width ->
            (1..MAX_DOCK_SLOTS).forEach { count ->
                val size = dockIconSize(count, width)
                assertTrue(size in DOCK_ICON_MIN_DP..DOCK_ICON_MAX_DP)
                assertEquals(0, size % 2)
                if (size > DOCK_ICON_MIN_DP) assertTrue(count * (size + DOCK_ITEM_SPARE_DP) <= width - 40f)
            }
        }
        assertEquals(DOCK_ICON_MAX_DP, dockIconSize(0, 412f))
    }

    @Test
    fun `sin elegir, los nombres se ven hasta seis huecos`() {
        assertTrue(dockLabelsShown(null, 6))
        assertFalse(dockLabelsShown(null, 7))
        assertTrue(dockLabelsShown(true, 8))
        assertFalse(dockLabelsShown(false, 3))
    }
}
