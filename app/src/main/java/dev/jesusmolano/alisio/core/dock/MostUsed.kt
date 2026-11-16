package dev.jesusmolano.alisio.core.dock

/**
 * Las apps más abiertas, para rellenar de una vez un dock vacío: primero la que más veces se ha
 * abierto y, a igualdad, la abierta más recientemente. Una app sin ninguna apertura no entra: no hay
 * motivo para proponerla.
 *
 * @param openings horas de apertura por clave; puede traer claves que no son apps (accesos directos,
 *   contactos), que se ignoran.
 * @param installed claves de las apps instaladas, las únicas que pueden ir al dock.
 * @param count cuántas como mucho.
 */
fun mostUsed(openings: Map<String, List<Long>>, installed: Collection<String>, count: Int): List<String> = installed
    .asSequence()
    .distinct()
    .mapNotNull { key -> openings[key]?.takeIf { it.isNotEmpty() }?.let { key to it } }
    .sortedWith(
        compareByDescending<Pair<String, List<Long>>> { it.second.size }
            .thenByDescending { it.second.max() }
            .thenBy { it.first }
    )
    .take(count.coerceAtLeast(0))
    .map { it.first }
    .toList()
