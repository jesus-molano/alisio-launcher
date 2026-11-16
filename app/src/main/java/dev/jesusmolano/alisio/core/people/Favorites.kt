package dev.jesusmolano.alisio.core.people

/** Caras que caben en una fila de la barra sin apretarse ni tapar sus nombres. */
const val MAX_FAVORITES = 5

// Las claves de persona ([personKey]) no llevan este carácter: separa sin ambigüedad.
private const val SEPARATOR = '\n'

/** Las personas favoritas guardadas, en su orden; lo vacío o repetido se descarta. */
fun parseFavorites(stored: String?): List<String> =
    stored.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_FAVORITES)

fun formatFavorites(keys: List<String>): String = keys.joinToString(SEPARATOR.toString())

/**
 * Pone o quita [key] de las favoritas: la nueva va al final. Devuelve `null` si ya hay
 * [MAX_FAVORITES] y se pide otra: hay que quitar una antes.
 */
fun toggleFavorite(current: List<String>, key: String): List<String>? = when {
    key in current -> current - key
    current.size >= MAX_FAVORITES -> null
    else -> current + key
}
