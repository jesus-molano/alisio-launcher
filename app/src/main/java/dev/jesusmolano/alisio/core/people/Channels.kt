package dev.jesusmolano.alisio.core.people

import dev.jesusmolano.alisio.core.text.normalize
import dev.jesusmolano.alisio.core.text.takeGraphemes
import java.security.MessageDigest

/** Formas de contactar con una persona (§5.4). [id] es lo que se guarda en el historial. */
enum class Channel(val id: String) {
    WHATSAPP_CHAT("wa-chat"),
    WHATSAPP_CALL("wa-call"),
    WHATSAPP_VIDEO("wa-video"),
    CALL("call"),
    SMS("sms")
}

private const val PERSON_PREFIX = "person:"
private const val CHANNEL_SEPARATOR = '|'
private const val DIGEST_BYTES = 12

/**
 * Clave de la persona en el índice de la barra y en el historial. Lleva un resumen de su
 * `LOOKUP_KEY`, no la clave misma: en contactos locales esa clave incluye el nombre codificado, y
 * el historial se guarda en disco.
 */
fun personKey(lookupKey: String): String = PERSON_PREFIX + digest(lookupKey)

private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray())
    .take(DIGEST_BYTES)
    .joinToString("") { "%02x".format(it) }

/** Clave de historial del par contacto-canal: la frecencia del canal aprendido se lleva con ella. */
fun channelKey(lookupKey: String, channel: Channel): String = personKey(lookupKey) + CHANNEL_SEPARATOR + channel.id

/**
 * Junta, por persona, las aperturas de todos sus canales: es lo que ordena a las personas en la
 * barra y lo que dice a quién se ha contactado hace menos. Las claves que no son de un canal de
 * persona se dejan fuera.
 */
fun openingsByPerson(openingsByKey: Map<String, List<Long>>): Map<String, List<Long>> {
    val result = HashMap<String, MutableList<Long>>()
    for ((key, openings) in openingsByKey) {
        if (!key.startsWith(PERSON_PREFIX)) continue
        val separator = key.lastIndexOf(CHANNEL_SEPARATOR)
        if (separator < 0) continue
        result.getOrPut(key.substring(0, separator)) { ArrayList() } += openings
    }
    return result
}

/**
 * Canal del botón principal: el que más peso tiene de los disponibles. Sin historial, el chat de
 * WhatsApp si existe y, si no, la llamada; `null` si la persona no tiene ninguno.
 */
fun mainChannel(lookupKey: String, available: Set<Channel>, frecencyByKey: Map<String, Double>): MainChannel? {
    val best =
        available
            .map { it to (frecencyByKey[channelKey(lookupKey, it)] ?: 0.0) }
            .filter { it.second > 0.0 }
            // En empate manda el orden de [Channel], que es estable.
            .maxWithOrNull(compareBy<Pair<Channel, Double>> { it.second }.thenByDescending { it.first.ordinal })
    if (best != null) return MainChannel(best.first, learned = true)
    val fallback =
        when {
            Channel.WHATSAPP_CHAT in available -> Channel.WHATSAPP_CHAT
            Channel.CALL in available -> Channel.CALL
            else -> Channel.entries.firstOrNull { it in available }
        }
    return fallback?.let { MainChannel(it, learned = false) }
}

/** @param learned `true` si el canal sale del historial y no de la regla por defecto. */
data class MainChannel(val channel: Channel, val learned: Boolean)

/** Iniciales para el avatar: la primera letra de las dos primeras palabras, en mayúscula. */
fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.takeGraphemes(1).uppercase() }

/**
 * Quita de [this] lo que se llama igual que un contacto, sin distinguir acentos ni mayúsculas. Una
 * conversación de una app de mensajería con alguien de la agenda es esa misma persona: en la barra
 * basta con su tarjeta, que ya ofrece ese canal.
 */
fun <T> List<T>.withoutContacts(contactNames: Collection<String>, label: (T) -> String): List<T> {
    val names = contactNames.mapTo(HashSet(), ::normalize)
    return filterNot { normalize(label(it)) in names }
}
