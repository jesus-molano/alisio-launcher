package dev.jesusmolano.alisio.core.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovalTest {
    @Test
    fun `una app instalada por el usuario se desinstala entera`() {
        assertEquals(Removal.UNINSTALL, removalFor(system = false, updated = false, ownProfile = true))
    }

    @Test
    fun `una del sistema solo quita sus actualizaciones, y sin ellas nada`() {
        assertEquals(Removal.UPDATES, removalFor(system = true, updated = true, ownProfile = true))
        assertEquals(Removal.NONE, removalFor(system = true, updated = false, ownProfile = true))
    }

    @Test
    fun `las de otro perfil no se ofrecen`() {
        assertEquals(Removal.NONE, removalFor(system = false, updated = false, ownProfile = false))
    }
}
