package dev.jesusmolano.alisio.data.zones

import dev.jesusmolano.alisio.core.zones.AssignmentChange
import dev.jesusmolano.alisio.core.zones.CustomZone
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.core.zones.assignmentChange
import dev.jesusmolano.alisio.data.db.ZoneAssignment
import dev.jesusmolano.alisio.data.db.ZoneDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lo que Zonas guarda: las islas propias y la isla elegida a mano para cada app (§5.7, §5.9). */
class ZonesRepository(private val dao: ZoneDao, private val scope: CoroutineScope) {
    // De una en una, como en el dock: borrar una isla son dos sentencias, y una asignación hecha
    // entre medias no debe quedarse apuntando a una isla que ya no existe.
    private val writes = Mutex()

    /** Las islas propias en su orden; `null` hasta que se lee de la base de datos. */
    val custom: StateFlow<List<CustomZone>?> =
        dao.zones()
            .map { rows -> rows.map { CustomZone(it.id, it.name) } }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Isla elegida a mano por clave de app; `null` hasta que se lee. Una fila ilegible se ignora. */
    val assignments: StateFlow<Map<String, ZoneId>?> =
        dao.assignments()
            .map { rows -> rows.mapNotNull { row -> ZoneId.parse(row.zone)?.let { row.appKey to it } }.toMap() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Crea una isla al final. Un nombre en blanco no crea nada. */
    fun create(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) write { dao.create(clean) }
    }

    fun rename(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) write { dao.rename(id, clean) }
    }

    /**
     * Borra la isla y lo asignado a ella: sus apps vuelven a la isla que les toca sin asignación
     * (Resto, salvo las del dock y las de Sistema). Primero las asignaciones: si el proceso muere
     * entre las dos sentencias queda una isla vacía, no apps apuntando a una isla que no existe.
     */
    fun delete(id: Long) = write {
        dao.clearZone(ZoneId.Custom(id).encode())
        dao.delete(id)
    }

    /**
     * Lleva la app a [target]. Si es la isla que le toca sin asignación, basta con olvidar la que
     * tuviera; a Diario o a una pila no se puede llevar desde aquí y no se guarda nada.
     *
     * @param default la isla de la app sin asignación.
     */
    fun move(appKey: String, target: ZoneId, default: ZoneId) {
        when (val change = assignmentChange(target, default)) {
            AssignmentChange.Clear -> write { dao.clear(appKey) }
            is AssignmentChange.Assign -> write { dao.assign(ZoneAssignment(appKey, change.zone.encode())) }
            AssignmentChange.Invalid -> Unit
        }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch { writes.withLock { block() } }
    }
}
