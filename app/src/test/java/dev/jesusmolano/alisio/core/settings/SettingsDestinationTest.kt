package dev.jesusmolano.alisio.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsDestinationTest {
    private val nothingOn =
        AccessUse(
            player = false,
            unreadDots = false,
            lockDoubleTap = false,
            notificationsSwipe = false,
            contactPhotos = false
        )

    @Test
    fun `sin ser la app de inicio siempre falta ese acceso`() {
        assertEquals(setOf(Access.HOME_ROLE), blockingAccess(emptySet(), nothingOn))
    }

    @Test
    fun `con todo apagado y el rol de inicio no falta nada`() {
        assertEquals(emptySet<Access>(), blockingAccess(setOf(Access.HOME_ROLE), nothingOn))
    }

    @Test
    fun `el reproductor o los puntos piden el acceso a notificaciones`() {
        val granted = setOf(Access.HOME_ROLE)
        assertEquals(setOf(Access.NOTIFICATIONS), blockingAccess(granted, nothingOn.copy(player = true)))
        assertEquals(setOf(Access.NOTIFICATIONS), blockingAccess(granted, nothingOn.copy(unreadDots = true)))
    }

    @Test
    fun `cualquiera de los dos gestos pide el servicio de accesibilidad`() {
        val granted = setOf(Access.HOME_ROLE)
        assertEquals(setOf(Access.ACCESSIBILITY), blockingAccess(granted, nothingOn.copy(lockDoubleTap = true)))
        assertEquals(setOf(Access.ACCESSIBILITY), blockingAccess(granted, nothingOn.copy(notificationsSwipe = true)))
    }

    @Test
    fun `las fotos piden los contactos`() {
        assertEquals(
            setOf(Access.CONTACTS),
            blockingAccess(setOf(Access.HOME_ROLE), nothingOn.copy(contactPhotos = true))
        )
    }

    @Test
    fun `un acceso concedido no falta aunque se use`() {
        val everythingOn =
            AccessUse(
                player = true,
                unreadDots = true,
                lockDoubleTap = true,
                notificationsSwipe = true,
                contactPhotos = true
            )
        assertEquals(emptySet<Access>(), blockingAccess(Access.entries.toSet(), everythingOn))
    }

    @Test
    fun `el audio Bluetooth y el acceso a uso nunca bloquean`() {
        val granted = Access.entries.toSet() - Access.BLUETOOTH - Access.USAGE
        assertEquals(emptySet<Access>(), blockingAccess(granted, nothingOn))
    }

    @Test
    fun `los accesos que faltan van primero y cada grupo conserva su orden`() {
        val granted = setOf(Access.HOME_ROLE, Access.BLUETOOTH)
        assertEquals(
            listOf(
                Access.CONTACTS,
                Access.NOTIFICATIONS,
                Access.USAGE,
                Access.ACCESSIBILITY,
                Access.HOME_ROLE,
                Access.BLUETOOTH
            ),
            accessOrder(granted)
        )
    }

    @Test
    fun `solo la portada, Aspecto e Inicio llevan la vista previa`() {
        assertEquals(
            setOf(SettingsDestination.COVER, SettingsDestination.LOOK, SettingsDestination.HOME),
            SettingsDestination.entries.filter { it.hasPreview }.toSet()
        )
    }
}
