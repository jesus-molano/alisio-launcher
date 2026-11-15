package dev.jesusmolano.alisio.data.apps

import dev.jesusmolano.alisio.core.text.normalize

/** Apps que comparten inicial. [firstIndex] es la posición de la primera en la lista completa. */
data class AppSection<T>(val letter: String, val firstIndex: Int, val items: List<T>)

const val OTHER_SECTION = "#"

/** Inicial sin acentos y en mayúscula («Ábaco» → «A»); lo que no sea una letra de la A a la Z va a «#». */
fun sectionLetter(label: String): String {
    val first = normalize(label).firstOrNull() ?: return OTHER_SECTION
    return if (first in 'a'..'z') first.uppercaseChar().toString() else OTHER_SECTION
}

/** Agrupa por inicial respetando el orden de la lista. */
fun <T> List<T>.toSections(label: (T) -> String): List<AppSection<T>> {
    val groups = LinkedHashMap<String, Pair<Int, MutableList<T>>>()
    forEachIndexed { index, item ->
        groups.getOrPut(sectionLetter(label(item))) { index to mutableListOf() }.second += item
    }
    return groups.map { (letter, group) -> AppSection(letter, group.first, group.second) }
}
