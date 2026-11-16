package dev.jesusmolano.alisio.data.now

import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.Forgotten
import dev.jesusmolano.alisio.data.db.ForgottenDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lo aprendido que se ha olvidado a mano (docs/IDEAS.md §2.6). No se borra ninguna apertura: borrar
 * historial cambiaría también la frecencia de la barra y la regla de Bluetooth. Se guarda desde
 * cuándo vuelve a contar, y las funciones puras de `core/` dejan fuera lo anterior.
 */
class ForgottenRepository(private val dao: ForgottenDao, private val clock: Clock, private val scope: CoroutineScope) {
    private val writes = Mutex()

    /** Desde cuándo cuenta cada decisión olvidada, por tipo y clave. */
    val marks: StateFlow<Map<Pair<String, String>, Long>> =
        dao.all()
            .map { rows -> rows.associate { (it.kind to it.key) to it.since } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Las de un tipo, por clave. */
    fun marksOf(kind: String): Map<String, Long> =
        marks.value.mapNotNull { (id, since) -> if (id.first == kind) id.second to since else null }.toMap()

    /** Olvida lo aprendido hasta ahora para [kind] y [key]. Devuelve cómo deshacerlo. */
    fun forget(kind: String, key: String): () -> Unit {
        // Solo se toca bajo `writes`, y deshacer se encola detrás de la escritura que lo rellena.
        var before: Forgotten? = null
        val now = clock.now()
        scope.launch {
            writes.withLock {
                before = dao.find(kind, key)
                dao.set(Forgotten(kind, key, now))
            }
        }
        return {
            scope.launch {
                writes.withLock {
                    val previous = before
                    if (previous == null) dao.clear(kind, key) else dao.set(previous)
                }
            }
        }
    }
}
