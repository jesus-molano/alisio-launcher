package dev.jesusmolano.alisio.data.home

import dev.jesusmolano.alisio.data.db.DockSlot
import dev.jesusmolano.alisio.data.db.HomeConfigDao
import dev.jesusmolano.alisio.data.db.StackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val DOCK_SLOTS = 6

/** Lo que hay configurado en un hueco del dock, con claves de app (`AppKey`). */
sealed interface SlotConfig {
    data object Empty : SlotConfig

    data class App(val appKey: String) : SlotConfig

    data class Stack(val name: String, val appKeys: List<String>) : SlotConfig
}

/** Configuración del dock: cada hueco está vacío, tiene una app o es una pila con nombre. */
class HomeConfigRepository(private val dao: HomeConfigDao, private val scope: CoroutineScope) {
    // Las escrituras van de una en una: dos toques seguidos sobre la misma app de una pila deben
    // añadirla y quitarla, y el segundo tiene que ver ya lo que escribió el primero.
    private val writes = Mutex()

    /** [DOCK_SLOTS] elementos, en orden; `null` hasta que se lee de la base de datos. */
    val dock: StateFlow<List<SlotConfig>?> =
        combine(dao.dock(), dao.stackItems()) { slots, items ->
            val byPosition = slots.associateBy { it.position }
            val stacks = items.groupBy({ it.slot }, { it.appKey })
            List(DOCK_SLOTS) { position ->
                val slot = byPosition[position]
                when {
                    slot?.appKey != null -> SlotConfig.App(slot.appKey)
                    slot?.stackName != null -> SlotConfig.Stack(slot.stackName, stacks[position].orEmpty())
                    else -> SlotConfig.Empty
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setApp(position: Int, appKey: String) = write {
        dao.clearStack(position)
        dao.setSlot(DockSlot(position, appKey = appKey))
    }

    /** Convierte el hueco en pila o le cambia el nombre; las apps que ya tuviera la pila se quedan. */
    fun setStack(position: Int, name: String) = write { dao.setSlot(DockSlot(position, stackName = name)) }

    fun clear(position: Int) = write {
        dao.clearStack(position)
        dao.clearSlot(position)
    }

    fun toggleInStack(position: Int, appKey: String) = write {
        if (dao.isInStack(position, appKey)) {
            dao.removeFromStack(position, appKey)
        } else {
            dao.addToStack(StackItem(position, appKey, dao.nextStackPosition(position)))
        }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch { writes.withLock { block() } }
    }
}
