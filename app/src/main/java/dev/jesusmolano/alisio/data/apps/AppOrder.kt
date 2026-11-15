package dev.jesusmolano.alisio.data.apps

import dev.jesusmolano.alisio.core.text.normalize

/** Orden A–Z sin distinguir acentos ni mayúsculas; el desempate es estable. */
fun <T> List<T>.sortedAlphabetically(label: (T) -> String, key: (T) -> AppKey): List<T> =
    map { Triple(normalize(label(it)), label(it), it) }
        .sortedWith(compareBy({ it.first }, { it.second }, { key(it.third).toString() }))
        .map { it.third }
