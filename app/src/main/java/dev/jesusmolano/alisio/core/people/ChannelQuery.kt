package dev.jesusmolano.alisio.core.people

import dev.jesusmolano.alisio.core.text.normalize

/** Lo que se ha escrito con un canal delante: «wa laura» es «Laura, por WhatsApp». */
data class ChannelQuery(val channel: Channel, val name: String)

// Pocos verbos y fijos, en los dos idiomas (docs/IDEAS.md, A4). «Vídeo» solo existe por WhatsApp.
private val VERBS: Map<String, Channel> =
    mapOf(
        "tel" to Channel.CALL,
        "llamar" to Channel.CALL,
        "call" to Channel.CALL,
        "wa" to Channel.WHATSAPP_CHAT,
        "vid" to Channel.WHATSAPP_VIDEO,
        "video" to Channel.WHATSAPP_VIDEO,
        "sms" to Channel.SMS
    )

/**
 * El canal que lo escrito pide delante del nombre, o `null` si no empieza por uno de los verbos.
 * El verbo solo cuenta seguido de un espacio y de algo más: «tel» solo, «telegram» o «wallapop»
 * siguen siendo búsquedas normales. No distingue mayúsculas ni acentos («Vídeo ana»).
 */
fun parseChannelPrefix(query: String): ChannelQuery? {
    val trimmed = query.trimStart()
    val space = trimmed.indexOf(' ')
    if (space <= 0) return null
    val channel = VERBS[normalize(trimmed.substring(0, space))] ?: return null
    val name = trimmed.substring(space + 1).trim()
    return if (name.isEmpty()) null else ChannelQuery(channel, name)
}
