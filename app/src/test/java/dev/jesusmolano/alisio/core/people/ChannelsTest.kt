package dev.jesusmolano.alisio.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelsTest {
    private val all = Channel.entries.toSet()

    @Test
    fun `sin historial gana el chat de WhatsApp y despues la llamada`() {
        assertEquals(MainChannel(Channel.WHATSAPP_CHAT, learned = false), mainChannel("ana", all, emptyMap()))
        assertEquals(
            MainChannel(Channel.CALL, learned = false),
            mainChannel("ana", setOf(Channel.CALL, Channel.SMS), emptyMap())
        )
        assertEquals(MainChannel(Channel.SMS, learned = false), mainChannel("ana", setOf(Channel.SMS), emptyMap()))
        assertNull(mainChannel("ana", emptySet(), emptyMap()))
    }

    @Test
    fun `con historial gana el canal con mas peso`() {
        val frecency =
            mapOf(
                channelKey("ana", Channel.CALL) to 3.0,
                channelKey("ana", Channel.WHATSAPP_CHAT) to 1.0,
                channelKey("luis", Channel.SMS) to 9.0
            )
        assertEquals(MainChannel(Channel.CALL, learned = true), mainChannel("ana", all, frecency))
    }

    @Test
    fun `un canal aprendido que ya no existe no cuenta`() {
        val frecency = mapOf(channelKey("ana", Channel.WHATSAPP_VIDEO) to 5.0)
        assertEquals(
            MainChannel(Channel.CALL, learned = false),
            mainChannel("ana", setOf(Channel.CALL, Channel.SMS), frecency)
        )
    }

    @Test
    fun `en empate manda el orden de los canales`() {
        val frecency = mapOf(channelKey("ana", Channel.SMS) to 2.0, channelKey("ana", Channel.CALL) to 2.0)
        assertEquals(MainChannel(Channel.CALL, learned = true), mainChannel("ana", all, frecency))
    }

    @Test
    fun `las aperturas de una persona juntan las de sus canales`() {
        val frecency =
            mapOf(
                channelKey("ana", Channel.CALL) to listOf(1L, 5L),
                channelKey("ana", Channel.SMS) to listOf(3L),
                channelKey("luis", Channel.CALL) to listOf(4L),
                "com.app/.Main#0" to listOf(7L)
            )
        val byPerson = openingsByPerson(frecency).mapValues { it.value.sorted() }
        assertEquals(mapOf(personKey("ana") to listOf(1L, 3L, 5L), personKey("luis") to listOf(4L)), byPerson)
    }

    @Test
    fun `una clave de busqueda con barras verticales no se confunde`() {
        val lookup = "0r1-2A|3B"
        assertEquals(
            mapOf(personKey(lookup) to listOf(1L)),
            openingsByPerson(mapOf(channelKey(lookup, Channel.CALL) to listOf(1L)))
        )
        val frecency = mapOf(channelKey(lookup, Channel.CALL) to 1.0)
        assertEquals(MainChannel(Channel.CALL, learned = true), mainChannel(lookup, all, frecency))
    }

    @Test
    fun `la clave guardada no deja ver la clave del contacto`() {
        val lookup = "r12-4A6F73C3A9"
        val key = personKey(lookup)
        assertEquals(key, personKey(lookup))
        assertEquals(false, lookup in key)
        assertEquals(false, key == personKey("r13-4A6F73C3A9"))
    }

    @Test
    fun `las iniciales salen de las dos primeras palabras`() {
        assertEquals("AG", initials("ana garcía lópez"))
        assertEquals("L", initials("  Luis "))
        assertEquals("", initials(" "))
        assertEquals("\uD83D\uDE00A", initials("\uD83D\uDE00 Ana"))
    }
}
