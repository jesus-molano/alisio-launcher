package dev.jesusmolano.alisio.data.apps

import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.data.db.AppCustom
import dev.jesusmolano.alisio.data.db.AppCustomDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nombre propio, icono propio y ocultación de cada app (tabla `app_custom`). Cada cambio devuelve
 * la orden que lo deshace: la fila vuelve a como estaba justo antes.
 */
class AppCustomRepository(
    private val dao: AppCustomDao,
    private val images: CustomImages,
    private val scope: CoroutineScope
) {
    // Una escritura detrás de otra: el deshacer de un cambio lee lo que el cambio pisó, no algo a medias.
    private val writes = Mutex()

    /** Por clave de app; `null` hasta leer la base, para no pintar un instante los nombres sin cambiar. */
    val all: StateFlow<Map<String, AppCustom>?> =
        dao.all().map { rows -> rows.associateBy { it.appKey } }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // Las imágenes que ya no usa ninguna app ni pila (cambiadas, devueltas al original) se borran al
        // arrancar: durante la sesión aún pueden volver con «Deshacer».
        scope.launch {
            val used = dao.usedIcons().mapNotNull { (CustomIcon.parse(it) as? CustomIcon.Image)?.file }
            images.cleanup(used.toSet())
        }
    }

    /** `null` devuelve el nombre de la app. */
    fun setLabel(appKey: String, label: String?): () -> Unit = change(appKey) { it.copy(label = label) }

    /** `null` devuelve el icono de la app, en el estilo elegido. */
    fun setIcon(appKey: String, icon: CustomIcon?): () -> Unit = change(appKey) { it.copy(icon = icon?.id) }

    fun setHidden(appKey: String, hidden: Boolean): () -> Unit = change(appKey) { it.copy(hidden = hidden) }

    private fun change(appKey: String, edit: (AppCustom) -> AppCustom): () -> Unit {
        // Solo se toca bajo `writes`, y deshacer se encola detrás de la escritura que lo rellena.
        var before: AppCustom? = null
        write {
            before = dao.find(appKey)
            store(edit(before ?: AppCustom(appKey)))
        }
        return {
            write {
                val previous = before
                if (previous == null) dao.clear(appKey) else dao.set(previous)
            }
        }
    }

    private suspend fun store(custom: AppCustom) {
        if (custom.isEmpty) dao.clear(custom.appKey) else dao.set(custom)
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch { writes.withLock { block() } }
    }
}
