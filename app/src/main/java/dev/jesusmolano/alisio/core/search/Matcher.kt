package dev.jesusmolano.alisio.core.search

import kotlin.math.abs

/** Calidad de la coincidencia (§5.3 del plan) y posiciones del nombre que coinciden, para resaltarlas. */
class Match(val quality: Double, val positions: IntArray)

const val QUALITY_EXACT = 1.0
const val QUALITY_PREFIX = 0.9
const val QUALITY_WORD_START = 0.8
const val QUALITY_INITIALS = 0.7
const val QUALITY_SUBSEQUENCE = 0.5
const val QUALITY_TYPO = 0.4

private const val TYPO_MIN_LENGTH = 4

/**
 * Compara una consulta con un nombre, los dos ya normalizados con `normalize`. Devuelve la mejor
 * regla que se cumple, de más a menos estricta, o `null` si ninguna encaja.
 */
fun match(query: String, name: String): Match? {
    if (query.isEmpty() || name.isEmpty()) return null
    if (name == query) return Match(QUALITY_EXACT, range(0, query.length))
    if (name.startsWith(query)) return Match(QUALITY_PREFIX, range(0, query.length))

    val starts = wordStarts(name)
    for (start in starts) {
        if (start > 0 && name.startsWith(query, start)) return Match(QUALITY_WORD_START, range(start, query.length))
    }
    if (query.length >= 2 && query.length <= starts.size) {
        val initials = starts.take(query.length)
        if (initials.indices.all { name[initials[it]] == query[it] }) {
            return Match(QUALITY_INITIALS, initials.toIntArray())
        }
    }
    subsequence(query, name)?.let { return Match(QUALITY_SUBSEQUENCE, it) }
    if (query.length >= TYPO_MIN_LENGTH && starts.any { hasTypoAt(query, name, it) }) {
        return Match(QUALITY_TYPO, IntArray(0))
    }
    return null
}

private fun range(start: Int, length: Int) = IntArray(length) { start + it }

private fun wordStarts(name: String): List<Int> =
    name.indices.filter { name[it].isLetterOrDigit() && (it == 0 || !name[it - 1].isLetterOrDigit()) }

private fun subsequence(query: String, name: String): IntArray? {
    val positions = IntArray(query.length)
    var from = 0
    for (i in query.indices) {
        val found = name.indexOf(query[i], from)
        if (found < 0) return null
        positions[i] = found
        from = found + 1
    }
    return positions
}

/** ¿Empieza en [start] algo que está a una errata de [query]? Se prueban los tres largos posibles. */
private fun hasTypoAt(query: String, name: String, start: Int): Boolean =
    (query.length - 1..query.length + 1).any { length ->
        start + length <= name.length && withinOneEdit(query, name.substring(start, start + length))
    }

/** Una sustitución, una letra de más o de menos, o dos letras vecinas cambiadas de sitio. */
internal fun withinOneEdit(a: String, b: String): Boolean {
    if (abs(a.length - b.length) > 1) return false
    var i = 0
    while (i < a.length && i < b.length && a[i] == b[i]) i++
    if (i == a.length && i == b.length) return true
    return when {
        a.length == b.length ->
            a.regionMatches(i + 1, b, i + 1, a.length - i - 1) ||
                (
                    i + 1 < a.length &&
                        a[i] == b[i + 1] &&
                        a[i + 1] == b[i] &&
                        a.regionMatches(i + 2, b, i + 2, a.length - i - 2)
                    )

        a.length > b.length -> a.regionMatches(i + 1, b, i, b.length - i)

        else -> a.regionMatches(i, b, i + 1, a.length - i)
    }
}
