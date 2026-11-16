package dev.jesusmolano.alisio.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelPrefixTest {
    @Test
    fun `un verbo seguido de espacio elige el canal`() {
        assertEquals(ChannelQuery(Channel.WHATSAPP_CHAT, "laura"), parseChannelPrefix("wa laura"))
        assertEquals(ChannelQuery(Channel.CALL, "laura"), parseChannelPrefix("tel laura"))
        assertEquals(ChannelQuery(Channel.CALL, "Ana Mª"), parseChannelPrefix("llamar Ana Mª "))
        assertEquals(ChannelQuery(Channel.CALL, "ana"), parseChannelPrefix("Call ana"))
        assertEquals(ChannelQuery(Channel.WHATSAPP_VIDEO, "luis"), parseChannelPrefix("Vídeo luis"))
        assertEquals(ChannelQuery(Channel.SMS, "luis"), parseChannelPrefix("sms   luis"))
    }

    @Test
    fun `sin espacio o sin nombre es una busqueda normal`() {
        assertNull(parseChannelPrefix("telegram"))
        assertNull(parseChannelPrefix("wallapop"))
        assertNull(parseChannelPrefix("tel"))
        assertNull(parseChannelPrefix("tel "))
        assertNull(parseChannelPrefix("laura"))
        assertNull(parseChannelPrefix("whatsapp laura"))
    }

    @Test
    fun `olvidar el canal deja fuera solo lo anterior de esa persona`() {
        val ana = personKey("ana")
        val openings =
            mapOf(
                channelKey("ana", Channel.CALL) to listOf(10L, 20L, 30L),
                channelKey("luis", Channel.CALL) to listOf(10L),
                "app/x#0" to listOf(5L)
            )
        val forgotten = forgetChannels(openings, mapOf(ana to 25L))
        assertEquals(listOf(30L), forgotten[channelKey("ana", Channel.CALL)])
        assertEquals(listOf(10L), forgotten[channelKey("luis", Channel.CALL)])
        assertEquals(listOf(5L), forgotten["app/x#0"])
    }
}
