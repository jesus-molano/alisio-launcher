package dev.jesusmolano.alisio.data.home

import dev.jesusmolano.alisio.core.dock.MAX_DOCK_SLOTS
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

/** Lo que hay configurado en un hueco del dock, con claves de app (`AppKey`). */
sealed interface SlotConfig {
    data object Empty : SlotConfig

    data class App(val appKey: String) : SlotConfig

    /** @param image nombre de la imagen elegida para la pila (`CustomImages`), aunque no esté en uso. */
    data class Stack(
        val name: String,
        val appKeys: List<String>,
        val icon: StackIcon,
        val glyph: String,
        val image: String? = null
    ) : SlotConfig
}

/** Cómo se dibuja el icono de una pila. [id] es lo que se guarda. */
enum class StackIcon(val id: String) {
    /** Como una carpeta: sus cuatro primeras apps en miniatura. */
    FOLDER("folder"),

    /** El icono de su primera app. */
    FIRST_APP("first"),

    /** Un símbolo o emoji que escribe el usuario. */
    SYMBOL("symbol"),

    /** Una imagen de la galería, recortada a la forma de los iconos. */
    IMAGE("image");

    companion object {
        fun parse(id: String?): StackIcon = entries.firstOrNull { it.id == id } ?: FOLDER
    }
}

/**
 * Configuración del dock: cada hueco está vacío, tiene una app o es una pila con nombre.
 *
 * @param visibleSlots cuántos huecos enseña el dock. Los de más allá siguen en la base de datos con
 *   lo que tenían: al reducir el dock se ocultan y vuelven tal cual al ampliarlo. Todo lo que lee
 *   [dock] (el Inicio, Ahora, Zonas, los ajustes) ve solo los visibles.
 */
class HomeConfigRepository(
    private val dao: HomeConfigDao,
    private val scope: CoroutineScope,
    private val visibleSlots: StateFlow<Int>
) {
    // Las escrituras van de una en una: dos toques seguidos sobre la misma app de una pila deben
    // añadirla y quitarla, y el segundo tiene que ver ya lo que escribió el primero.
    private val writes = Mutex()

    /**
     * Los [MAX_DOCK_SLOTS] huecos, también los ocultos; `null` hasta que se lee de la base de datos.
     * Solo lo usan los ajustes, para enseñar qué volverá al ampliar el dock.
     */
    val allSlots: StateFlow<List<SlotConfig>?> =
        combine(dao.dock(), dao.stackItems()) { slots, items ->
            val byPosition = slots.associateBy { it.position }
            val stacks = items.groupBy({ it.slot }, { it.appKey })
            List(MAX_DOCK_SLOTS) { position ->
                val slot = byPosition[position]
                when {
                    slot?.appKey != null -> SlotConfig.App(slot.appKey)

                    slot?.stackName != null ->
                        SlotConfig.Stack(
                            name = slot.stackName,
                            appKeys = stacks[position].orEmpty(),
                            icon = StackIcon.parse(slot.stackIcon),
                            glyph = slot.stackGlyph.orEmpty(),
                            image = slot.stackImage
                        )

                    else -> SlotConfig.Empty
                }
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Un elemento por hueco visible, en orden; `null` hasta que se lee de la base de datos. */
    val dock: StateFlow<List<SlotConfig>?> =
        combine(allSlots, visibleSlots) { slots, count -> slots?.take(count) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Pone una app en el hueco. Devuelve cómo deshacerlo: lo que el hueco tenía antes vuelve entero. */
    fun setApp(position: Int, appKey: String): () -> Unit = replace(position) {
        dao.clearStack(position)
        dao.setSlot(DockSlot(position, appKey = appKey))
    }

    /**
     * Hace [change] en el hueco y devuelve la orden que lo deja como estaba: su app, o su pila con
     * nombre, icono, apps y orden. Lo anterior se lee dentro de la misma escritura, así que es
     * exactamente lo que [change] pisa.
     */
    private fun replace(position: Int, change: suspend () -> Unit): () -> Unit {
        // Solo se toca bajo `writes`, y deshacer se encola detrás de la escritura que lo rellena.
        var before: Pair<DockSlot?, List<StackItem>>? = null
        write {
            before = dao.slot(position) to dao.stackOf(position)
            change()
        }
        return {
            write {
                before?.let { (slot, items) ->
                    dao.clearStack(position)
                    if (slot == null) dao.clearSlot(position) else dao.setSlot(slot)
                    items.forEach { dao.addToStack(it) }
                }
            }
        }
    }

    /**
     * Convierte el hueco en una pila. Si tenía una app, queda como la primera de la pila: convertir
     * no debe parecer que la app se ha perdido ni obligar a buscarla otra vez.
     */
    fun makeStack(position: Int, name: String) = write {
        val seed = dao.slot(position)?.appKey
        dao.clearStack(position)
        dao.setSlot(DockSlot(position, stackName = name))
        if (seed != null) dao.addToStack(StackItem(position, seed, 0))
    }

    /**
     * Pone [appKeys] en los primeros huecos visibles, uno por hueco. Solo toca huecos vacíos, así
     * que deshacerlo es vaciar los que ha llenado. Devuelve esa orden.
     */
    fun fill(appKeys: List<String>): () -> Unit {
        // Solo se toca bajo `writes`, como en `replace`.
        var filled = emptyList<Int>()
        write {
            val free = (0 until visibleSlots.value).filter { dao.slot(it).isFree() }
            filled = free.zip(appKeys) { position, appKey ->
                dao.setSlot(DockSlot(position, appKey = appKey))
                position
            }
        }
        return { write { filled.forEach { dao.clearSlot(it) } } }
    }

    private fun DockSlot?.isFree(): Boolean = this == null || (appKey == null && stackName == null)

    fun renameStack(position: Int, name: String) = write { dao.renameStack(position, name) }

    fun setStackIcon(position: Int, icon: StackIcon, glyph: String) = write {
        dao.setStackIcon(position, icon.id, glyph.ifBlank { null })
    }

    /**
     * Pone a la pila la imagen [image] (`CustomImages`) y la elige como su icono. Devuelve cómo
     * deshacerlo: vuelven el modo y la imagen de antes, que sigue en el disco hasta el próximo arranque.
     */
    fun setStackImage(position: Int, image: String): () -> Unit {
        // Solo se toca bajo `writes`, como en `replace`.
        var before: DockSlot? = null
        write {
            before = dao.slot(position)
            dao.setStackImage(position, StackIcon.IMAGE.id, image)
        }
        return {
            write {
                before?.let { dao.setStackImage(position, it.stackIcon ?: StackIcon.FOLDER.id, it.stackImage) }
            }
        }
    }

    /** Vacía el hueco. Devuelve cómo deshacerlo, igual que [setApp]. */
    fun clear(position: Int): () -> Unit = replace(position) {
        dao.clearStack(position)
        dao.clearSlot(position)
    }

    /** Guarda el orden elegido a mano: cada app queda en el puesto que ocupa en [appKeys]. */
    fun reorderStack(position: Int, appKeys: List<String>) = write {
        appKeys.forEachIndexed { index, appKey -> dao.setStackPosition(position, appKey, index) }
    }

    /**
     * Mete [key] al final de la pila del hueco, si no estaba ya. Puede ser un acceso directo fijado
     * desde otra app (`shortcutKey`): `stack_item` guarda claves y no distingue, y así no hace falta
     * cambiar el esquema (docs/IDEAS.md §2.3).
     */
    fun addToStack(position: Int, key: String) = write {
        if (!dao.isInStack(position, key)) dao.addToStack(StackItem(position, key, dao.nextStackPosition(position)))
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
