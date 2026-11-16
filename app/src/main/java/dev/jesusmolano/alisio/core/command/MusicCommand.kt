package dev.jesusmolano.alisio.core.command

import dev.jesusmolano.alisio.core.text.normalize

// Frases enteras y ya sin tildes: «musica» y «music» solos, o con el verbo de ponerla delante.
private val MUSIC_PHRASES =
    setOf("musica", "music", "pon musica", "poner musica", "play music", "reanudar musica", "resume music")

/**
 * Si lo escrito en la barra pide la música (Música siempre a mano): solo la frase entera, para que
 * «music player» o «musica clasica» sigan siendo una búsqueda.
 */
fun isMusicCommand(input: String): Boolean = normalize(input).replace(Regex("""\s+"""), " ").trim() in MUSIC_PHRASES
