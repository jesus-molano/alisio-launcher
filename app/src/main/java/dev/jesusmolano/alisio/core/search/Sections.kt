package dev.jesusmolano.alisio.core.search

import dev.jesusmolano.alisio.core.text.normalize

/**
 * Reparte resultados ya ordenados en secciones. Las secciones salen por el orden de su mejor
 * resultado, así el mejor de todos es el primero de la primera; cada una se corta en su límite.
 */
fun <T, S> List<T>.toSections(sectionOf: (T) -> S, limitOf: (S) -> Int): List<Pair<S, List<T>>> =
    groupBy(sectionOf).map { (section, items) -> section to items.take(limitOf(section)) }

/**
 * Filtra una lista por nombre con las mismas reglas que la barra, de mejor a peor coincidencia.
 * Con la consulta vacía devuelve la lista tal cual.
 */
fun <T> List<T>.filterByName(query: String, label: (T) -> String): List<T> {
    val normalized = normalize(query)
    if (normalized.isEmpty()) return this
    return mapNotNull { item -> match(normalized, normalize(label(item)))?.let { item to it.quality } }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** Claves ordenadas de la apertura más reciente a la más antigua. */
fun Map<String, List<Long>>.mostRecentKeys(): List<String> =
    entries.filter { it.value.isNotEmpty() }.sortedByDescending { it.value.max() }.map { it.key }

/** La lista con el elemento de [from] movido a [to]; los demás conservan su orden. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
