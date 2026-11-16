package dev.jesusmolano.alisio.core.draw

import dev.jesusmolano.alisio.core.text.normalize

/** Con dos o más casi igual de usados se deja elegir; si no, se abre el primero. */
const val MAX_INITIAL_CHOICES = 3

// Lo que tiene que usarse el segundo frente al primero para contar como empate.
private const val TIE_RATIO = 0.8

/** Lo que pasa al dibujar una inicial. */
sealed interface InitialPick<out T> {
    /** Uno destaca: se abre. */
    class Open<T>(val item: T) : InitialPick<T>

    /** Varios igual de usados (o ninguno usado aún): se enseñan para elegir, el más usado primero. */
    class Choose<T>(val items: List<T>) : InitialPick<T>
}

/** La inicial de un nombre para dibujarla: su primera letra sin tilde y en mayúscula («Ñ» es «N»). */
fun initialOf(label: String): Char? = normalize(label).firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()

/**
 * Qué abrir al dibujar [letter]: de [items] (apps y personas), los que empiezan por ella, del más
 * usado al menos según [usage] (frecencia por clave). Si el segundo se usa casi como el primero
 * (o nada se ha usado), se deja elegir entre los [MAX_INITIAL_CHOICES] primeros. `null` si nada
 * empieza por esa letra.
 */
fun <T> pickByInitial(
    letter: Char,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    usage: Map<String, Double>
): InitialPick<T>? {
    val matching =
        items.filter { initialOf(label(it)) == letter.uppercaseChar() }
            .sortedWith(compareByDescending<T> { usage[key(it)] ?: 0.0 }.thenBy { normalize(label(it)) })
    if (matching.isEmpty()) return null
    if (matching.size == 1) return InitialPick.Open(matching.first())
    val best = usage[key(matching[0])] ?: 0.0
    val second = usage[key(matching[1])] ?: 0.0
    return if (best > 0.0 && second < best * TIE_RATIO) {
        InitialPick.Open(matching.first())
    } else {
        InitialPick.Choose(matching.take(MAX_INITIAL_CHOICES))
    }
}
