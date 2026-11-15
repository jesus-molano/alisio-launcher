package dev.jesusmolano.alisio.data.home

import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.apps.shortcutKey
import dev.jesusmolano.alisio.data.db.IconGesture
import dev.jesusmolano.alisio.data.db.IconGestureDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A dónde lleva deslizar hacia arriba sobre un icono: otra app o un acceso directo (§5.6). */
sealed interface GestureTarget {
    data class App(val appKey: String) : GestureTarget

    data class Shortcut(val packageName: String, val id: String, val userSerial: Long) : GestureTarget {
        /** La misma clave que lleva el acceso directo en `AppsRepository` y en el historial. */
        val key: String get() = shortcutKey(packageName, id, userSerial)
    }
}

/** Un [GestureTarget] ya resuelto a algo instalado, que se puede abrir y pintar. */
sealed interface SwipeTarget {
    val label: String

    class App(val app: AppEntry) : SwipeTarget {
        override val label: String get() = app.label
    }

    class Shortcut(val shortcut: ShortcutEntry) : SwipeTarget {
        override val label: String get() = shortcut.label
    }
}

/**
 * Destino secundario de cada icono. Va por app (su `AppKey`) y no por posición del dock: al cambiar
 * la app de un hueco, la nueva no hereda el gesto de la anterior, y la anterior lo recupera si vuelve.
 */
class IconGestureRepository(private val dao: IconGestureDao, private val scope: CoroutineScope) {
    /** Por clave de la app del icono. Una fila que no describe ningún destino se ignora. */
    val targets: StateFlow<Map<String, GestureTarget>> =
        dao.all()
            .map { rows -> rows.mapNotNull { row -> row.target()?.let { row.appKey to it } }.toMap() }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun set(appKey: String, target: GestureTarget) {
        val row =
            when (target) {
                is GestureTarget.App -> IconGesture(appKey, targetAppKey = target.appKey)

                is GestureTarget.Shortcut ->
                    IconGesture(
                        appKey,
                        shortcutPackage = target.packageName,
                        shortcutId = target.id,
                        shortcutUser = target.userSerial
                    )
            }
        scope.launch { dao.set(row) }
    }

    fun clear(appKey: String) {
        scope.launch { dao.clear(appKey) }
    }

    private fun IconGesture.target(): GestureTarget? = when {
        targetAppKey != null -> GestureTarget.App(targetAppKey)

        shortcutPackage != null && shortcutId != null && shortcutUser != null ->
            GestureTarget.Shortcut(shortcutPackage, shortcutId, shortcutUser)

        else -> null
    }
}
