package dev.jesusmolano.alisio.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadTest {
    private val chat = "com.whatsapp"
    private val mail = "com.google.android.gm"
    private val x = "com.twitter.android"

    @Test
    fun `una notificacion normal cuenta`() {
        assertTrue(NotificationFacts(chat).countsAsUnread())
    }

    @Test
    fun `una permanente no cuenta`() {
        assertFalse(NotificationFacts(chat, ongoing = true).countsAsUnread())
    }

    @Test
    fun `una de servicio en primer plano no cuenta`() {
        assertFalse(NotificationFacts(chat, foregroundService = true).countsAsUnread())
    }

    @Test
    fun `el resumen de un grupo no cuenta`() {
        assertFalse(NotificationFacts(chat, groupSummary = true).countsAsUnread())
    }

    @Test
    fun `el aviso de un reproductor en pausa no cuenta`() {
        assertFalse(NotificationFacts("com.spotify.music", media = true).countsAsUnread())
    }

    @Test
    fun `el recuento va por paquete y solo con las que cuentan`() {
        val notifications =
            listOf(
                NotificationFacts(chat),
                NotificationFacts(chat),
                NotificationFacts(chat, groupSummary = true),
                NotificationFacts(mail),
                NotificationFacts(mail, ongoing = true)
            )
        assertEquals(mapOf(chat to 2, mail to 1), unreadByPackage(notifications))
    }

    @Test
    fun `un paquete sin nada que cuente no aparece`() {
        val notifications =
            listOf(NotificationFacts(chat, ongoing = true), NotificationFacts(chat, groupSummary = true))
        assertEquals(emptyMap<String, Int>(), unreadByPackage(notifications))
    }

    @Test
    fun `sin notificaciones no hay recuento`() {
        assertEquals(emptyMap<String, Int>(), unreadByPackage(emptyList()))
    }

    @Test
    fun `un paquete apagado no ensena punto`() {
        assertEquals(setOf(chat), packagesWithDot(mapOf(chat to 3, x to 9), muted = setOf(x)))
    }

    @Test
    fun `un recuento a cero no ensena punto`() {
        assertEquals(emptySet<String>(), packagesWithDot(mapOf(chat to 0), muted = emptySet()))
    }
}
