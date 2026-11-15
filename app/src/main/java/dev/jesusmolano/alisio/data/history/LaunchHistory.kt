package dev.jesusmolano.alisio.data.history

import dev.jesusmolano.alisio.core.search.DAY_MILLIS
import dev.jesusmolano.alisio.core.search.HISTORY_DAYS
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.LaunchEvent
import dev.jesusmolano.alisio.data.db.LaunchEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Historial de aperturas de los últimos 90 días: alimenta el ranking y los recientes de la barra. */
class LaunchHistory(private val dao: LaunchEventDao, private val clock: Clock, private val scope: CoroutineScope) {
    /** Horas de apertura por clave, de la más antigua a la más reciente. */
    val openings: StateFlow<Map<String, List<Long>>> =
        dao.since(clock.now() - HISTORY_DAYS * DAY_MILLIS)
            .map { rows -> rows.groupBy({ it.key }, { it.at }) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch { dao.deleteBefore(clock.now() - HISTORY_DAYS * DAY_MILLIS) }
    }

    fun record(type: String, key: String) {
        scope.launch { dao.insert(LaunchEvent(type = type, key = key, at = clock.now())) }
    }

    companion object {
        const val TYPE_APP = "app"
        const val TYPE_SHORTCUT = "shortcut"
        const val TYPE_SETTING = "setting"
    }
}
