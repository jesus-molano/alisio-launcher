package dev.jesusmolano.alisio.core.search

import dev.jesusmolano.alisio.core.text.normalize
import java.time.ZoneId

/**
 * Algo que la barra puede encontrar. [key] es su clave en el historial de aperturas.
 *
 * @param synonyms otras formas de llamarlo (los ajustes los usan); cuentan para encontrar, no para resaltar.
 * @param weight multiplica la calidad: por debajo de 1 deja pasar delante a una app que coincide igual.
 */
class IndexItem<T>(
    val key: String,
    val label: String,
    val payload: T,
    synonyms: List<String> = emptyList(),
    val weight: Double = 1.0
) {
    val normalized: String = normalize(label)
    val normalizedSynonyms: List<String> = synonyms.map(::normalize)
}

class SearchHit<T>(val item: IndexItem<T>, val score: Double, val positions: IntArray)

/** Frecencia y extra horario por clave, ya calculados para el momento en que se abre la barra. */
class Usage(val frecency: Map<String, Double>, val hourBonus: Map<String, Double>) {
    companion object {
        val NONE = Usage(emptyMap(), emptyMap())

        fun from(openings: Map<String, List<Long>>, now: Long, zone: ZoneId): Usage = Usage(
            frecency = openings.mapValues { frecency(it.value, now) },
            hourBonus = openings.mapValues { hourBonus(it.value, now, zone) }.filterValues { it > 0 }
        )
    }
}

/**
 * Recorre el índice entero en cada tecla (§5.3) y devuelve lo que coincide, de mayor a menor
 * puntuación. En empate gana el nombre más corto y después el orden alfabético.
 */
fun <T> search(items: List<IndexItem<T>>, query: String, usage: Usage): List<SearchHit<T>> {
    val normalizedQuery = normalize(query)
    if (normalizedQuery.isEmpty()) return emptyList()
    val hits = ArrayList<SearchHit<T>>()
    for (item in items) {
        val onLabel = match(normalizedQuery, item.normalized)
        val onSynonym = item.normalizedSynonyms.maxOfOrNull { match(normalizedQuery, it)?.quality ?: 0.0 } ?: 0.0
        val quality = maxOf(onLabel?.quality ?: 0.0, onSynonym)
        if (quality == 0.0) continue
        val total =
            score(quality * item.weight, usage.frecency[item.key] ?: 0.0, usage.hourBonus[item.key] ?: 0.0)
        hits += SearchHit(item, total, onLabel?.positions ?: IntArray(0))
    }
    hits.sortWith(
        compareByDescending<SearchHit<T>> { it.score }
            .thenBy { it.item.normalized.length }
            .thenBy { it.item.normalized }
    )
    return hits
}
